package app.ledgerpop.data.sms

import android.util.Log
import app.ledgerpop.data.local.SmsAuditDao
import app.ledgerpop.data.local.SmsAuditEntity
import app.ledgerpop.data.local.SmsTransactionDao
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.data.parser.SmsParser

class SmsImporter(
    private val smsReader: SmsReader,
    private val dao: SmsTransactionDao,
    private val auditDao: SmsAuditDao
) {

    private val TAG = "LedgerPop"

    suspend fun importInbox(
        fromMillis: Long? = null,
        toMillis: Long? = null
    ): Int {
        val messages = smsReader.readTransactionSms()

        Log.d(TAG, "Total SMS read from inbox: ${messages.size}")

        var importedCount = 0

        for (msg in messages) {
            if (fromMillis != null && msg.timestamp < fromMillis) continue
            if (toMillis != null && msg.timestamp > toMillis) continue

            importSingle(msg)?.let {
                importedCount++
            }
        }

        Log.d(TAG, "Import complete. Imported: $importedCount")
        return importedCount
    }

    suspend fun importSingle(msg: SmsMessage): SmsTransactionEntity? {
        val shouldProcess = SmsFilter.shouldProcess(msg.sender, msg.body)
        val reason = SmsFilter.skipReason(msg.sender, msg.body)

        Log.d(
            TAG,
            "Processing: sender=${msg.sender} | filter=$shouldProcess | reason=$reason | body=${msg.body.take(120)}"
        )

        if (!shouldProcess) {
            val hashKey = buildHashKey(msg.sender, msg.timestamp, 0.0, "SKIPPED")

            auditDao.insert(
                SmsAuditEntity(
                    sender = msg.sender,
                    body = msg.body,
                    timestamp = msg.timestamp,
                    status = "SKIPPED",
                    skipReason = reason,
                    parsedAmount = 0.0,
                    parsedType = "",
                    hashKey = hashKey
                )
            )
            return null
        }

        // The upgraded Parser now returns the beautifully formatted Account Name and Auto-Category
        val parsed = SmsParser.parse(msg.sender, msg.body)

        if (parsed == null) {
            val hashKey = buildHashKey(msg.sender, msg.timestamp, 0.0, "PARSE_FAILED")

            auditDao.insert(
                SmsAuditEntity(
                    sender = msg.sender,
                    body = msg.body,
                    timestamp = msg.timestamp,
                    status = "PARSE_FAILED",
                    skipReason = "Parser returned null — no amount/type detected",
                    parsedAmount = 0.0,
                    parsedType = "",
                    hashKey = hashKey
                )
            )
            return null
        }

        val hashKey = buildHashKey(msg.sender, msg.timestamp, parsed.amount, parsed.type)

        if (dao.exists(hashKey) > 0) {
            Log.d(TAG, "Duplicate skipped: $hashKey")
            return null
        }

        val entity = SmsTransactionEntity(
            sender = msg.sender,
            body = msg.body,
            amount = parsed.amount,
            type = parsed.type,
            merchant = parsed.merchant,

            // Map the newly extracted category
            category = parsed.category,

            bank = parsed.bank,

            // Map the newly extracted Account string: "SBI (3456)" instead of just the last 4
            accountHint = parsed.accountName,

            isBillable = true,
            transactionTime = msg.timestamp,
            hashKey = hashKey
        )

        dao.insert(entity)

        auditDao.insert(
            SmsAuditEntity(
                sender = msg.sender,
                body = msg.body,
                timestamp = msg.timestamp,
                status = "IMPORTED",
                skipReason = "",
                parsedAmount = parsed.amount,
                parsedType = parsed.type,
                hashKey = hashKey
            )
        )

        return entity
    }

    private fun buildHashKey(
        sender: String,
        timestamp: Long,
        amount: Double,
        type: String
    ): String {
        return "${sender}_${timestamp}_${amount}_${type}"
    }
}
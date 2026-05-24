package app.ledgerpop.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import app.ledgerpop.data.local.SmsAuditDao
import app.ledgerpop.data.local.SmsAuditEntity
import app.ledgerpop.data.local.SmsTransactionDao
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.data.parser.SmsParser

data class ImportResult(
    val imported: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0
)

class SmsImporter(
    private val context: Context,
    private val smsReader: SmsReader,
    private val dao: SmsTransactionDao,
    private val auditDao: SmsAuditDao,
    private val aliasDao: app.ledgerpop.data.local.AccountAliasDao? = null,
    private val accountDao: app.ledgerpop.data.local.AccountDao? = null
) {

    private val TAG = "LedgerPop"

    private suspend fun resolveAccountName(name: String): String {
        if (name.isBlank()) return ""
        val target = aliasDao?.getTargetName(name) ?: name
        // Auto-create account if it doesn't exist
        if (target.isNotBlank() && accountDao != null) {
            if (accountDao.getByName(target) == null) {
                accountDao.insert(app.ledgerpop.data.local.AccountEntity(name = target))
            }
        }
        return target
    }

    suspend fun importInbox(
        fromMillis: Long? = null,
        toMillis: Long? = null
    ): ImportResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission READ_SMS not granted. Aborting import.")
            return ImportResult()
        }

        val messages = smsReader.readTransactionSms()
        Log.d(TAG, "Total SMS read from inbox: ${messages.size}")

        val transactionsToInsert = mutableListOf<SmsTransactionEntity>()
        val auditToInsert = mutableListOf<SmsAuditEntity>()

        var imported = 0
        var failed = 0
        var skipped = 0

        for (msg in messages) {
            if (fromMillis != null && msg.timestamp < fromMillis) continue
            if (toMillis != null && msg.timestamp > toMillis) continue

            val shouldProcess = SmsFilter.shouldProcess(msg.sender, msg.body)
            val reason = SmsFilter.skipReason(msg.sender, msg.body)

            if (!shouldProcess) {
                val hashKey = buildHashKey(msg.sender, msg.timestamp, 0.0, "SKIPPED")
                auditToInsert.add(
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
                skipped++
                continue
            }

            val parsed = app.ledgerpop.data.parser.SmsParser.parse(msg.sender, msg.body)
            if (parsed == null) {
                val hashKey = buildHashKey(msg.sender, msg.timestamp, 0.0, "PARSE_FAILED")
                auditToInsert.add(
                    SmsAuditEntity(
                        sender = msg.sender,
                        body = msg.body,
                        timestamp = msg.timestamp,
                        status = "PARSE_FAILED",
                        skipReason = "Parser returned null",
                        parsedAmount = 0.0,
                        parsedType = "",
                        hashKey = hashKey
                    )
                )
                failed++
                continue
            }

            val hashKey = buildHashKey(msg.sender, msg.timestamp, parsed.amount, parsed.type)
            if (dao.exists(hashKey) > 0) {
                skipped++
                continue
            }

            transactionsToInsert.add(
                SmsTransactionEntity(
                    sender = msg.sender,
                    body = msg.body,
                    amount = parsed.amount,
                    type = parsed.type,
                    merchant = parsed.merchant,
                    category = parsed.category,
                    bank = parsed.bank,
                    accountHint = resolveAccountName(parsed.accountName),
                    isBillable = true,
                    transactionTime = msg.timestamp,
                    hashKey = hashKey
                )
            )
            auditToInsert.add(
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
            imported++
        }

        if (transactionsToInsert.isNotEmpty()) {
            dao.insertAll(transactionsToInsert)
        }
        if (auditToInsert.isNotEmpty()) {
            auditDao.insertAll(auditToInsert)
        }

        Log.d(TAG, "Import complete. $imported imported, $failed failed, $skipped skipped")
        return ImportResult(imported, failed, skipped)
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
            accountHint = resolveAccountName(parsed.accountName),

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

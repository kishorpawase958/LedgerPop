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
import app.ledgerpop.data.local.AccountAliasDao
import app.ledgerpop.data.local.AccountDao
import app.ledgerpop.data.local.AccountEntity
import app.ledgerpop.data.local.SmartRuleDao
import app.ledgerpop.data.category.CategoryEngine
import app.ledgerpop.data.parser.SmsParser

data class ImportResult(
    val imported: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val scanned: Int = 0
)

class SmsImporter(
    private val context: Context,
    private val smsReader: SmsReader,
    private val dao: SmsTransactionDao,
    private val auditDao: SmsAuditDao,
    private val aliasDao: AccountAliasDao? = null,
    private val accountDao: AccountDao? = null,
    private val smartRuleDao: SmartRuleDao? = null
) {

    private val TAG = "LedgerPop"

    private suspend fun resolveAccountName(name: String): String {
        if (name.isBlank()) return ""
        val target = aliasDao?.getTargetName(name) ?: name
        // Auto-create account if it doesn't exist
        if (target.isNotBlank() && accountDao != null) {
            if (accountDao.getByName(target) == null) {
                accountDao.insert(AccountEntity(name = target))
            }
        }
        return target
    }

    private suspend fun resolveMerchantName(name: String): String {
        if (name.isBlank()) return ""
        // Use the alias system to resolve merchant names as well
        return aliasDao?.getTargetName(name) ?: name
    }

    private suspend fun resolveCategory(merchant: String, body: String, sender: String, type: String): String {
        if (merchant.isBlank()) return CategoryEngine.categorize(merchant, body, sender, type = type)
        
        // 1. Try lookback in history for this merchant (using normalized name for better hits)
        val normalized = CategoryEngine.normalizeMerchant(merchant)
        
        // Exact match check
        dao.getLastCategoryForMerchant(normalized)?.let { return it }
        dao.getLastCategoryForMerchant(merchant)?.let { return it }
        
        // Fuzzy match check (prefix/keyword matching)
        if (normalized.length >= 3) {
            dao.getLastCategoryForMerchantFuzzy(normalized)?.let { return it }
        }
        if (merchant.length >= 3) {
            dao.getLastCategoryForMerchantFuzzy(merchant)?.let { return it }
        }
        
        // 2. Fallback to CategoryEngine auto-categorization
        return CategoryEngine.categorize(merchant, body, sender, type = type)
    }

    private suspend fun getSmartAction(sender: String, body: String, rules: List<app.ledgerpop.data.local.SmartRuleEntity>? = null): String? {
        val structure = SmsParser.getStructure(body)
        val senderRules = rules ?: smartRuleDao?.getBySender(sender) ?: return null
        return senderRules.find { it.bodyStructure == structure }?.ruleType
    }

    suspend fun importInbox(
        fromMillis: Long? = null,
        toMillis: Long? = null
    ): ImportResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission READ_SMS not granted. Aborting import.")
            return ImportResult()
        }

        // Optimization: Fetch only messages since fromMillis if provided
        val messages = smsReader.readTransactionSms(since = fromMillis ?: 0L)
        Log.d(TAG, "Total SMS read from inbox: ${messages.size}")

        // Pre-fetch all smart rules for batch processing
        val allRules = smartRuleDao?.getAll()?.groupBy { it.sender } ?: emptyMap()
        
        // Pre-fetch recent audit hash keys to avoid duplicate checks in DB loop
        val recentAuditHashes = auditDao.getAllSync().take(1000).map { it.hashKey }.toHashSet()
        val recentTransactionHashes = dao.getAllTransactionsSync().take(1000).map { it.hashKey }.toHashSet()

        val transactionsToInsert = mutableListOf<SmsTransactionEntity>()
        val auditToInsert = mutableListOf<SmsAuditEntity>()
        val categoryCache = mutableMapOf<String, String>()

        var imported = 0
        var failed = 0
        var skipped = 0
        var scanned = 0

        for (msg in messages) {
            if (fromMillis != null && msg.timestamp < fromMillis) continue
            if (toMillis != null && msg.timestamp > toMillis) continue

            scanned++
            
            val senderKey: String = msg.sender
            val smartAction = getSmartAction(senderKey, msg.body, allRules[senderKey])

            val shouldProcess = when (smartAction) {
                "ALWAYS_IMPORT" -> true
                "ALWAYS_SKIP" -> false
                else -> SmsFilter.shouldProcess(msg.sender, msg.body)
            }

            if (!shouldProcess) {
                val reason = when (smartAction) {
                    "ALWAYS_SKIP" -> "Smart Rule: ALWAYS_SKIP"
                    else -> SmsFilter.skipReason(msg.sender, msg.body)
                }
                
                val hashKey = SmsParser.buildHashKey(msg.sender, msg.timestamp, 0.0, "SKIPPED")
                if (recentAuditHashes.contains(hashKey)) {
                    skipped++
                    continue
                }
                
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

            val parsed = SmsParser.parse(msg.sender, msg.body, ignoreSpamCheck = smartAction == "ALWAYS_IMPORT")
            if (parsed == null) {
                val hashKey = SmsParser.buildHashKey(msg.sender, msg.timestamp, 0.0, "PARSE_FAILED")
                if (recentAuditHashes.contains(hashKey)) {
                    skipped++
                    continue
                }

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

            val hashKey = SmsParser.buildHashKey(msg.sender, msg.timestamp, parsed.amount, parsed.type, parsed.refNo)
            
            // Fast check against pre-fetched hashes
            if (recentTransactionHashes.contains(hashKey) || recentAuditHashes.contains(hashKey)) {
                skipped++
                continue
            }

            val merchant = resolveMerchantName(parsed.merchant)
            
            // Use local cache to speed up category resolution for common merchants
            val category = categoryCache.getOrPut(merchant) {
                resolveCategory(merchant, msg.body, msg.sender, parsed.type)
            }

            transactionsToInsert.add(
                SmsTransactionEntity(
                    sender = msg.sender,
                    body = msg.body,
                    amount = parsed.amount,
                    type = parsed.type,
                    merchant = merchant,
                    category = category,
                    bank = parsed.bank,
                    accountHint = resolveAccountName(parsed.accountName),
                    isBillable = parsed.includeInAnalytics,
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
            
            // Batch inserts every 100 items to avoid keeping too much in memory
            if (transactionsToInsert.size >= 100) {
                dao.insertAll(transactionsToInsert)
                transactionsToInsert.clear()
            }
            if (auditToInsert.size >= 100) {
                auditDao.insertAll(auditToInsert)
                auditToInsert.clear()
            }
        }

        if (transactionsToInsert.isNotEmpty()) {
            dao.insertAll(transactionsToInsert)
        }
        if (auditToInsert.isNotEmpty()) {
            auditDao.insertAll(auditToInsert)
        }

        Log.d(TAG, "Import complete. $imported imported, $failed failed, $skipped skipped, $scanned scanned")
        return ImportResult(imported, failed, skipped, scanned)
    }

    suspend fun importSingle(msg: SmsMessage): SmsTransactionEntity? {
        val smartAction = getSmartAction(msg.sender, msg.body)

        val shouldProcess = when (smartAction) {
            "ALWAYS_IMPORT" -> true
            "ALWAYS_SKIP" -> false
            else -> SmsFilter.shouldProcess(msg.sender, msg.body)
        }

        val reason = when (smartAction) {
            "ALWAYS_IMPORT" -> "Smart Rule: ALWAYS_IMPORT"
            "ALWAYS_SKIP" -> "Smart Rule: ALWAYS_SKIP"
            else -> SmsFilter.skipReason(msg.sender, msg.body)
        }

        Log.d(
            TAG,
            "Processing: sender=${msg.sender} | smartAction=$smartAction | filter=$shouldProcess | reason=$reason | body=${msg.body.take(120)}"
        )

        if (auditDao.existsDuplicate(msg.sender, msg.body, msg.timestamp) > 0) {
            Log.d(TAG, "Duplicate audit entry skipped")
            return null
        }

        if (!shouldProcess) {
            val hashKey = SmsParser.buildHashKey(msg.sender, msg.timestamp, 0.0, "SKIPPED")

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
        val parsed = SmsParser.parse(msg.sender, msg.body, ignoreSpamCheck = smartAction == "ALWAYS_IMPORT")

        if (parsed == null) {
            val hashKey = SmsParser.buildHashKey(msg.sender, msg.timestamp, 0.0, "PARSE_FAILED")

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

        val hashKey = SmsParser.buildHashKey(msg.sender, msg.timestamp, parsed.amount, parsed.type, parsed.refNo)

        if (dao.exists(hashKey) > 0 || dao.existsDuplicate(msg.sender, msg.body, parsed.amount, msg.timestamp) > 0) {
            Log.d(TAG, "Duplicate skipped: $hashKey")
            return null
        }

        val resolvedMerchant = resolveMerchantName(parsed.merchant)

        val entity = SmsTransactionEntity(
            sender = msg.sender,
            body = msg.body,
            amount = parsed.amount,
            type = parsed.type,
            merchant = resolvedMerchant,

            // Map the newly extracted category or lookback history
            category = resolveCategory(resolvedMerchant, msg.body, msg.sender, parsed.type),

            bank = parsed.bank,

            // Map the newly extracted Account string: "SBI (3456)" instead of just the last 4
            accountHint = resolveAccountName(parsed.accountName),

            isBillable = parsed.includeInAnalytics,
            transactionTime = msg.timestamp,
            hashKey = hashKey
        )

        val insertedId = dao.insert(entity).toInt()
        val savedEntity = entity.copy(id = insertedId)

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

        return savedEntity
    }

    // Moved to SmsParser.buildHashKey
}

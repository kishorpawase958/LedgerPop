package app.ledgerpop.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.ledgerpop.data.local.AccountEntity
import app.ledgerpop.data.local.AccountAliasEntity
import app.ledgerpop.data.local.CustomCategoryEntity
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.data.sms.ImportResult
import app.ledgerpop.data.sms.SmsImporter
import app.ledgerpop.data.sms.SmsReader
import app.ledgerpop.ui.state.AppTheme
import app.ledgerpop.ui.state.SettingsUiState
import app.ledgerpop.data.local.SmsAuditEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

class SettingsViewModel(
    private val db: LedgerPopDatabase,
    context: Context
) : ViewModel() {

    private val appContext = context.applicationContext
    private val sharedPrefs = appContext.getSharedPreferences("ledgerpop_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SettingsUiState(
        appTheme = try {
            AppTheme.valueOf(sharedPrefs.getString("app_theme", AppTheme.AUTO.name) ?: AppTheme.AUTO.name)
        } catch (e: Exception) {
            AppTheme.AUTO
        },
        userName = sharedPrefs.getString("user_name", "Kishor") ?: "Kishor"
    ))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val smsImporter = SmsImporter(
        appContext,
        SmsReader(appContext),
        db.smsTransactionDao(),
        db.smsAuditDao(),
        db.accountAliasDao(),
        db.accountDao()
    )

    init {
        refreshPermissions()
        // Observe transaction count
        viewModelScope.launch {
            db.smsTransactionDao().getAllTransactions().collect { list ->
                _uiState.update { it.copy(totalTransactions = list.size) }
            }
        }
        // Observe custom categories
        viewModelScope.launch {
            db.customCategoryDao().getAllCategories().collect { list ->
                _uiState.update { it.copy(customCategories = list) }
            }
        }
        // Observe accounts
        viewModelScope.launch {
            db.accountDao().getAllAccounts().collect { list ->
                _uiState.update { it.copy(accounts = list) }
            }
        }
        syncAccountsFromTransactions()
    }

    private fun syncAccountsFromTransactions() {
        viewModelScope.launch {
            val accountNames = db.smsTransactionDao().getAllAccounts()
            val existingAccounts = db.accountDao().getAllSync().map { it.name }
            accountNames.forEach { name ->
                val resolved = db.accountAliasDao().getTargetName(name) ?: name
                if (db.accountDao().getByName(resolved) == null) {
                    db.accountDao().insert(AccountEntity(name = resolved))
                }
            }
        }
    }

    fun updateTheme(theme: AppTheme) {
        sharedPrefs.edit().putString("app_theme", theme.name).apply()
        _uiState.update { it.copy(appTheme = theme) }
    }

    fun refreshPermissions() {
        val readGranted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        val receiveGranted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        _uiState.update { it.copy(
            hasReadSmsPermission = readGranted,
            hasReceiveSmsPermission = receiveGranted
        ) }
    }

    fun importSms() {
        if (!uiState.value.hasReadSmsPermission) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, lastImportMessage = "", lastImportResult = null) }
            try {
                val result = smsImporter.importInbox()
                _uiState.update { it.copy(
                    isImporting = false,
                    lastImportResult = result,
                    lastImportMessage = "Imported ${result.imported} new transactions."
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isImporting = false,
                    lastImportMessage = "Import failed: ${e.message}"
                ) }
            }
        }
    }

    fun importSmsWithDateRange() {
        if (!uiState.value.hasReadSmsPermission) return
        val from = uiState.value.dateRangeFromMillis
        val to = uiState.value.dateRangeToMillis

        if (from == null || to == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, lastImportMessage = "", lastImportResult = null) }
            try {
                val result = smsImporter.importInbox(fromMillis = from, toMillis = to)
                _uiState.update { it.copy(
                    isImporting = false,
                    lastImportResult = result,
                    lastImportMessage = "Imported ${result.imported} new transactions from the selected range."
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isImporting = false,
                    lastImportMessage = "Import failed: ${e.message}"
                ) }
            }
        }
    }

    fun showDateRangePicker() {
        if (!uiState.value.hasReadSmsPermission) return
        _uiState.update { it.copy(showDateRangePicker = true) }
    }

    fun hideDateRangePicker() {
        _uiState.update { it.copy(showDateRangePicker = false) }
    }

    fun onDateRangeSelected(from: Long, to: Long) {
        _uiState.update { it.copy(
            dateRangeFromMillis = from,
            dateRangeToMillis = to,
            showDateRangePicker = false
        ) }
    }

    fun clearDateRange() {
        _uiState.update { it.copy(dateRangeFromMillis = null, dateRangeToMillis = null) }
    }

    fun updateUserName(newName: String) {
        sharedPrefs.edit().putString("user_name", newName).apply()
        _uiState.update { it.copy(userName = newName) }
    }

    fun updateCategoryEmoji(categoryName: String, emoji: String, type: String) {
        viewModelScope.launch {
            val existing = db.customCategoryDao().getByName(categoryName)
            if (existing != null) {
                db.customCategoryDao().insert(existing.copy(emoji = emoji))
            } else {
                db.customCategoryDao().insert(CustomCategoryEntity(name = categoryName, type = type, emoji = emoji))
            }
        }
    }

    fun addCustomCategory(name: String, emoji: String, type: String) {
        viewModelScope.launch {
            db.customCategoryDao().insert(CustomCategoryEntity(name = name, type = type, emoji = emoji))
        }
    }

    fun deleteCategory(id: Int) {
        viewModelScope.launch {
            db.customCategoryDao().deleteById(id)
        }
    }

    fun updateAccount(id: Int, newName: String, newIcon: String) {
        viewModelScope.launch {
            val accounts = db.accountDao().getAllSync()
            val existing = accounts.find { it.id == id } ?: return@launch
            val oldName = existing.name

            // 1. Update the account itself
            db.accountDao().insert(existing.copy(name = newName, icon = newIcon))

            // 2. If name changed, update transactions and aliases
            if (oldName != newName) {
                db.smsTransactionDao().updateAccountName(oldName, newName)
                
                // Update existing aliases that pointed to the old name
                val aliases = db.accountAliasDao().getAllAliases()
                aliases.forEach {
                    if (it.targetAccountName == oldName) {
                        db.accountAliasDao().insert(it.copy(targetAccountName = newName))
                    }
                }
                
                // Add an alias from oldName to newName so future imports match
                db.accountAliasDao().insert(AccountAliasEntity(oldName, newName))
            }
        }
    }

    fun mergeAccounts(sourceAccountId: Int, targetAccountId: Int) {
        viewModelScope.launch {
            val accounts = db.accountDao().getAllSync()
            val source = accounts.find { it.id == sourceAccountId } ?: return@launch
            val target = accounts.find { it.id == targetAccountId } ?: return@launch

            if (source.id == target.id) return@launch

            // 1. Update transactions
            db.smsTransactionDao().updateAccountName(source.name, target.name)

            // 2. Update existing aliases that pointed to the source
            val aliases = db.accountAliasDao().getAllAliases()
            aliases.forEach {
                if (it.targetAccountName == source.name) {
                    db.accountAliasDao().insert(it.copy(targetAccountName = target.name))
                }
            }

            // 3. Add new alias from source to target
            db.accountAliasDao().insert(AccountAliasEntity(source.name, target.name))

            // 4. Delete source account
            db.accountDao().deleteById(sourceAccountId)
        }
    }

    fun updateAccountIcon(accountName: String, icon: String) {
        viewModelScope.launch {
            val existing = db.accountDao().getByName(accountName)
            if (existing != null) {
                db.accountDao().insert(existing.copy(icon = icon))
            } else {
                db.accountDao().insert(AccountEntity(name = accountName, icon = icon))
            }
        }
    }

    fun addAccount(name: String, icon: String) {
        viewModelScope.launch {
            db.accountDao().insert(AccountEntity(name = name, icon = icon))
        }
    }

    fun deleteAccount(id: Int) {
        viewModelScope.launch {
            db.accountDao().deleteById(id)
        }
    }

    fun clearImportResult() {
        _uiState.update { it.copy(lastImportResult = null, lastImportMessage = "") }
    }

    fun deleteAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearing = true) }
            try {
                db.smsTransactionDao().deleteAll()
                db.smsAuditDao().deleteAll()
                db.customCategoryDao().deleteAll()
                db.accountDao().deleteAll()
                _uiState.update { it.copy(isClearing = false, lastImportMessage = "All data cleared successfully.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isClearing = false, lastImportMessage = "Clear failed: ${e.message}") }
            }
        }
    }

    fun backupData(context: Context, uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Fetch all data from DB
                val transactions = db.smsTransactionDao().getAllTransactionsSync()
                val audits = db.smsAuditDao().getAllSync()
                val categories = db.customCategoryDao().getAllSync()

                val root = JSONObject()
                root.put("backupVersion", 2)
                root.put("timestamp", System.currentTimeMillis())
                root.put("appName", "LedgerPop")

                // 1. Preferences
                val prefsJson = JSONObject()
                prefsJson.put("appTheme", uiState.value.appTheme.name)
                prefsJson.put("userName", uiState.value.userName)
                root.put("preferences", prefsJson)

                // 2. Transactions
                val txnsArray = JSONArray()
                transactions.forEach { txn ->
                    val obj = JSONObject()
                    obj.put("sender", txn.sender)
                    obj.put("body", txn.body)
                    obj.put("amount", txn.amount)
                    obj.put("type", txn.type)
                    obj.put("merchant", txn.merchant)
                    obj.put("category", txn.category)
                    obj.put("accountHint", txn.accountHint)
                    obj.put("isBillable", txn.isBillable)
                    obj.put("transactionTime", txn.transactionTime)
                    obj.put("hashKey", txn.hashKey)
                    obj.put("bank", txn.bank)
                    obj.put("note", txn.note)
                    txnsArray.put(obj)
                }
                root.put("transactions", txnsArray)

                // 3. Audits
                val auditsArray = JSONArray()
                audits.forEach { audit ->
                    val obj = JSONObject()
                    obj.put("sender", audit.sender)
                    obj.put("body", audit.body)
                    obj.put("timestamp", audit.timestamp)
                    obj.put("status", audit.status)
                    obj.put("skipReason", audit.skipReason)
                    obj.put("parsedAmount", audit.parsedAmount)
                    obj.put("parsedType", audit.parsedType)
                    obj.put("hashKey", audit.hashKey)
                    obj.put("reportType", audit.reportType)
                    obj.put("reportNote", audit.reportNote)
                    auditsArray.put(obj)
                }
                root.put("auditLogs", auditsArray)

                // 4. Custom Categories
                val categoriesArray = JSONArray()
                categories.forEach { cat ->
                    val obj = JSONObject()
                    obj.put("name", cat.name)
                    obj.put("type", cat.type)
                    obj.put("emoji", cat.emoji)
                    categoriesArray.put(obj)
                }
                root.put("customCategories", categoriesArray)

                // 5. Accounts
                val accounts = db.accountDao().getAllSync()
                val accountsArray = JSONArray()
                accounts.forEach { acc ->
                    val obj = JSONObject()
                    obj.put("name", acc.name)
                    obj.put("icon", acc.icon)
                    accountsArray.put(obj)
                }
                root.put("accounts", accountsArray)

                // Write to file
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(root.toString(2).toByteArray())
                }

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(lastImportMessage = "Full backup (${transactions.size} transactions) saved successfully.") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(lastImportMessage = "Backup failed: ${e.message}") }
                }
            }
        }
    }

    fun restoreData(context: Context, uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isImporting = true) }
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader().readText()
                } ?: throw Exception("Could not read backup file")

                val root = JSONObject(jsonString)
                
                // 1. Restore Preferences
                if (root.has("preferences")) {
                    val prefs = root.getJSONObject("preferences")
                    val theme = prefs.optString("appTheme", AppTheme.AUTO.name)
                    val name = prefs.optString("userName", "Kishor")
                    
                    sharedPrefs.edit()
                        .putString("app_theme", theme)
                        .putString("user_name", name)
                        .apply()
                        
                    _uiState.update { it.copy(
                        appTheme = try { AppTheme.valueOf(theme) } catch (e: Exception) { AppTheme.AUTO },
                        userName = name
                    ) }
                }

                // 2. Restore Transactions
                val txnsArray = root.optJSONArray("transactions")
                if (txnsArray != null) {
                    val txns = mutableListOf<SmsTransactionEntity>()
                    for (i in 0 until txnsArray.length()) {
                        val obj = txnsArray.getJSONObject(i)
                        txns.add(SmsTransactionEntity(
                            id = 0, // Let Room auto-generate
                            sender = obj.getString("sender"),
                            body = obj.getString("body"),
                            amount = obj.getDouble("amount"),
                            type = obj.getString("type"),
                            merchant = obj.getString("merchant"),
                            category = obj.getString("category"),
                            accountHint = obj.optString("accountHint", ""),
                            isBillable = obj.optBoolean("isBillable", true),
                            transactionTime = obj.getLong("transactionTime"),
                            hashKey = obj.getString("hashKey"),
                            bank = obj.optString("bank", ""),
                            note = obj.optString("note", "")
                        ))
                    }
                    // Wipe and restore transactions
                    db.smsTransactionDao().deleteAll()
                    if (txns.isNotEmpty()) {
                        db.smsTransactionDao().insertAll(txns)
                    }
                }

                // 3. Restore Audits
                val auditsArray = root.optJSONArray("auditLogs")
                if (auditsArray != null) {
                    val audits = mutableListOf<SmsAuditEntity>()
                    for (i in 0 until auditsArray.length()) {
                        val obj = auditsArray.getJSONObject(i)
                        audits.add(SmsAuditEntity(
                            id = 0, // Auto-generate
                            sender = obj.getString("sender"),
                            body = obj.getString("body"),
                            timestamp = obj.getLong("timestamp"),
                            status = obj.getString("status"),
                            skipReason = obj.optString("skipReason", ""),
                            parsedAmount = obj.optDouble("parsedAmount", 0.0),
                            parsedType = obj.optString("parsedType", ""),
                            reportType = obj.optString("reportType", ""),
                            reportNote = obj.optString("reportNote", ""),
                            hashKey = obj.getString("hashKey")
                        ))
                    }
                    db.smsAuditDao().deleteAll()
                    if (audits.isNotEmpty()) {
                        db.smsAuditDao().insertAll(audits)
                    }
                }

                // 4. Restore Categories
                val categoriesArray = root.optJSONArray("customCategories")
                if (categoriesArray != null) {
                    val categories = mutableListOf<CustomCategoryEntity>()
                    for (i in 0 until categoriesArray.length()) {
                        val obj = categoriesArray.getJSONObject(i)
                        categories.add(CustomCategoryEntity(
                            id = 0,
                            name = obj.getString("name"),
                            type = obj.getString("type"),
                            emoji = obj.getString("emoji")
                        ))
                    }
                    db.customCategoryDao().deleteAll()
                    categories.forEach { db.customCategoryDao().insert(it) }
                }

                // 5. Restore Accounts
                val accountsArray = root.optJSONArray("accounts")
                if (accountsArray != null) {
                    val accounts = mutableListOf<AccountEntity>()
                    for (i in 0 until accountsArray.length()) {
                        val obj = accountsArray.getJSONObject(i)
                        accounts.add(AccountEntity(
                            id = 0,
                            name = obj.getString("name"),
                            icon = obj.getString("icon")
                        ))
                    }
                    db.accountDao().deleteAll()
                    accounts.forEach { db.accountDao().insert(it) }
                }

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(
                        isImporting = false, 
                        lastImportMessage = "Restore successful! All data has been synchronized."
                    ) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isImporting = false, lastImportMessage = "Restore failed: ${e.message}") }
                }
            }
        }
    }

    fun importFromCsv(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isImporting = true) }
            try {
                val transactions = parseCsv(appContext, uri)
                if (transactions.isNotEmpty()) {
                    db.smsTransactionDao().insertAll(transactions)
                }
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(
                        isImporting = false, 
                        lastImportResult = if (transactions.isNotEmpty()) ImportResult(imported = transactions.size) else null,
                        lastImportMessage = if (transactions.isEmpty()) "No valid transactions found in CSV" else ""
                    ) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isImporting = false, lastImportMessage = "CSV Import failed: ${e.message}") }
                }
            }
        }
    }

    private suspend fun parseCsv(context: Context, uri: Uri): List<SmsTransactionEntity> {
        val transactions = mutableListOf<SmsTransactionEntity>()
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val timeFormatterAlt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        
        val existingAccounts = db.smsTransactionDao().getAllAccounts()

        context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader().use { reader ->
                reader.readLine() // Skip header
                var line = reader.readLine()
                while (line != null) {
                    if (line.isBlank()) {
                        line = reader.readLine()
                        continue
                    }
                    
                    // Detect delimiter: tab or comma
                    val parts = if (line.contains("\t")) {
                        line.split("\t").map { it.trim().removeSurrounding("\"") }
                    } else {
                        parseCsvLine(line).map { it.trim().removeSurrounding("\"") }
                    }

                    if (parts.size >= 9) {
                        try {
                            val dateStr = parts[0]
                            val timeStr = parts[1]
                            val merchant = parts[2]
                            // Handle commas in amounts like 1,000 or 104,104.75
                            val amountStr = parts[3].replace(",", "")
                            val amount = amountStr.toDoubleOrNull() ?: 0.0
                            val typeStr = parts[4]
                            val accountRaw = parts[5]
                            val expenseStatus = parts[6]
                            val incomeStatus = parts[7]
                            val categoryRaw = parts[8]
                            val note = if (parts.size > 9) parts[9] else ""
                            
                            val isDr = typeStr.equals("DR", ignoreCase = true)
                            val type = if (isDr) "DEBIT" else "CREDIT"
                            
                            val isBillable = if (isDr) {
                                expenseStatus.equals("Yes", ignoreCase = true)
                            } else {
                                incomeStatus.equals("Yes", ignoreCase = true)
                            }

                            val date = LocalDate.parse(dateStr, dateFormatter)
                            val time = try {
                                LocalTime.parse(timeStr.uppercase(), timeFormatter)
                            } catch (e: Exception) {
                                LocalTime.parse(timeStr.uppercase(), timeFormatterAlt)
                            }
                            val transactionTime = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            
                            // Smart Category Merging
                            val category = app.ledgerpop.data.category.CategoryEngine.normalize(categoryRaw)
                            
                            // Smart Account Merging
                            val account = matchAccount(accountRaw, existingAccounts)

                            val hashKey = "CSV_${transactionTime}_${amount}_${merchant}_${type}"

                            transactions.add(
                                SmsTransactionEntity(
                                    sender = "CSV",
                                    body = "Imported from CSV",
                                    amount = amount,
                                    type = type,
                                    merchant = merchant,
                                    category = category,
                                    accountHint = account,
                                    isBillable = isBillable,
                                    transactionTime = transactionTime,
                                    hashKey = hashKey,
                                    bank = "",
                                    note = note
                                )
                            )
                        } catch (e: Exception) {
                            // Skip invalid lines
                        }
                    }
                    line = reader.readLine()
                }
            }
        }
        return transactions
    }

    private fun matchAccount(csvAccount: String, existingAccounts: List<String>): String {
        if (csvAccount.isBlank()) return ""
        
        // 1. Exact match (ignore case)
        existingAccounts.find { it.equals(csvAccount, ignoreCase = true) }?.let { return it }
        
        // 2. Try to match by last 4 digits if present
        val last4Regex = Regex("""(\d{4})""")
        val csvLast4 = last4Regex.find(csvAccount)?.groupValues?.get(1)
        
        if (csvLast4 != null) {
            existingAccounts.find { last4Regex.find(it)?.groupValues?.get(1) == csvLast4 }?.let { return it }
        }
        
        // 3. Fuzzy match: if one contains the other (e.g. "SBI 1234" and "SBI (1234)")
        val cleanCsv = csvAccount.lowercase().replace(Regex("[^a-z0-9]"), "")
        existingAccounts.find {
            val cleanExisting = it.lowercase().replace(Regex("[^a-z0-9]"), "")
            cleanCsv.contains(cleanExisting) || cleanExisting.contains(cleanCsv)
        }?.let { return it }
        
        return csvAccount
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    current.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString())
                current = StringBuilder()
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    companion object {
        fun factory(db: LedgerPopDatabase, context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(db, context) as T
                }
            }
    }
}

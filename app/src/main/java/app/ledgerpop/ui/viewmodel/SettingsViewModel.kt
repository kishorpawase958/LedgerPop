package app.ledgerpop.ui.viewmodel

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
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
import app.ledgerpop.data.backup.BackupScheduler
import app.ledgerpop.data.backup.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
        } catch (_: Exception) {
            AppTheme.AUTO
        },
        userName = sharedPrefs.getString("user_name", "User") ?: "User",
        isAutoBackupEnabled = sharedPrefs.getBoolean("auto_backup_enabled", false),
        backupFrequency = sharedPrefs.getString("backup_frequency", "Daily") ?: "Daily",
        backupFolderUri = sharedPrefs.getString("backup_folder_uri", null),
        backupFolderName = sharedPrefs.getString("backup_folder_name", null),
        isFirstRun = sharedPrefs.getBoolean("is_first_run", true)
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

    private val backupScheduler = BackupScheduler(appContext)

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
        // Observe account aliases
        viewModelScope.launch {
            db.accountAliasDao().getAllAliasesFlow().collect { list ->
                _uiState.update { it.copy(accountAliases = list) }
            }
        }
        syncAccountsFromTransactions()
        backupScheduler.scheduleBackup(uiState.value.isAutoBackupEnabled, uiState.value.backupFrequency)
    }

    private fun syncAccountsFromTransactions() {
        viewModelScope.launch {
            val accountNames = db.smsTransactionDao().getAllAccounts()
            accountNames.forEach { name ->
                val resolved = db.accountAliasDao().getTargetName(name) ?: name
                if (db.accountDao().getByName(resolved) == null) {
                    db.accountDao().insert(AccountEntity(name = resolved))
                }
            }
        }
    }

    fun updateTheme(theme: AppTheme) {
        sharedPrefs.edit { putString("app_theme", theme.name) }
        _uiState.update { it.copy(appTheme = theme) }
    }

    fun refreshPermissions() {
        val readGranted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        val receiveGranted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        _uiState.update { it.copy(
            hasReadSmsPermission = readGranted,
            hasReceiveSmsPermission = receiveGranted,
            hasNotificationsPermission = notificationsGranted
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
                    lastImportMessage = "Transactions imported:${result.imported}"
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
                    lastImportMessage = "Transactions imported:${result.imported}"
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

    fun setFirstRunComplete() {
        sharedPrefs.edit { putBoolean("is_first_run", false) }
        _uiState.update { it.copy(isFirstRun = false) }
    }

    fun updateUserName(newName: String) {
        sharedPrefs.edit { putString("user_name", newName) }
        _uiState.update { it.copy(userName = newName) }
    }

    fun updateAutoBackupEnabled(enabled: Boolean) {
        sharedPrefs.edit { putBoolean("auto_backup_enabled", enabled) }
        _uiState.update { it.copy(isAutoBackupEnabled = enabled) }
        backupScheduler.scheduleBackup(enabled, uiState.value.backupFrequency)
    }

    fun updateBackupFrequency(frequency: String) {
        sharedPrefs.edit { putString("backup_frequency", frequency) }
        _uiState.update { it.copy(backupFrequency = frequency) }
        backupScheduler.scheduleBackup(uiState.value.isAutoBackupEnabled, frequency)
    }

    fun updateBackupFolder(uri: Uri, name: String) {
        // Take persistable permission
        try {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Log error
        }
        
        sharedPrefs.edit {
            putString("backup_folder_uri", uri.toString())
            putString("backup_folder_name", name)
        }
        _uiState.update { it.copy(backupFolderUri = uri.toString(), backupFolderName = name) }
    }

    fun updateCategory(id: Int?, oldName: String, newName: String, emoji: String, type: String) {
        viewModelScope.launch {
            if (id != null) {
                db.customCategoryDao().insert(CustomCategoryEntity(id = id, name = newName, emoji = emoji, type = type))
            } else {
                db.customCategoryDao().insert(CustomCategoryEntity(name = newName, type = type, emoji = emoji))
            }
            if (oldName != newName) {
                db.smsTransactionDao().updateCategoryName(oldName, newName)
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

    fun updateAccount(id: Int, newName: String, newIcon: String, newType: String) {
        viewModelScope.launch {
            val accounts = db.accountDao().getAllSync()
            val existing = accounts.find { it.id == id } ?: return@launch
            val oldName = existing.name

            // 1. Update the account itself
            db.accountDao().insert(existing.copy(name = newName, icon = newIcon, type = newType))

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

    fun addAccount(name: String, icon: String, type: String) {
        viewModelScope.launch {
            db.accountDao().insert(AccountEntity(name = name, icon = icon, type = type))
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

    fun performManualBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, lastImportMessage = "Creating rolling backup...") }
            try {
                val success = withContext(Dispatchers.IO) {
                    val backupManager = BackupManager(appContext)
                    backupManager.createAutomaticBackup()
                }
                if (success) {
                    _uiState.update { it.copy(
                        isImporting = false, 
                        lastImportMessage = "Backup created successfully in your selected folder."
                    ) }
                } else {
                    _uiState.update { it.copy(
                        isImporting = false, 
                        lastImportMessage = "Backup failed: Could not access selected folder. Please re-select it."
                    ) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isImporting = false, 
                    lastImportMessage = "Backup failed: ${e.message}"
                ) }
            }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearing = true) }
            try {
                // 1. Revoke all persisted URI permissions
                appContext.contentResolver.persistedUriPermissions.forEach { permission ->
                    try {
                        appContext.contentResolver.releasePersistableUriPermission(
                            permission.uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    } catch (e: Exception) {
                        // Ignore
                    }
                }

                // 2. Revoke SMS permissions if supported (API 33+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        val permissionsToRevoke = mutableListOf<String>()
                        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                            permissionsToRevoke.add(Manifest.permission.READ_SMS)
                        }
                        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) {
                            permissionsToRevoke.add(Manifest.permission.RECEIVE_SMS)
                        }
                        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                            permissionsToRevoke.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (permissionsToRevoke.isNotEmpty()) {
                            appContext.revokeSelfPermissionsOnKill(permissionsToRevoke)
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }

                // 3. The nuclear option: Clear all application user data
                // This will wipe the database, shared preferences, cache, and internal files.
                // It will also kill the app process, ensuring a clean state on next launch.
                val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val success = am.clearApplicationUserData()
                
                if (!success) {
                    // Fallback if the system call fails
                    db.smsTransactionDao().deleteAll()
                    db.smsAuditDao().deleteAll()
                    db.customCategoryDao().deleteAll()
                    db.accountDao().deleteAll()
                    db.accountAliasDao().deleteAll()
                    sharedPrefs.edit().clear().putBoolean("is_first_run", true).apply()
                    
                    _uiState.update { it.copy(
                        isClearing = false, 
                        lastImportMessage = "Partial clear successful. Some data may remain.",
                        isFirstRun = true
                    ) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isClearing = false, lastImportMessage = "Clear failed: ${e.message}") }
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
                    val name = prefs.optString("userName", "User")
                    val autoBackup = prefs.optBoolean("autoBackupEnabled", false)
                    val backupFreq = prefs.optString("backupFrequency", "Daily")
                    val folderUri: String? = if (!prefs.isNull("backupFolderUri")) prefs.optString("backupFolderUri") else null
                    val folderName: String? = if (!prefs.isNull("backupFolderName")) prefs.optString("backupFolderName") else null
                    
                    sharedPrefs.edit {
                        putString("app_theme", theme)
                        putString("user_name", name)
                        putBoolean("auto_backup_enabled", autoBackup)
                        putString("backup_frequency", backupFreq)
                        if (folderUri != null) putString("backup_folder_uri", folderUri)
                        if (folderName != null) putString("backup_folder_name", folderName)
                    }
                        
                    _uiState.update { it.copy(
                        appTheme = try { AppTheme.valueOf(theme) } catch (e: Exception) { AppTheme.AUTO },
                        userName = name,
                        isAutoBackupEnabled = autoBackup,
                        backupFrequency = backupFreq,
                        backupFolderUri = folderUri,
                        backupFolderName = folderName
                    ) }

                    backupScheduler.scheduleBackup(autoBackup, backupFreq)
                }

                // 1.1 Restore Budgets
                if (root.has("budgets")) {
                    val budgets = root.getJSONObject("budgets")
                    sharedPrefs.edit {
                        budgets.keys().forEach { key ->
                            val value = budgets.get(key)
                            if (value is Number) {
                                putFloat(key, value.toFloat())
                            } else if (value is String) {
                                value.toFloatOrNull()?.let { putFloat(key, it) }
                            }
                        }
                    }
                }

                // 2. Restore Transactions
                val txnsArray = root.optJSONArray("transactions")
                if (txnsArray != null) {
                    val txns = mutableListOf<SmsTransactionEntity>()
                    for (i in 0 until txnsArray.length()) {
                        val obj = txnsArray.getJSONObject(i)
                        txns.add(SmsTransactionEntity(
                            id = obj.optInt("id", 0),
                            sender = obj.getString("sender"),
                            body = obj.getString("body"),
                            amount = obj.getDouble("amount"),
                            originalAmount = if (obj.has("originalAmount") && !obj.isNull("originalAmount")) obj.getDouble("originalAmount") else null,
                            type = obj.getString("type"),
                            merchant = obj.getString("merchant"),
                            category = obj.getString("category"),
                            accountHint = obj.optString("accountHint", ""),
                            isBillable = obj.optBoolean("isBillable", true),
                            transactionTime = obj.getLong("transactionTime"),
                            hashKey = obj.getString("hashKey"),
                            bank = obj.optString("bank", ""),
                            note = obj.optString("note", ""),
                            linkedTransactionId = if (obj.has("linkedTransactionId") && !obj.isNull("linkedTransactionId")) obj.getInt("linkedTransactionId") else null
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
                            id = obj.optInt("id", 0),
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
                            id = obj.optInt("id", 0),
                            name = obj.getString("name"),
                            icon = obj.getString("icon"),
                            type = obj.optString("type", "BANK")
                        ))
                    }
                    db.accountDao().deleteAll()
                    accounts.forEach { db.accountDao().insert(it) }
                }

                // 6. Restore Aliases
                val aliasesArray = root.optJSONArray("accountAliases")
                if (aliasesArray != null) {
                    db.accountAliasDao().deleteAll()
                    for (i in 0 until aliasesArray.length()) {
                        val obj = aliasesArray.getJSONObject(i)
                        db.accountAliasDao().insert(AccountAliasEntity(
                            alias = obj.getString("alias"),
                            targetAccountName = obj.getString("targetAccountName")
                        ))
                    }
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

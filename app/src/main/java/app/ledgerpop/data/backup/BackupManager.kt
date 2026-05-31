package app.ledgerpop.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.ui.state.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class BackupManager(private val context: Context) {
    private val db = LedgerPopDatabase.getInstance(context)
    private val sharedPrefs = context.getSharedPreferences("ledgerpop_prefs", Context.MODE_PRIVATE)

    suspend fun createAutomaticBackup(): Boolean = withContext(Dispatchers.IO) {
        val folderUriStr = sharedPrefs.getString("backup_folder_uri", null) ?: return@withContext false
        val json = generateBackupJson()

        val folderUri = folderUriStr.toUri()
        val pickedDir = DocumentFile.fromTreeUri(context, folderUri)
        if (pickedDir != null && pickedDir.canWrite()) {
            performRollingBackupInFolder(pickedDir, json)
            return@withContext true
        }
        false
    }

    private fun performRollingBackupInFolder(folder: DocumentFile, json: String) {
        // 1. Delete backup_1.dat
        folder.findFile("backup_1.dat")?.delete()

        // 2. Rename backup_2->1, 3->2, 4->3, 5->4
        for (i in 2..5) {
            val currentFile = folder.findFile("backup_$i.dat")
            if (currentFile != null) {
                currentFile.renameTo("backup_${i - 1}.dat")
            }
        }

        // 3. Create new backup_5.dat
        val newFile = folder.createFile("application/octet-stream", "backup_5.dat")
        newFile?.uri?.let { uri ->
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(json.toByteArray())
            }
        }
    }

    suspend fun generateBackupJson(): String = withContext(Dispatchers.IO) {
        val transactions = db.smsTransactionDao().getAllTransactionsSync()
        val audits = db.smsAuditDao().getAllSync()
        val categories = db.customCategoryDao().getAllSync()
        val accounts = db.accountDao().getAllSync()
        val aliases = db.accountAliasDao().getAllAliases()

        val root = JSONObject()
        root.put("backupVersion", 3)
        root.put("timestamp", System.currentTimeMillis())
        root.put("appName", "LedgerPop")

        val prefsJson = JSONObject()
        prefsJson.put("appTheme", sharedPrefs.getString("app_theme", AppTheme.AUTO.name))
        prefsJson.put("userName", sharedPrefs.getString("user_name", "Kishor"))
        prefsJson.put("autoBackupEnabled", sharedPrefs.getBoolean("auto_backup_enabled", false))
        prefsJson.put("backupFrequency", sharedPrefs.getString("backup_frequency", "Daily"))
        prefsJson.put("backupFolderUri", sharedPrefs.getString("backup_folder_uri", null))
        prefsJson.put("backupFolderName", sharedPrefs.getString("backup_folder_name", null))
        root.put("preferences", prefsJson)

        val budgetsJson = JSONObject()
        sharedPrefs.all.forEach { (key, value) ->
            if (key.startsWith("budget_") || key == "monthly_budget") {
                budgetsJson.put(key, value)
            }
        }
        root.put("budgets", budgetsJson)

        val txnsArray = JSONArray()
        transactions.forEach { txn ->
            val obj = JSONObject()
            obj.put("id", txn.id)
            obj.put("sender", txn.sender)
            obj.put("body", txn.body)
            obj.put("amount", txn.amount)
            obj.put("originalAmount", txn.originalAmount)
            obj.put("type", txn.type)
            obj.put("merchant", txn.merchant)
            obj.put("category", txn.category)
            obj.put("accountHint", txn.accountHint)
            obj.put("isBillable", txn.isBillable)
            obj.put("transactionTime", txn.transactionTime)
            obj.put("hashKey", txn.hashKey)
            obj.put("bank", txn.bank)
            obj.put("note", txn.note)
            obj.put("linkedTransactionId", txn.linkedTransactionId)
            txnsArray.put(obj)
        }
        root.put("transactions", txnsArray)

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

        val categoriesArray = JSONArray()
        categories.forEach { cat ->
            val obj = JSONObject()
            obj.put("id", cat.id)
            obj.put("name", cat.name)
            obj.put("type", cat.type)
            obj.put("emoji", cat.emoji)
            categoriesArray.put(obj)
        }
        root.put("customCategories", categoriesArray)

        val accountsArray = JSONArray()
        accounts.forEach { acc ->
            val obj = JSONObject()
            obj.put("id", acc.id)
            obj.put("name", acc.name)
            obj.put("icon", acc.icon)
            obj.put("type", acc.type)
            accountsArray.put(obj)
        }
        root.put("accounts", accountsArray)

        val aliasesArray = JSONArray()
        aliases.forEach { alias ->
            val obj = JSONObject()
            obj.put("alias", alias.alias)
            obj.put("targetAccountName", alias.targetAccountName)
            aliasesArray.put(obj)
        }
        root.put("accountAliases", aliasesArray)

        return@withContext root.toString(2)
    }
}

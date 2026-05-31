package app.ledgerpop.ui.state

import app.ledgerpop.data.local.AccountEntity
import app.ledgerpop.data.local.AccountAliasEntity
import app.ledgerpop.data.local.CustomCategoryEntity
import app.ledgerpop.data.sms.ImportResult

enum class AppTheme {
    AUTO, LIGHT, DARK
}

data class SettingsUiState(
    val isImporting: Boolean = false,
    val isClearing: Boolean = false,
    val importedCount: Int = 0,
    val scannedCount: Int = 0,
    val totalTransactions: Int = 0,
    val lastImportMessage: String = "",
    val lastImportResult: ImportResult? = null,
    val userName: String = "User",
    val showDateRangePicker: Boolean = false,
    val dateRangeFromMillis: Long? = null,
    val dateRangeToMillis: Long? = null,
    val hasReadSmsPermission: Boolean = false,
    val hasReceiveSmsPermission: Boolean = false,
    val hasNotificationsPermission: Boolean = false,
    val appTheme: AppTheme = AppTheme.AUTO,
    val customCategories: List<CustomCategoryEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val accountAliases: List<AccountAliasEntity> = emptyList(),
    val isAutoBackupEnabled: Boolean = false,
    val backupFrequency: String = "Daily",
    val backupFolderUri: String? = null,
    val backupFolderName: String? = null,
    val isFirstRun: Boolean = false,
)

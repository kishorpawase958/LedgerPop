package app.ledgerpop.ui.state

data class SettingsUiState(
    val isImporting: Boolean = false,
    val isClearing: Boolean = false,
    val importedCount: Int = 0,
    val totalTransactions: Int = 0,
    val lastImportMessage: String = "",
    val showDateRangePicker: Boolean = false,
    val dateRangeFromMillis: Long? = null,
    val dateRangeToMillis: Long? = null
)
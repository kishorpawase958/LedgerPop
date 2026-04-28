package app.ledgerpop.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.sms.SmsImporter
import app.ledgerpop.data.sms.SmsReader
import app.ledgerpop.ui.state.AppTheme
import app.ledgerpop.ui.state.SettingsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val db: LedgerPopDatabase,
    context: Context
) : ViewModel() {

    private val appContext = context.applicationContext
    private val sharedPrefs = appContext.getSharedPreferences("ledgerpop_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SettingsUiState(
        appTheme = AppTheme.valueOf(sharedPrefs.getString("app_theme", AppTheme.AUTO.name) ?: AppTheme.AUTO.name)
    ))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val smsImporter = SmsImporter(
        appContext,
        SmsReader(appContext),
        db.smsTransactionDao(),
        db.smsAuditDao()
    )

    init {
        refreshPermissions()
        // Observe transaction count
        viewModelScope.launch {
            db.smsTransactionDao().getAllTransactions().collect { list ->
                _uiState.update { it.copy(totalTransactions = list.size) }
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
        _uiState.update { it.copy(userName = newName) }
    }

    fun clearImportResult() {
        _uiState.update { it.copy(lastImportResult = null) }
    }

    fun deleteAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearing = true) }
            try {
                db.smsTransactionDao().deleteAll()
                db.smsAuditDao().deleteAll()
                _uiState.update { it.copy(isClearing = false, lastImportMessage = "All data cleared.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isClearing = false, lastImportMessage = "Clear failed: ${e.message}") }
            }
        }
    }

    fun backupData(context: Context) {
        // Implementation for backup
    }

    fun restoreData(context: Context, uri: Uri?) {
        // Implementation for restore
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

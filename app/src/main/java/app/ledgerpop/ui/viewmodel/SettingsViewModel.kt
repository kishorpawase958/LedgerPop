package app.ledgerpop.ui.viewmodel

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.sms.SmsImporter
import app.ledgerpop.data.sms.SmsReader
import app.ledgerpop.ui.state.SettingsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val db: LedgerPopDatabase,
    contentResolver: ContentResolver
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val smsImporter = SmsImporter(
        SmsReader(contentResolver),
        db.smsTransactionDao(),
        db.smsAuditDao()
    )

    init {
        // Observe transaction count
        viewModelScope.launch {
            db.smsTransactionDao().getAllTransactions().collect { list ->
                _uiState.update { it.copy(totalTransactions = list.size) }
            }
        }
    }

    fun importSms() {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, lastImportMessage = "") }
            try {
                val count = smsImporter.importInbox()
                _uiState.update { it.copy(
                    isImporting = false,
                    lastImportMessage = "Imported $count new transactions."
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
        val from = uiState.value.dateRangeFromMillis
        val to = uiState.value.dateRangeToMillis

        if (from == null || to == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, lastImportMessage = "") }
            try {
                val count = smsImporter.importInbox(fromMillis = from, toMillis = to)
                _uiState.update { it.copy(
                    isImporting = false,
                    lastImportMessage = "Imported $count new transactions from the selected range."
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

    companion object {
        fun factory(db: LedgerPopDatabase, contentResolver: ContentResolver): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(db, contentResolver) as T
                }
            }
    }
}

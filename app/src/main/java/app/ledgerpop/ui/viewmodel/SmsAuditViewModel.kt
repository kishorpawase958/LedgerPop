package app.ledgerpop.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.local.SmsAuditEntity
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.data.parser.SmsParser
import app.ledgerpop.ui.state.AuditFilter
import app.ledgerpop.ui.state.SmsAuditUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SmsAuditViewModel(
    private val database: LedgerPopDatabase
) : ViewModel() {

    private val auditDao = database.smsAuditDao()
    private val transactionDao = database.smsTransactionDao()

    private val _uiState = MutableStateFlow(SmsAuditUiState(isLoading = true))
    val uiState: StateFlow<SmsAuditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            auditDao.getAll().collect { entries ->
                val imported = entries.count { it.status == "IMPORTED" }
                val skipped = entries.count { it.status == "SKIPPED" }
                val parseFailed = entries.count { it.status == "PARSE_FAILED" }
                val reported = entries.count { it.reportType.isNotBlank() }

                _uiState.update { state ->
                    val updated = state.copy(
                        allEntries = entries,
                        totalSeen = entries.size,
                        totalImported = imported,
                        totalSkipped = skipped,
                        totalParseFailed = parseFailed,
                        totalReported = reported,
                        isLoading = false
                    )
                    updated.copy(
                        filteredEntries = applyFilter(
                            entries = entries,
                            filter = state.selectedFilter,
                            query = state.searchQuery
                        )
                    )
                }
            }
        }
    }

    // ── Filter + search ───────────────────────────────────────────────────────

    fun onFilterChange(filter: AuditFilter) {
        _uiState.update { state ->
            state.copy(
                selectedFilter = filter,
                filteredEntries = applyFilter(state.allEntries, filter, state.searchQuery)
            )
        }
    }

    fun onSearchChange(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredEntries = applyFilter(state.allEntries, state.selectedFilter, query)
            )
        }
    }

    private fun applyFilter(
        entries: List<SmsAuditEntity>,
        filter: AuditFilter,
        query: String
    ): List<SmsAuditEntity> {
        val byFilter = when (filter) {
            AuditFilter.ALL         -> entries
            AuditFilter.IMPORTED    -> entries.filter { it.status == "IMPORTED" }
            AuditFilter.SKIPPED     -> entries.filter { it.status == "SKIPPED" }
            AuditFilter.PARSE_FAILED -> entries.filter { it.status == "PARSE_FAILED" }
            AuditFilter.REPORTED    -> entries.filter { it.reportType.isNotBlank() }
        }
        return if (query.isBlank()) byFilter
        else byFilter.filter {
            it.sender.contains(query, ignoreCase = true) ||
                    it.body.contains(query, ignoreCase = true) ||
                    it.skipReason.contains(query, ignoreCase = true)
        }
    }

    // ── Expand / collapse SMS body ────────────────────────────────────────────

    fun toggleExpand(id: Int) {
        _uiState.update { state ->
            state.copy(expandedEntryId = if (state.expandedEntryId == id) null else id)
        }
    }

    // ── Report dialog ─────────────────────────────────────────────────────────

    fun showReportDialog(entry: SmsAuditEntity) {
        _uiState.update {
            it.copy(
                showReportDialog = true,
                reportingEntry = entry,
                reportNote = ""
            )
        }
    }

    fun hideReportDialog() {
        _uiState.update {
            it.copy(
                showReportDialog = false,
                reportingEntry = null,
                reportNote = ""
            )
        }
    }

    fun onReportNoteChange(note: String) {
        _uiState.update { it.copy(reportNote = note) }
    }

    /**
     * Submit a report:
     * - FALSE_POSITIVE → "This was wrongly imported as a transaction". Action: DELETE from transactions.
     * - FALSE_NEGATIVE → "This was wrongly skipped / missed". Action: INSERT into transactions.
     */
    fun submitReport(reportType: String) {
        val state = _uiState.value
        val entry = state.reportingEntry ?: return

        viewModelScope.launch {
            // Update the audit table
            auditDao.updateReport(
                id = entry.id,
                reportType = reportType,
                note = state.reportNote.trim()
            )

            // Bridge logic: Update the actual transactions table based on the report type
            when (reportType) {
                "FALSE_POSITIVE" -> {
                    // Delete the falsely imported transaction using its hashKey
                    val txnToDelete = transactionDao.getTransactionByHash(entry.hashKey)
                    if (txnToDelete != null) {
                        transactionDao.delete(txnToDelete)
                    }
                }
                "FALSE_NEGATIVE" -> {
                    // Re-parse the skipped message even if it looks like spam
                    val parsed = SmsParser.parse(entry.sender, entry.body, ignoreSpamCheck = true)

                    val newTxn = SmsTransactionEntity(
                        sender = entry.sender,
                        body = entry.body,
                        amount = parsed?.amount ?: 0.0,
                        type = parsed?.type ?: "DEBIT",
                        merchant = parsed?.merchant ?: "Manual Recovery",
                        category = parsed?.category ?: "Uncategorized",
                        bank = parsed?.bank ?: "Unknown Bank",
                        accountHint = parsed?.accountName ?: "Recovery",
                        transactionTime = entry.timestamp,
                        hashKey = entry.hashKey,
                        isBillable = parsed?.includeInAnalytics ?: true
                    )
                    transactionDao.insert(newTxn)
                }
            }

            _uiState.update {
                it.copy(
                    showReportDialog = false,
                    reportingEntry = null,
                    reportNote = ""
                )
            }
        }
    }

    // ── Clear reported flag ───────────────────────────────────────────────────

    fun clearReport(entryId: Int) {
        viewModelScope.launch {
            val entry = auditDao.getEntryById(entryId) ?: return@launch

            // If we are clearing the report, we should revert the action taken.
            if (entry.reportType == "FALSE_POSITIVE") {
                // It was deleted, we should add it back.
                val fallbackAmount = if (entry.parsedAmount > 0) entry.parsedAmount else 0.0
                val fallbackType = if (entry.parsedType.isNotBlank()) entry.parsedType else "DEBIT"

                val recoveredTxn = SmsTransactionEntity(
                    sender = entry.sender,
                    body = entry.body,
                    amount = fallbackAmount,
                    type = fallbackType,
                    merchant = "Recovered Import",
                    category = "Uncategorized",
                    bank = "Unknown",
                    accountHint = "Recovery",
                    transactionTime = entry.timestamp,
                    hashKey = entry.hashKey,
                    isBillable = true
                )
                transactionDao.insert(recoveredTxn)

            } else if (entry.reportType == "FALSE_NEGATIVE") {
                // It was added manually, we should delete it.
                val txnToDelete = transactionDao.getTransactionByHash(entry.hashKey)
                if (txnToDelete != null) {
                    transactionDao.delete(txnToDelete)
                }
            }

            auditDao.updateReport(id = entryId, reportType = "", note = "")
        }
    }

    companion object {
        fun factory(database: LedgerPopDatabase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SmsAuditViewModel(database) as T
                }
            }
    }
}
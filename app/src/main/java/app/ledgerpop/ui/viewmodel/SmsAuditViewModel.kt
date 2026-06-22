package app.ledgerpop.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.local.SmartRuleEntity
import app.ledgerpop.data.local.SmsAuditEntity
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.data.category.CategoryEngine
import app.ledgerpop.data.local.AccountEntity
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
    private val smartRuleDao = database.smartRuleDao()
    private val aliasDao = database.accountAliasDao()
    private val accountDao = database.accountDao()

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
            // 1. Process the primary entry
            processCorrection(entry, reportType, state.reportNote.trim())

            // 2. Find historical similar messages that haven't been corrected yet
            val structure = SmsParser.getStructure(entry.body)
            val similar = state.allEntries.filter {
                it.id != entry.id &&
                        it.sender == entry.sender &&
                        it.reportType.isBlank() &&
                        SmsParser.getStructure(it.body) == structure
            }

            if (similar.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        showReportDialog = false,
                        showSimilarEntriesDialog = true,
                        similarEntries = similar,
                        selectedSimilarIds = similar.map { e -> e.id }.toSet(),
                        retroactiveReportType = reportType
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        showReportDialog = false,
                        reportingEntry = null,
                        reportNote = ""
                    )
                }
            }
        }
    }

    fun toggleSimilarSelection(id: Int) {
        _uiState.update { state ->
            val newSelected = if (state.selectedSimilarIds.contains(id)) {
                state.selectedSimilarIds - id
            } else {
                state.selectedSimilarIds + id
            }
            state.copy(selectedSimilarIds = newSelected)
        }
    }

    fun applyRetroactiveCorrections() {
        val state = _uiState.value
        val reportType = state.retroactiveReportType
        val selectedIds = state.selectedSimilarIds
        val note = state.reportNote.trim()

        viewModelScope.launch {
            val toCorrect = state.similarEntries.filter { selectedIds.contains(it.id) }
            toCorrect.forEach { entry ->
                processCorrection(entry, reportType, note)
            }
            hideSimilarEntriesDialog()
        }
    }

    fun hideSimilarEntriesDialog() {
        _uiState.update {
            it.copy(
                showSimilarEntriesDialog = false,
                similarEntries = emptyList(),
                selectedSimilarIds = emptySet(),
                reportingEntry = null,
                reportNote = "",
                retroactiveReportType = ""
            )
        }
    }

    private suspend fun processCorrection(entry: SmsAuditEntity, reportType: String, note: String) {
        // Update the audit table
        auditDao.updateReport(
            id = entry.id,
            reportType = reportType,
            note = note
        )

        // Bridge logic: Update the actual transactions table based on the report type
        when (reportType) {
            "FALSE_POSITIVE" -> {
                // Delete the falsely imported transaction using its hashKey
                val txnToDelete = transactionDao.getTransactionByHash(entry.hashKey)
                if (txnToDelete != null) {
                    transactionDao.delete(txnToDelete)
                }

                // Smart Learning: Remember this structure to ALWAYS SKIP it in the future
                val structure = SmsParser.getStructure(entry.body)
                smartRuleDao.insert(
                    SmartRuleEntity(
                        sender = entry.sender,
                        bodyStructure = structure,
                        ruleType = "ALWAYS_SKIP"
                    )
                )
            }
            "FALSE_NEGATIVE" -> {
                // Re-parse the skipped message even if it looks like spam
                val parsed = SmsParser.parse(entry.sender, entry.body, ignoreSpamCheck = true)

                val merchant = resolveMerchantName(parsed?.merchant ?: "")
                val category = resolveCategory(merchant, entry.body, entry.sender)
                val account = resolveAccountName(parsed?.accountName ?: "")

                val newTxn = SmsTransactionEntity(
                    sender = entry.sender,
                    body = entry.body,
                    amount = parsed?.amount ?: 0.0,
                    type = parsed?.type ?: "DEBIT",
                    merchant = if (merchant.isNotBlank()) merchant else "Manual Recovery",
                    category = category,
                    bank = parsed?.bank ?: "Unknown Bank",
                    accountHint = account,
                    transactionTime = entry.timestamp,
                    hashKey = entry.hashKey,
                    isBillable = parsed?.includeInAnalytics ?: true
                )
                transactionDao.insert(newTxn)

                // Smart Learning: Remember this structure to ALWAYS IMPORT it in the future
                val structure = SmsParser.getStructure(entry.body)
                smartRuleDao.insert(
                    SmartRuleEntity(
                        sender = entry.sender,
                        bodyStructure = structure,
                        ruleType = "ALWAYS_IMPORT"
                    )
                )
            }
        }
    }

    // ── Clear reported flag ───────────────────────────────────────────────────

    fun clearReport(entryId: Int) {
        viewModelScope.launch {
            val entry = auditDao.getEntryById(entryId) ?: return@launch
            val state = _uiState.value

            // Find similar reported entries
            val structure = SmsParser.getStructure(entry.body)
            val similar = state.allEntries.filter {
                it.id != entry.id &&
                        it.sender == entry.sender &&
                        it.reportType == entry.reportType &&
                        SmsParser.getStructure(it.body) == structure
            }

            if (similar.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        showClearSimilarDialog = true,
                        similarEntries = similar,
                        selectedSimilarIds = similar.map { e -> e.id }.toSet(),
                        reportingEntry = entry // Reuse this to keep track of the trigger entry
                    )
                }
            } else {
                processClearReport(entry)
            }
        }
    }

    fun applyBatchClear() {
        val state = _uiState.value
        val triggerEntry = state.reportingEntry ?: return
        val selectedIds = state.selectedSimilarIds

        viewModelScope.launch {
            // Clear the trigger entry
            processClearReport(triggerEntry)

            // Clear selected similar entries
            val toClear = state.similarEntries.filter { selectedIds.contains(it.id) }
            toClear.forEach { entry ->
                processClearReport(entry)
            }

            hideClearSimilarDialog()
        }
    }

    fun hideClearSimilarDialog() {
        _uiState.update {
            it.copy(
                showClearSimilarDialog = false,
                similarEntries = emptyList(),
                selectedSimilarIds = emptySet(),
                reportingEntry = null
            )
        }
    }

    private suspend fun processClearReport(entry: SmsAuditEntity) {
        // If we are clearing the report, we should revert the action taken.
        if (entry.reportType == "FALSE_POSITIVE") {
            // It was deleted, we should add it back.
            // Re-parse to get all original details (Merchant, Category, Account, etc.)
            val parsed = SmsParser.parse(entry.sender, entry.body, ignoreSpamCheck = true)
            
            val merchant = resolveMerchantName(parsed?.merchant ?: "")
            val category = resolveCategory(merchant, entry.body, entry.sender)
            val account = resolveAccountName(parsed?.accountName ?: "")

            val recoveredTxn = SmsTransactionEntity(
                sender = entry.sender,
                body = entry.body,
                amount = parsed?.amount ?: (if (entry.parsedAmount > 0) entry.parsedAmount else 0.0),
                type = parsed?.type ?: (if (entry.parsedType.isNotBlank()) entry.parsedType else "DEBIT"),
                merchant = if (merchant.isNotBlank()) merchant else "Recovered Import",
                category = category,
                bank = parsed?.bank ?: "Unknown Bank",
                accountHint = account,
                transactionTime = entry.timestamp,
                hashKey = entry.hashKey,
                isBillable = parsed?.includeInAnalytics ?: true
            )
            transactionDao.insert(recoveredTxn)

        } else if (entry.reportType == "FALSE_NEGATIVE") {
            // It was added manually, we should delete it.
            val txnToDelete = transactionDao.getTransactionByHash(entry.hashKey)
            if (txnToDelete != null) {
                transactionDao.delete(txnToDelete)
            }
        }

        // Remove the smart rule associated with this correction
        val structure = SmsParser.getStructure(entry.body)
        smartRuleDao.deleteRule(entry.sender, structure)

        auditDao.updateReport(id = entry.id, reportType = "", note = "")
    }

    private suspend fun resolveAccountName(name: String): String {
        if (name.isBlank()) return ""
        val target = aliasDao.getTargetName(name) ?: name
        if (target.isNotBlank()) {
            if (accountDao.getByName(target) == null) {
                accountDao.insert(AccountEntity(name = target))
            }
        }
        return target
    }

    private suspend fun resolveMerchantName(name: String): String {
        if (name.isBlank()) return ""
        return aliasDao.getTargetName(name) ?: name
    }

    private suspend fun resolveCategory(merchant: String, body: String, sender: String): String {
        if (merchant.isBlank()) return CategoryEngine.categorize(merchant, body, sender)
        val normalized = CategoryEngine.normalizeMerchant(merchant)
        transactionDao.getLastCategoryForMerchant(normalized)?.let { return it }
        transactionDao.getLastCategoryForMerchant(merchant)?.let { return it }
        if (normalized.length >= 3) {
            transactionDao.getLastCategoryForMerchantFuzzy(normalized)?.let { return it }
        }
        if (merchant.length >= 3) {
            transactionDao.getLastCategoryForMerchantFuzzy(merchant)?.let { return it }
        }
        return CategoryEngine.categorize(merchant, body, sender)
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
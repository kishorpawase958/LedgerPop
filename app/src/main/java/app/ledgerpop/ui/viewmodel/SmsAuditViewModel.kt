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
import app.ledgerpop.data.sms.SmsFilter
import app.ledgerpop.ui.state.AuditFilter
import app.ledgerpop.ui.state.SmsAuditUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            auditDao.getAll().collect { entries ->
                val imported = entries.count { it.status == "IMPORTED" }
                val skipped = entries.count { it.status == "SKIPPED" }
                val parseFailed = entries.count { it.status == "PARSE_FAILED" }
                val reported = entries.count { it.reportType.isNotBlank() }

                _uiState.update { state ->
                    val isReparsing = state.loadingProgress != null
                    val updated = state.copy(
                        allEntries = entries,
                        totalSeen = entries.size,
                        totalImported = imported,
                        totalSkipped = skipped,
                        totalParseFailed = parseFailed,
                        totalReported = reported,
                        isLoading = if (isReparsing) state.isLoading else false,
                        loadingProgress = if (isReparsing) state.loadingProgress else null,
                        processingCount = if (isReparsing) state.processingCount else 0,
                        processingCurrent = if (isReparsing) state.processingCurrent else 0
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
                .sortedByDescending { it.reportTimestamp }
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
            val newExpanded = if (state.expandedAuditIds.contains(id)) {
                state.expandedAuditIds - id
            } else {
                state.expandedAuditIds + id
            }
            state.copy(expandedAuditIds = newExpanded)
        }
    }

    // ── Selection Mode ────────────────────────────────────────────────────────

    fun toggleSelection(id: Int) {
        _uiState.update { state ->
            val newSelected = if (state.selectedAuditIds.contains(id)) {
                state.selectedAuditIds - id
            } else {
                state.selectedAuditIds + id
            }
            state.copy(
                selectedAuditIds = newSelected,
                isSelectionMode = newSelected.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedAuditIds = emptySet(), isSelectionMode = false) }
    }

    fun selectAll() {
        _uiState.update { state ->
            val allIds = state.filteredEntries.map { it.id }.toSet()
            val allSelected = state.selectedAuditIds.containsAll(allIds) && allIds.isNotEmpty()
            val newSelected = if (allSelected) emptySet() else allIds
            state.copy(selectedAuditIds = newSelected, isSelectionMode = newSelected.isNotEmpty())
        }
    }

    fun reparseSelected() {
        val selectedIds = _uiState.value.selectedAuditIds
        if (selectedIds.isEmpty()) return

        _uiState.update { it.copy(isLoading = true, loadingProgress = 0f, processingCount = 0, processingCurrent = 0, isSelectionMode = false, selectedAuditIds = emptySet()) }
        viewModelScope.launch {
            val allAudit = auditDao.getAllSync()
            val toReparse = allAudit.filter { selectedIds.contains(it.id) }
            val total = toReparse.size
            var changedCount = 0
            toReparse.forEachIndexed { index, entry ->
                if (reparseEntry(entry)) changedCount++
                _uiState.update { it.copy(
                    loadingProgress = (index + 1).toFloat() / total,
                    processingCount = total,
                    processingCurrent = index + 1
                ) }
            }
            _uiState.update { it.copy(isLoading = false, loadingProgress = null, processingCount = 0, processingCurrent = 0) }
            _events.emit("Processed $total messages. $changedCount updated.")
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

    fun showBulkReportDialog() {
        _uiState.update { it.copy(showBulkReportDialog = true, reportNote = "") }
    }

    fun hideBulkReportDialog() {
        _uiState.update { it.copy(showBulkReportDialog = false, reportNote = "") }
    }

    fun applyBulkReport(reportType: String) {
        val state = _uiState.value
        val selectedIds = state.selectedAuditIds
        if (selectedIds.isEmpty()) return

        _uiState.update {
            it.copy(
                isLoading = true,
                loadingProgress = 0f,
                processingCount = 0,
                processingCurrent = 0,
                isSelectionMode = false,
                selectedAuditIds = emptySet(),
                showBulkReportDialog = false
            )
        }

        viewModelScope.launch {
            val allAudit = auditDao.getAllSync()
            val toCorrect = allAudit.filter { selectedIds.contains(it.id) }
            val total = toCorrect.size
            val note = state.reportNote.trim().ifBlank { "Bulk report" }
            toCorrect.forEachIndexed { index, entry ->
                processCorrection(entry, reportType, note)
                _uiState.update { it.copy(
                    loadingProgress = (index + 1).toFloat() / total,
                    processingCount = total,
                    processingCurrent = index + 1
                ) }
            }
            _uiState.update { it.copy(isLoading = false, loadingProgress = null, processingCount = 0, processingCurrent = 0) }
            _events.emit("Applied report to $total messages.")
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

        _uiState.update { it.copy(isLoading = true, loadingProgress = 0f, processingCount = 0, processingCurrent = 0) }
        viewModelScope.launch {
            val toCorrect = state.similarEntries.filter { selectedIds.contains(it.id) }
            val total = toCorrect.size
            toCorrect.forEachIndexed { index, entry ->
                processCorrection(entry, reportType, note)
                _uiState.update { it.copy(
                    loadingProgress = (index + 1).toFloat() / total,
                    processingCount = total,
                    processingCurrent = index + 1
                ) }
            }
            _uiState.update { it.copy(isLoading = false, loadingProgress = null, processingCount = 0, processingCurrent = 0) }
            _events.emit("Retroactively corrected $total messages.")
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
            note = note,
            timestamp = System.currentTimeMillis()
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

        _uiState.update { it.copy(isLoading = true, loadingProgress = 0f, processingCount = 0, processingCurrent = 0) }
        viewModelScope.launch {
            // Clear the trigger entry
            processClearReport(triggerEntry)

            // Clear selected similar entries
            val toClear = state.similarEntries.filter { selectedIds.contains(it.id) }
            val total = toClear.size
            toClear.forEachIndexed { index, entry ->
                processClearReport(entry)
                if (total > 0) {
                    _uiState.update { it.copy(
                        loadingProgress = (index + 1).toFloat() / total,
                        processingCount = total,
                        processingCurrent = index + 1
                    ) }
                }
            }

            _uiState.update { it.copy(isLoading = false, loadingProgress = null, processingCount = 0, processingCurrent = 0) }
            _events.emit("Cleared reports for ${total + 1} messages.")
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

        auditDao.updateReport(id = entry.id, reportType = "", note = "", timestamp = 0L)
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

    // ── Reparse All ──────────────────────────────────────────────────────────

    fun reparseAll() {
        _uiState.update { it.copy(isLoading = true, loadingProgress = 0f, processingCount = 0, processingCurrent = 0) }
        viewModelScope.launch {
            val allAudit = auditDao.getAllSync()
            val total = allAudit.size
            var changedCount = 0
            allAudit.forEachIndexed { index, entry ->
                if (reparseEntry(entry)) changedCount++
                _uiState.update { it.copy(
                    loadingProgress = (index + 1).toFloat() / total,
                    processingCount = total,
                    processingCurrent = index + 1
                ) }
            }
            _uiState.update { it.copy(isLoading = false, loadingProgress = null, processingCount = 0, processingCurrent = 0) }
            _events.emit("Reparsed $total messages. $changedCount updated/moved.")
        }
    }

    private suspend fun reparseEntry(entry: SmsAuditEntity): Boolean {
        val oldStatus = entry.status

        val structure = SmsParser.getStructure(entry.body)
        val rules = smartRuleDao.getBySender(entry.sender)
        val smartAction = rules.find { it.bodyStructure == structure }?.ruleType

        val shouldProcess = when (smartAction) {
            "ALWAYS_IMPORT" -> true
            "ALWAYS_SKIP" -> false
            else -> SmsFilter.shouldProcess(entry.sender, entry.body)
        }
        val reason = when (smartAction) {
            "ALWAYS_IMPORT" -> "Smart Rule: ALWAYS_IMPORT"
            "ALWAYS_SKIP" -> "Smart Rule: ALWAYS_SKIP"
            else -> SmsFilter.skipReason(entry.sender, entry.body)
        }

        if (!shouldProcess) {
            val newHashKey = SmsParser.buildHashKey(entry.sender, entry.timestamp, 0.0, "SKIPPED")
            
            // If it was previously IMPORTED, delete the transaction
            if (entry.status == "IMPORTED") {
                transactionDao.getTransactionByHash(entry.hashKey)?.let {
                    transactionDao.delete(it)
                }
            }

            // Flag as FALSE_POSITIVE if it was IMPORTED but now SKIPPED
            val newReportType = if (entry.status == "IMPORTED") "FALSE_POSITIVE" else entry.reportType
            val newNote = if (entry.status == "IMPORTED") "Reparsed: Now correctly skipped" else entry.reportNote
            val newReportTimestamp = if (entry.status == "IMPORTED") System.currentTimeMillis() else entry.reportTimestamp

            auditDao.update(
                entry.copy(
                    status = "SKIPPED",
                    skipReason = reason,
                    parsedAmount = 0.0,
                    parsedType = "",
                    hashKey = newHashKey,
                    reportType = newReportType,
                    reportNote = newNote,
                    reportTimestamp = newReportTimestamp
                )
            )
            return oldStatus != "SKIPPED"
        }

        val parsed = SmsParser.parse(entry.sender, entry.body, ignoreSpamCheck = smartAction == "ALWAYS_IMPORT")
        if (parsed == null) {
            val newHashKey = SmsParser.buildHashKey(entry.sender, entry.timestamp, 0.0, "PARSE_FAILED")
            if (entry.status == "IMPORTED") {
                transactionDao.getTransactionByHash(entry.hashKey)?.let {
                    transactionDao.delete(it)
                }
            }
            auditDao.update(
                entry.copy(
                    status = "PARSE_FAILED",
                    skipReason = "Parser returned null",
                    parsedAmount = 0.0,
                    parsedType = "",
                    hashKey = newHashKey,
                    reportType = if (entry.status == "IMPORTED") "FALSE_POSITIVE" else entry.reportType,
                    reportNote = if (entry.status == "IMPORTED") "Reparsed: Now failed to parse" else entry.reportNote,
                    reportTimestamp = if (entry.status == "IMPORTED") System.currentTimeMillis() else entry.reportTimestamp
                )
            )
            return oldStatus != "PARSE_FAILED"
        }

        val newHashKey = SmsParser.buildHashKey(entry.sender, entry.timestamp, parsed.amount, parsed.type, parsed.refNo)

        // Update or Insert transaction
        val merchant = resolveMerchantName(parsed.merchant)
        val category = resolveCategory(merchant, entry.body, entry.sender)
        val account = resolveAccountName(parsed.accountName)

        val existingTxn = transactionDao.getTransactionByHash(entry.hashKey)
        var changed = false
        if (existingTxn != null) {
            val updatedTxn = existingTxn.copy(
                amount = parsed.amount,
                type = parsed.type,
                merchant = merchant,
                category = category,
                bank = parsed.bank,
                accountHint = account,
                isBillable = parsed.includeInAnalytics,
                hashKey = newHashKey
            )
            if (updatedTxn != existingTxn) {
                transactionDao.update(updatedTxn)
                changed = true
            }
        } else {
            // Check if it exists with the new hashKey to avoid duplicates
            if (transactionDao.exists(newHashKey) == 0) {
                transactionDao.insert(
                    SmsTransactionEntity(
                        sender = entry.sender,
                        body = entry.body,
                        amount = parsed.amount,
                        type = parsed.type,
                        merchant = merchant,
                        category = category,
                        bank = parsed.bank,
                        accountHint = account,
                        transactionTime = entry.timestamp,
                        hashKey = newHashKey,
                        isBillable = parsed.includeInAnalytics
                    )
                )
                changed = true
            }
        }

        // Flag as FALSE_NEGATIVE if it was SKIPPED but now IMPORTED
        val newReportType = if (entry.status == "SKIPPED" || entry.status == "PARSE_FAILED") "FALSE_NEGATIVE" else entry.reportType
        val newNote = if (entry.status == "SKIPPED" || entry.status == "PARSE_FAILED") "Reparsed: Now correctly imported" else entry.reportNote
        val newReportTimestamp = if (entry.status == "SKIPPED" || entry.status == "PARSE_FAILED") System.currentTimeMillis() else entry.reportTimestamp

        val updatedEntry = entry.copy(
            status = "IMPORTED",
            skipReason = "",
            parsedAmount = parsed.amount,
            parsedType = parsed.type,
            hashKey = newHashKey,
            reportType = newReportType,
            reportNote = newNote,
            reportTimestamp = newReportTimestamp
        )
        if (updatedEntry != entry) {
            auditDao.update(updatedEntry)
            changed = true
        }
        
        return changed || oldStatus != "IMPORTED"
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
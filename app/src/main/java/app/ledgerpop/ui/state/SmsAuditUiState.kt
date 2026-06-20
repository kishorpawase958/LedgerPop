package app.ledgerpop.ui.state

import app.ledgerpop.data.local.SmsAuditEntity

enum class AuditFilter { ALL, IMPORTED, SKIPPED, PARSE_FAILED, REPORTED }

data class SmsAuditUiState(
    val allEntries: List<SmsAuditEntity> = emptyList(),
    val filteredEntries: List<SmsAuditEntity> = emptyList(),
    val selectedFilter: AuditFilter = AuditFilter.ALL,
    val searchQuery: String = "",

    // Stats
    val totalSeen: Int = 0,
    val totalImported: Int = 0,
    val totalSkipped: Int = 0,
    val totalParseFailed: Int = 0,
    val totalReported: Int = 0,

    // Report dialog
    val showReportDialog: Boolean = false,
    val reportingEntry: SmsAuditEntity? = null,
    val reportNote: String = "",

    // Detail expand — id of currently expanded SMS body
    val expandedEntryId: Int? = null,

    // Retroactive correction
    val showSimilarEntriesDialog: Boolean = false,
    val showClearSimilarDialog: Boolean = false,
    val similarEntries: List<SmsAuditEntity> = emptyList(),
    val selectedSimilarIds: Set<Int> = emptySet(),
    val retroactiveReportType: String = "",

    val isLoading: Boolean = true
)
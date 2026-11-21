package app.ledgerpop.ui.state

import app.ledgerpop.data.local.SmsAuditEntity
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.data.local.SpamKeywordEntity

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
    val showBulkReportDialog: Boolean = false,
    val reportingEntry: SmsAuditEntity? = null,
    val reportNote: String = "",

    // Detail expand — ids of expanded SMS bodies
    val expandedAuditIds: Set<Int> = emptySet(),

    // Retroactive correction
    val showSimilarEntriesDialog: Boolean = false,
    val showClearSimilarDialog: Boolean = false,
    val similarEntries: List<SmsAuditEntity> = emptyList(),
    val selectedSimilarIds: Set<Int> = emptySet(),
    val retroactiveReportType: String = "",

    // Spam Keywords Management & Retroactive Audit
    val spamKeywords: List<SpamKeywordEntity> = emptyList(),
    val showAddSpamKeywordDialog: Boolean = false,
    val showEditSpamKeywordDialog: Boolean = false,
    val editingSpamKeyword: SpamKeywordEntity? = null,
    val showSpamKeywordsSection: Boolean = false,
    val showRetroactiveSpamDialog: Boolean = false,
    val retroactiveSpamKeyword: String = "",
    val matchedSpamTransactions: List<SmsTransactionEntity> = emptyList(),
    val selectedSpamTxnIds: Set<Int> = emptySet(),

    val selectedAuditIds: Set<Int> = emptySet(),
    val isSelectionMode: Boolean = false,

    val isLoading: Boolean = true,
    val loadingProgress: Float? = null,
    val processingCount: Int = 0,
    val processingCurrent: Int = 0
)
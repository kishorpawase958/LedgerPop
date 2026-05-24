package app.ledgerpop.ui.state

import app.ledgerpop.data.local.CustomCategoryEntity
import app.ledgerpop.data.local.SmsTransactionEntity

data class TransactionsUiState(
    val allTransactions: List<SmsTransactionEntity> = emptyList(),
    val filteredTransactions: List<SmsTransactionEntity> = emptyList(),
    val availableCategories: List<String> = emptyList(),
    val availableAccounts: List<String> = emptyList(),
    val trendSummaries: List<TrendSummary> = emptyList(),
    val customCategories: List<CustomCategoryEntity> = emptyList(),
    val query: String = "",
    val selectedFilter: String = "All",
    val selectedCategory: String = "All",
    val selectedAccount: String = "All",
    val startDateMillis: Long? = null,
    val endDateMillis: Long? = null,
    val selectedMonth: String? = null,
    val isLoading: Boolean = true
)
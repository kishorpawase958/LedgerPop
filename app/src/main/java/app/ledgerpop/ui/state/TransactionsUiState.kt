package app.ledgerpop.ui.state

import app.ledgerpop.data.local.CustomCategoryEntity
import app.ledgerpop.data.local.SmsTransactionEntity

enum class TransactionSortOrder(val label: String) {
    DATE_DESC("Date (Newest)"),
    DATE_ASC("Date (Oldest)"),
    AMOUNT_DESC("Amount (High)"),
    AMOUNT_ASC("Amount (Low)"),
    MERCHANT_ASC("Merchant (A-Z)"),
    MERCHANT_DESC("Merchant (Z-A)")
}

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
    val selectedSortOrder: TransactionSortOrder = TransactionSortOrder.DATE_DESC,
    val startDateMillis: Long? = null,
    val endDateMillis: Long? = null,
    val selectedMonth: String? = null,
    val isLoading: Boolean = true
)

package app.ledgerpop.ui.state

import app.ledgerpop.data.local.SmsTransactionEntity

data class TransactionsUiState(
    val allTransactions: List<SmsTransactionEntity> = emptyList(),
    val filteredTransactions: List<SmsTransactionEntity> = emptyList(),
    val query: String = "",
    val selectedFilter: String = "All",
    val selectedCategory: String = "All",
    val startDateMillis: Long? = null,
    val endDateMillis: Long? = null,
    val isLoading: Boolean = true
)
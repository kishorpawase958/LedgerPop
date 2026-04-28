package app.ledgerpop.ui.state

import app.ledgerpop.data.local.SmsTransactionEntity

data class SpendingInsight(
    val message: String,
    val icon: String   // emoji for quick rendering
)

data class HomeUiState(
    val recentTransactions: List<SmsTransactionEntity> = emptyList(),
    val totalBalance: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val thisMonthExpense: Double = 0.0,
    val lastMonthExpense: Double = 0.0,
    val topCategory: String = "",
    val topCategoryAmount: Double = 0.0,
    val insights: List<SpendingInsight> = emptyList(),
    val isLoading: Boolean = true
)
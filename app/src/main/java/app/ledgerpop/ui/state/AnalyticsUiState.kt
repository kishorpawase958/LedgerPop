package app.ledgerpop.ui.state

import app.ledgerpop.data.local.CustomCategoryEntity
import app.ledgerpop.data.local.SmsTransactionEntity

data class TrendSummary(
    val label: String,
    val income: Double,
    val expense: Double
)

data class CategorySummary(
    val category: String,
    val amount: Double,
    val percentage: Float
)

enum class GroupingType { DAILY, WEEKLY, MONTHLY }
enum class AnalyticsViewType { SPENDS, INCOME }

data class AnalyticsUiState(
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val net: Double = 0.0,
    val transactionCount: Int = 0,
    val avgDebit: Double = 0.0,
    val trendSummaries: List<TrendSummary> = emptyList(),
    val categoryBreakdown: List<CategorySummary> = emptyList(),
    val customCategories: List<CustomCategoryEntity> = emptyList(),
    val isLoading: Boolean = true,

    // Filters
    val startDateMillis: Long? = null,
    val endDateMillis: Long? = null,
    val groupBy: GroupingType = GroupingType.MONTHLY,
    val viewType: AnalyticsViewType = AnalyticsViewType.SPENDS,
    val selectedCategory: String = "All",
    val selectedAccount: String = "All",

    // Available Filter Options
    val availableCategories: List<String> = listOf("All"),
    val availableAccounts: List<String> = listOf("All"),

    // For CSV Export
    val filteredTransactions: List<SmsTransactionEntity> = emptyList()
)
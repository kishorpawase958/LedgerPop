package app.ledgerpop.ui.state

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

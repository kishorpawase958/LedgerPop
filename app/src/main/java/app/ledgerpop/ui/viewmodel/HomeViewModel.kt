package app.ledgerpop.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class HomeInsight(val icon: String, val message: String)

data class CategoryAggregate(val category: String, val amount: Double)

data class HomeUiState(
    val recentTransactions: List<SmsTransactionEntity> = emptyList(),
    val topCategories: List<CategoryAggregate> = emptyList(),
    val topTransactions: List<SmsTransactionEntity> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val totalBalance: Double = 0.0,
    val thisMonthIncome: Double = 0.0,
    val thisMonthExpense: Double = 0.0,
    val thisMonthBalance: Double = 0.0,
    val lastMonthExpense: Double = 0.0,
    val insights: List<HomeInsight> = emptyList(),
    val monthlyBudget: Double = 0.0,
    val isLoading: Boolean = true
)

class HomeViewModel(
    private val repository: TransactionRepository,
    context: Context
) : ViewModel() {

    private val sharedPrefs = context.applicationContext.getSharedPreferences("ledgerpop_prefs", Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(HomeUiState(
        monthlyBudget = sharedPrefs.getFloat("monthly_budget", 0f).toDouble()
    ))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllTransactions().collect { all ->
                val billable = all.filter { it.isBillable }
                val income = billable.filter { it.type == "CREDIT" }.sumOf { it.amount }
                val expense = billable.filter { it.type == "DEBIT" }.sumOf { it.amount }

                val cal = Calendar.getInstance()
                val thisMonth = cal.get(Calendar.MONTH)
                val thisYear = cal.get(Calendar.YEAR)
                cal.add(Calendar.MONTH, -1)
                val lastMonth = cal.get(Calendar.MONTH)
                val lastYear = cal.get(Calendar.YEAR)

                fun txnMonth(txn: SmsTransactionEntity): Int {
                    val c = Calendar.getInstance()
                    c.timeInMillis = txn.transactionTime
                    return c.get(Calendar.MONTH)
                }

                fun txnYear(txn: SmsTransactionEntity): Int {
                    val c = Calendar.getInstance()
                    c.timeInMillis = txn.transactionTime
                    return c.get(Calendar.YEAR)
                }

                val thisMonthIncome = billable
                    .filter { it.type == "CREDIT" && txnMonth(it) == thisMonth && txnYear(it) == thisYear }
                    .sumOf { it.amount }

                val thisMonthExpense = billable
                    .filter { it.type == "DEBIT" && txnMonth(it) == thisMonth && txnYear(it) == thisYear }
                    .sumOf { it.amount }

                val lastMonthExpense = billable
                    .filter { it.type == "DEBIT" && txnMonth(it) == lastMonth && txnYear(it) == lastYear }
                    .sumOf { it.amount }

                val thisMonthDebits = billable.filter {
                    it.type == "DEBIT" && txnMonth(it) == thisMonth && txnYear(it) == thisYear
                }

                val topCategories = thisMonthDebits
                    .groupBy { it.category }
                    .map { (cat, txns) ->
                        CategoryAggregate(cat.ifBlank { "Other" }, txns.sumOf { it.amount })
                    }
                    .sortedByDescending { it.amount }
                    .take(3)

                val topTransactions = thisMonthDebits
                    .sortedByDescending { it.amount }
                    .take(3)

                val insights = buildInsights(income, expense, thisMonthExpense, lastMonthExpense, billable)

                _uiState.update {
                    it.copy(
                        recentTransactions = all.take(10),
                        topCategories = topCategories,
                        topTransactions = topTransactions,
                        totalIncome = income,
                        totalExpense = expense,
                        totalBalance = income - expense,
                        thisMonthIncome = thisMonthIncome,
                        thisMonthExpense = thisMonthExpense,
                        thisMonthBalance = thisMonthIncome - thisMonthExpense,
                        lastMonthExpense = lastMonthExpense,
                        insights = insights,
                        isLoading = false
                    )
                }
            }
        }
    }
    fun saveTransaction(txn: SmsTransactionEntity) {
        viewModelScope.launch { repository.update(txn) }
    }

    fun deleteTransaction(txn: SmsTransactionEntity) {
        viewModelScope.launch { repository.delete(txn) }
    }

    fun addTransaction(txn: SmsTransactionEntity) {
        viewModelScope.launch { repository.insert(txn) }
    }

    fun updateBudget(budget: Double) {
        sharedPrefs.edit().putFloat("monthly_budget", budget.toFloat()).apply()
        _uiState.update { it.copy(monthlyBudget = budget) }
    }

    private fun buildInsights(
        income: Double,
        expense: Double,
        thisMonth: Double,
        lastMonth: Double,
        transactions: List<SmsTransactionEntity>
    ): List<HomeInsight> {
        val list = mutableListOf<HomeInsight>()
        if (income > 0 && expense / income > 0.8)
            list.add(HomeInsight("⚠️", "You've spent over 80% of your income"))
        if (lastMonth > 0 && thisMonth > lastMonth * 1.2)
            list.add(HomeInsight("📈", "Spending is up 20%+ vs last month"))
        if (lastMonth > 0 && thisMonth < lastMonth * 0.8)
            list.add(HomeInsight("🎉", "Great job! Spending down vs last month"))
        val topCategory = transactions
            .filter { it.type == "DEBIT" }
            .groupBy { it.category }
            .maxByOrNull { it.value.sumOf { t -> t.amount } }
        if (topCategory != null && topCategory.key.isNotBlank())
            list.add(HomeInsight("🏷️", "Top spend category: ${topCategory.key}"))
        return list
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun factory(database: LedgerPopDatabase, context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(
                        TransactionRepository(
                            database.smsTransactionDao(),
                            database.customCategoryDao()
                        ),
                        context
                    ) as T
            }
    }
}

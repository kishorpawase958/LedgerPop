package app.ledgerpop.ui.viewmodel

import android.content.Context
import androidx.core.content.edit
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
import java.util.Locale

data class HomeInsight(val icon: String, val title: String, val message: String)

data class CategoryAggregate(val category: String, val amount: Double)

enum class IncomeBenchmark {
    CURRENT_MONTH, PREVIOUS_MONTH
}

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
    val thisMonthInvestment: Double = 0.0,
    val lastMonthExpense: Double = 0.0,
    val lastMonthIncome: Double = 0.0,
    val incomeBenchmark: IncomeBenchmark = IncomeBenchmark.PREVIOUS_MONTH,
    val insights: List<HomeInsight> = emptyList(),
    val monthlyBudget: Double = 0.0,
    val userName: String = "User",
    val isLoading: Boolean = true
)

class HomeViewModel(
    private val repository: TransactionRepository,
    context: Context
) : ViewModel() {

    private val sharedPrefs = context.applicationContext.getSharedPreferences("ledgerpop_prefs", Context.MODE_PRIVATE)

    private fun getBudgetKey(year: Int, month: Int) = "budget_${year}_$month"

    private fun getCurrentMonthBudget(year: Int, month: Int): Double {
        val key = getBudgetKey(year, month)

        if (sharedPrefs.contains(key)) {
            return sharedPrefs.getFloat(key, 0f).toDouble()
        }

        // Try to carry over from previous month
        val prevCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            add(Calendar.MONTH, -1)
        }
        val prevKey = getBudgetKey(prevCal.get(Calendar.YEAR), prevCal.get(Calendar.MONTH))

        val carryOverBudget = if (sharedPrefs.contains(prevKey)) {
            sharedPrefs.getFloat(prevKey, 0f)
        } else {
            sharedPrefs.getFloat("monthly_budget", 0f)
        }

        if (carryOverBudget > 0f) {
            sharedPrefs.edit { putFloat(key, carryOverBudget) }
        }
        return carryOverBudget.toDouble()
    }

    private val _uiState = MutableStateFlow(HomeUiState(
        monthlyBudget = run {
            val cal = Calendar.getInstance()
            getCurrentMonthBudget(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
        },
        userName = sharedPrefs.getString("user_name", "User") ?: "User",
        incomeBenchmark = try {
            IncomeBenchmark.valueOf(sharedPrefs.getString("income_benchmark", IncomeBenchmark.PREVIOUS_MONTH.name) ?: IncomeBenchmark.PREVIOUS_MONTH.name)
        } catch (_: Exception) {
            IncomeBenchmark.PREVIOUS_MONTH
        }
    ))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refreshData()
    }

    private fun refreshData() {
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

                val lastMonthIncome = billable
                    .filter { it.type == "CREDIT" && txnMonth(it) == lastMonth && txnYear(it) == lastYear }
                    .sumOf { it.amount }

                val thisMonthDebits = billable.filter {
                    it.type == "DEBIT" && txnMonth(it) == thisMonth && txnYear(it) == thisYear
                }

                val thisMonthInvestment = billable
                    .filter { it.type == "DEBIT" && it.category == "Investments" && txnMonth(it) == thisMonth && txnYear(it) == thisYear }
                    .sumOf { it.amount }

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

                val currentBenchmark = _uiState.value.incomeBenchmark
                val baselineIncome = if (currentBenchmark == IncomeBenchmark.PREVIOUS_MONTH && lastMonthIncome > 0) {
                    lastMonthIncome
                } else {
                    thisMonthIncome
                }

                val insights = buildInsights(
                    income = baselineIncome,
                    thisMonthExpense = thisMonthExpense,
                    lastMonthExpense = lastMonthExpense,
                    thisMonthInvestment = thisMonthInvestment
                )

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
                        thisMonthInvestment = thisMonthInvestment,
                        lastMonthExpense = lastMonthExpense,
                        lastMonthIncome = lastMonthIncome,
                        insights = insights,
                        monthlyBudget = getCurrentMonthBudget(thisYear, thisMonth),
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
        val cal = Calendar.getInstance()
        val key = getBudgetKey(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
        sharedPrefs.edit {
            putFloat(key, budget.toFloat())
            putFloat("monthly_budget", budget.toFloat())
        }
        _uiState.update { it.copy(monthlyBudget = budget) }
    }

    fun updateIncomeBenchmark(benchmark: IncomeBenchmark) {
        sharedPrefs.edit {
            putString("income_benchmark", benchmark.name)
        }
        _uiState.update { it.copy(incomeBenchmark = benchmark) }
        refreshData()
    }

    private fun buildInsights(
        income: Double,
        thisMonthExpense: Double,
        lastMonthExpense: Double,
        thisMonthInvestment: Double
    ): List<HomeInsight> {
        val list = mutableListOf<HomeInsight>()

        if (income > 0 && thisMonthInvestment > 0) {
            val percentage = ((thisMonthInvestment / income) * 100).toInt()
            list.add(
                HomeInsight(
                    "👏",
                    "MONEY INVESTED",
                    "You have invested $percentage% of your income"
                )
            )
        }

        if (income > 0) {
            val spentPct = ((thisMonthExpense / income) * 100).toInt()
            if (spentPct > 75) {
                list.add(
                    HomeInsight(
                        "⚠️",
                        "SPENDING LIMIT",
                        "You have spent over $spentPct% of your income"
                    )
                )
            }
        }

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
                            database.customCategoryDao(),
                            database.accountDao()
                        ),
                        context
                    ) as T
            }
    }
}

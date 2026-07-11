package app.ledgerpop.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.ledgerpop.data.category.CategoryEngine
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.data.repository.TransactionRepository
import app.ledgerpop.ui.state.AnalyticsUiState
import app.ledgerpop.ui.state.AnalyticsViewType
import app.ledgerpop.ui.state.CategorySummary
import app.ledgerpop.ui.state.GroupingType
import app.ledgerpop.ui.state.TrendSummary
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

sealed class DrillDownType {
    object Income : DrillDownType()
    object Expense : DrillDownType()
    data class Category(val categoryName: String) : DrillDownType()
    data class Trend(val label: String) : DrillDownType()
}

private data class AnalyticsFilters(
    val start: Long?,
    val end: Long?,
    val groupBy: GroupingType,
    val viewType: AnalyticsViewType,
    val categories: Set<String>,
    val accounts: Set<String>,
    val month: String?,
    val isAggregated: Boolean
)

class AnalyticsViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _startDateMillis = MutableStateFlow<Long?>(null)
    private val _endDateMillis = MutableStateFlow<Long?>(null)
    private val _groupBy = MutableStateFlow(GroupingType.MONTHLY)
    private val _viewType = MutableStateFlow(AnalyticsViewType.SPENDS)
    private val _selectedCategories = MutableStateFlow(setOf("All"))
    private val _selectedAccounts = MutableStateFlow(setOf("All"))
    private val _selectedMonth = MutableStateFlow<String?>(null)
    private val _isAggregated = MutableStateFlow(false)

    private val formats = mapOf(
        GroupingType.DAILY to SimpleDateFormat("dd MMM", Locale.getDefault()),
        GroupingType.WEEKLY to SimpleDateFormat("'W'W, MMM yy", Locale.getDefault()),
        GroupingType.MONTHLY to SimpleDateFormat("MMM yy", Locale.getDefault())
    )

    private val filters = combine(
        combine(_startDateMillis, _endDateMillis, _groupBy) { s, e, g -> Triple(s, e, g) },
        _viewType,
        _isAggregated,
        combine(_selectedCategories, _selectedAccounts, _selectedMonth) { c, a, m -> Triple(c, a, m) }
    ) { time, viewType, isAggregated, category ->
        AnalyticsFilters(
            time.first, time.second, time.third,
            viewType,
            category.first, category.second, category.third,
            isAggregated
        )
    }

    val uiState: StateFlow<AnalyticsUiState> = combine(
        repository.getAllTransactions(),
        repository.getAllCustomCategories(),
        filters
    ) { allTxns, customCategories, f ->
        val billableTxns = allTxns.filter { it.isBillable }
        
        val cats = listOf("All") + billableTxns.map { CategoryEngine.normalize(it.category) }.distinct().sorted()
        val accs = listOf("All") + billableTxns.map { it.accountHint.ifBlank { "Unknown" } }.distinct().sorted()

        var baseFiltered = billableTxns
        if (f.start != null) baseFiltered = baseFiltered.filter { it.transactionTime >= f.start }
        if (f.end != null) baseFiltered = baseFiltered.filter { it.transactionTime <= (f.end + 86400000L - 1) }
        if (!f.categories.contains("All")) baseFiltered = baseFiltered.filter { f.categories.contains(CategoryEngine.normalize(it.category)) }
        if (!f.accounts.contains("All")) baseFiltered = baseFiltered.filter { f.accounts.contains(it.accountHint.ifBlank { "Unknown" }) }

        val trendFormat = formats[f.groupBy]!!
        // Trend chart shows all historical data for the selected category/account, ignoring date range filters
        val trendBase = billableTxns.filter { txn ->
            val matchesCategory = f.categories.contains("All") || f.categories.contains(CategoryEngine.normalize(txn.category))
            val matchesAccount = f.accounts.contains("All") || f.accounts.contains(txn.accountHint.ifBlank { "Unknown" })
            matchesCategory && matchesAccount
        }
        val trendSummaries = trendBase
            .groupBy {
                val cal = Calendar.getInstance().apply { timeInMillis = it.transactionTime }
                trendFormat.format(cal.time)
            }
            .map { (label, txns) -> label to txns }
            .sortedBy { (_, txns) -> txns.minOf { it.transactionTime } }
            .map { (label, txns) ->
                TrendSummary(
                    label = label,
                    income = txns.filter { it.type == "CREDIT" && it.linkedTransactionId == null }.sumOf { it.amount },
                    expense = txns.filter { it.type == "DEBIT" }.sumOf { it.amount }
                )
            }

        val filteredTxns = if (f.month == null) {
            baseFiltered
        } else {
            baseFiltered.filter {
                val cal = Calendar.getInstance().apply { timeInMillis = it.transactionTime }
                trendFormat.format(cal.time) == f.month
            }
        }

        val income = filteredTxns.filter { it.type == "CREDIT" && it.linkedTransactionId == null }.sumOf { it.amount }
        val expense = filteredTxns.filter { it.type == "DEBIT" }.sumOf { it.amount }

        val effectiveViewType = when {
            expense > 0 && income == 0.0 -> AnalyticsViewType.SPENDS
            income > 0 && expense == 0.0 -> AnalyticsViewType.INCOME
            else -> f.viewType
        }

        val debits = filteredTxns.filter { it.type == "DEBIT" }
        val credits = filteredTxns.filter { it.type == "CREDIT" && it.linkedTransactionId == null }
        val avgDebit = if (debits.isNotEmpty()) expense / debits.size else 0.0
        val avgCredit = if (credits.isNotEmpty()) income / credits.size else 0.0

        val categoryBreakdown = if (effectiveViewType == AnalyticsViewType.SPENDS) {
            debits
                .groupBy { CategoryEngine.normalize(it.category) }
                .map { (cat, txns) ->
                    val total = txns.sumOf { it.amount }
                    CategorySummary(
                        category = cat,
                        amount = total,
                        percentage = if (expense > 0) (total / expense * 100).toFloat() else 0f
                    )
                }
                .sortedByDescending { it.amount }
        } else {
            credits
                .groupBy { CategoryEngine.normalize(it.category) }
                .map { (cat, txns) ->
                    val total = txns.sumOf { it.amount }
                    CategorySummary(
                        category = cat,
                        amount = total,
                        percentage = if (income > 0) (total / income * 100).toFloat() else 0f
                    )
                }
                .sortedByDescending { it.amount }
        }

        AnalyticsUiState(
            totalIncome = income,
            totalExpense = expense,
            net = income - expense,
            transactionCount = filteredTxns.size,
            avgDebit = avgDebit,
            avgCredit = avgCredit,
            trendSummaries = trendSummaries,
            categoryBreakdown = categoryBreakdown,
            customCategories = customCategories,
            isLoading = false,
            startDateMillis = f.start,
            endDateMillis = f.end,
            groupBy = f.groupBy,
            viewType = effectiveViewType,
            selectedCategories = f.categories,
            selectedAccounts = f.accounts,
            selectedMonth = f.month,
            isAggregated = f.isAggregated,
            availableCategories = cats,
            availableAccounts = accs,
            filteredTransactions = filteredTxns.sortedByDescending { it.transactionTime },
            allTransactions = allTxns
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalyticsUiState(isLoading = true)
    )

    private val _currentDrillDown = MutableStateFlow<DrillDownType?>(null)

    val drillDownTitle: StateFlow<String> = _currentDrillDown.map { type ->
        when (type) {
            is DrillDownType.Income -> "Income Details"
            is DrillDownType.Expense -> "Expense Details"
            is DrillDownType.Category -> "${type.categoryName} Details"
            is DrillDownType.Trend -> "Details for ${type.label}"
            null -> ""
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val drillDownTransactions: StateFlow<List<SmsTransactionEntity>?> = combine(
        repository.getAllTransactions(),
        _currentDrillDown,
        filters
    ) { allTxns, drillDown, f ->
        if (drillDown == null) return@combine null

        val trendFormat = formats[f.groupBy]!!

        var baseFiltered = allTxns
        if (f.start != null) baseFiltered = baseFiltered.filter { it.transactionTime >= f.start }
        if (f.end != null) baseFiltered = baseFiltered.filter { it.transactionTime <= (f.end + 86400000L - 1) }
        if (!f.accounts.contains("All")) baseFiltered = baseFiltered.filter { f.accounts.contains(it.accountHint.ifBlank { "Unknown" }) }

        val monthFiltered = if (f.month == null) {
            baseFiltered
        } else {
            baseFiltered.filter {
                val cal = Calendar.getInstance().apply { timeInMillis = it.transactionTime }
                trendFormat.format(cal.time) == f.month
            }
        }

        when (drillDown) {
            is DrillDownType.Income -> {
                monthFiltered.filter { it.type == "CREDIT" }
            }
            is DrillDownType.Expense -> {
                monthFiltered.filter { it.type == "DEBIT" }
            }
            is DrillDownType.Category -> {
                val income = monthFiltered.filter { it.type == "CREDIT" && it.linkedTransactionId == null }.sumOf { it.amount }
                val expense = monthFiltered.filter { it.type == "DEBIT" }.sumOf { it.amount }
                val effectiveViewType = when {
                    expense > 0 && income == 0.0 -> AnalyticsViewType.SPENDS
                    income > 0 && expense == 0.0 -> AnalyticsViewType.INCOME
                    else -> f.viewType
                }

                monthFiltered.filter {
                    val typeMatches = if (effectiveViewType == AnalyticsViewType.SPENDS) it.type == "DEBIT" else it.type == "CREDIT"
                    typeMatches && CategoryEngine.normalize(it.category) == drillDown.categoryName
                }
            }
            is DrillDownType.Trend -> {
                allTxns.filter {
                    val matchesCategory = f.categories.contains("All") || f.categories.contains(CategoryEngine.normalize(it.category))
                    val matchesAccount = f.accounts.contains("All") || f.accounts.contains(it.accountHint.ifBlank { "Unknown" })
                    if (matchesCategory && matchesAccount) {
                        val cal = Calendar.getInstance().apply { timeInMillis = it.transactionTime }
                        trendFormat.format(cal.time) == drillDown.label
                    } else false
                }
            }
        }.sortedByDescending { it.transactionTime }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setDateRange(start: Long?, end: Long?) {
        _startDateMillis.value = start
        _endDateMillis.value = end
    }

    fun setViewType(type: AnalyticsViewType) { _viewType.value = type }
    fun setAccountFilter(account: String) {
        _selectedAccounts.update { current ->
            if (account == "All") {
                setOf("All")
            } else {
                val next = current.toMutableSet().apply {
                    remove("All")
                    if (contains(account)) remove(account) else add(account)
                }
                if (next.isEmpty()) setOf("All") else next
            }
        }
    }

    fun setCategoryFilter(category: String) {
        _selectedCategories.update { current ->
            if (category == "All") {
                setOf("All")
            } else {
                val next = current.toMutableSet().apply {
                    remove("All")
                    if (contains(category)) remove(category) else add(category)
                }
                if (next.isEmpty()) setOf("All") else next
            }
        }
    }

    fun onMonthToggle(month: String) {
        _selectedMonth.value = if (_selectedMonth.value == month) null else month
    }

    fun toggleAggregation() {
        _isAggregated.value = !_isAggregated.value
    }

    fun clearFilters() {
        _startDateMillis.value = null
        _endDateMillis.value = null
        _selectedCategories.value = setOf("All")
        _selectedAccounts.value = setOf("All")
        _selectedMonth.value = null
        _groupBy.value = GroupingType.MONTHLY
        _viewType.value = AnalyticsViewType.SPENDS
    }

    fun openDrillDown(type: DrillDownType) {
        _currentDrillDown.value = type
    }

    fun closeDrillDown() {
        _currentDrillDown.value = null
    }

    fun saveTransaction(txn: SmsTransactionEntity) {
        viewModelScope.launch {
            repository.update(txn)
        }
    }

    fun deleteTransaction(txn: SmsTransactionEntity) {
        viewModelScope.launch {
            repository.delete(txn)
        }
    }

    // ── Export Logic ──────────────────────────────────────────────────────────

    fun exportToCsv(context: Context) {
        viewModelScope.launch {
            try {
                val state = uiState.value

                // Filter allTransactions by the same filters but ignore isBillable
                var exportList = state.allTransactions

                state.startDateMillis?.let { start ->
                    exportList = exportList.filter { it.transactionTime >= start }
                }
                state.endDateMillis?.let { end ->
                    exportList = exportList.filter { it.transactionTime <= (end + 86400000L - 1) }
                }
                if (!state.selectedAccounts.contains("All")) {
                    exportList = exportList.filter { state.selectedAccounts.contains(it.accountHint.ifBlank { "Unknown" }) }
                }
                if (!state.selectedCategories.contains("All")) {
                    exportList = exportList.filter { state.selectedCategories.contains(CategoryEngine.normalize(it.category)) }
                }
                if (state.selectedMonth != null) {
                    val trendFormat = formats[state.groupBy]!!
                    exportList = exportList.filter {
                        val cal = Calendar.getInstance().apply { timeInMillis = it.transactionTime }
                        trendFormat.format(cal.time) == state.selectedMonth
                    }
                }

                val txns = exportList.sortedByDescending { it.transactionTime }
                if (txns.isEmpty()) return@launch

                val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

                val csvHeader = "DATE,TIME,MERCHANT,AMOUNT,DR/CR,ACCOUNT,EXPENSE,INCOME,CATEGORY,NOTE\n"

                val csvData = StringBuilder(csvHeader)
                txns.forEach { txn ->
                    val txDate = Date(txn.transactionTime)
                    val date = dateFormatter.format(txDate)
                    val time = timeFormatter.format(txDate)

                    val merchant = "\"${txn.merchant.replace("\"", "\"\"")}\""
                    val amount = txn.amount
                    val drCr = if (txn.type == "DEBIT") "DR" else "CR"
                    val account = "\"${txn.accountHint.ifBlank { "Unknown" }.replace("\"", "\"\"")}\""

                    val isExpenseReported = if (txn.type == "DEBIT") {
                        if (txn.isBillable) "YES" else "NO"
                    } else "-"

                    val isIncomeReported = if (txn.type == "CREDIT") {
                        if (txn.isBillable) "YES" else "NO"
                    } else "-"

                    val category = "\"${txn.category.ifBlank { "Other" }.replace("\"", "\"\"")}\""
                    val note = "\"${txn.note.replace("\"", "\"\"")}\""

                    csvData.append("$date,$time,$merchant,$amount,$drCr,$account,$isExpenseReported,$isIncomeReported,$category,$note\n")
                }

                val fileDateFormatter = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                
                val startMillis = state.startDateMillis ?: txns.minOf { it.transactionTime }
                val endMillis = state.endDateMillis ?: txns.maxOf { it.transactionTime }

                val fromDate = fileDateFormatter.format(Date(startMillis))
                val toDate = fileDateFormatter.format(Date(endMillis))

                val fileName = "TxnReport_${fromDate}_to_${toDate}.csv"

                val file = File(context.cacheDir, fileName)
                file.writeText(csvData.toString())

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(Intent.createChooser(shareIntent, "Export LedgerPop Data"))

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        fun factory(database: LedgerPopDatabase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AnalyticsViewModel(
                        TransactionRepository(
                            database.smsTransactionDao(),
                            database.customCategoryDao(),
                            database.accountDao()
                        )
                    ) as T
                }
            }
    }
}

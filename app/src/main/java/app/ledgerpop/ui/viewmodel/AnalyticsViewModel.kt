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
    val category: String,
    val account: String,
    val month: String?
)

class AnalyticsViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _startDateMillis = MutableStateFlow<Long?>(null)
    private val _endDateMillis = MutableStateFlow<Long?>(null)
    private val _groupBy = MutableStateFlow(GroupingType.MONTHLY)
    private val _viewType = MutableStateFlow(AnalyticsViewType.SPENDS)
    private val _selectedCategory = MutableStateFlow("All")
    private val _selectedAccount = MutableStateFlow("All")
    private val _selectedMonth = MutableStateFlow<String?>(null)

    private val formats = mapOf(
        GroupingType.DAILY to SimpleDateFormat("dd MMM", Locale.getDefault()),
        GroupingType.WEEKLY to SimpleDateFormat("'W'W, MMM yy", Locale.getDefault()),
        GroupingType.MONTHLY to SimpleDateFormat("MMM yy", Locale.getDefault())
    )

    private val filters = combine(
        _startDateMillis,
        _endDateMillis,
        _groupBy,
        _viewType,
        combine(_selectedCategory, _selectedAccount, _selectedMonth) { c, a, m -> Triple(c, a, m) }
    ) { start, end, groupBy, viewType, triple ->
        AnalyticsFilters(start, end, groupBy, viewType, triple.first, triple.second, triple.third)
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
        if (f.category != "All") baseFiltered = baseFiltered.filter { CategoryEngine.normalize(it.category) == f.category }
        if (f.account != "All") baseFiltered = baseFiltered.filter { it.accountHint.ifBlank { "Unknown" } == f.account }

        val trendFormat = formats[f.groupBy]!!
        // Trend chart shows all historical data for the selected category/account, ignoring date range filters
        val trendBase = billableTxns.filter { txn ->
            val matchesCategory = f.category == "All" || CategoryEngine.normalize(txn.category) == f.category
            val matchesAccount = f.account == "All" || txn.accountHint.ifBlank { "Unknown" } == f.account
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
        val debits = filteredTxns.filter { it.type == "DEBIT" }
        val credits = filteredTxns.filter { it.type == "CREDIT" && it.linkedTransactionId == null }
        val avgDebit = if (debits.isNotEmpty()) expense / debits.size else 0.0
        val avgCredit = if (credits.isNotEmpty()) income / credits.size else 0.0

        val categoryBreakdown = if (f.viewType == AnalyticsViewType.SPENDS) {
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
            viewType = f.viewType,
            selectedCategory = f.category,
            selectedAccount = f.account,
            selectedMonth = f.month,
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
        if (f.account != "All") baseFiltered = baseFiltered.filter { it.accountHint.ifBlank { "Unknown" } == f.account }

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
                monthFiltered.filter {
                    val typeMatches = if (f.viewType == AnalyticsViewType.SPENDS) it.type == "DEBIT" else it.type == "CREDIT"
                    typeMatches && CategoryEngine.normalize(it.category) == drillDown.categoryName
                }
            }
            is DrillDownType.Trend -> {
                allTxns.filter {
                    val matchesCategory = f.category == "All" || CategoryEngine.normalize(it.category) == f.category
                    val matchesAccount = f.account == "All" || it.accountHint.ifBlank { "Unknown" } == f.account
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
        _selectedAccount.value = if (_selectedAccount.value == account) "All" else account
    }

    fun setCategoryFilter(category: String) {
        _selectedCategory.value = if (_selectedCategory.value == category) "All" else category
    }

    fun onMonthToggle(month: String) {
        _selectedMonth.value = if (_selectedMonth.value == month) null else month
    }

    fun clearFilters() {
        _startDateMillis.value = null
        _endDateMillis.value = null
        _selectedCategory.value = "All"
        _selectedAccount.value = "All"
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
                if (state.selectedAccount != "All") {
                    exportList = exportList.filter { it.accountHint.ifBlank { "Unknown" } == state.selectedAccount }
                }
                if (state.selectedCategory != "All") {
                    exportList = exportList.filter { CategoryEngine.normalize(it.category) == state.selectedCategory }
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
                            database.customCategoryDao()
                        )
                    ) as T
                }
            }
    }
}

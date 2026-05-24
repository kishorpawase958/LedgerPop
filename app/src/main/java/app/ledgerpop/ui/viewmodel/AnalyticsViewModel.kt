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
    val account: String
)

class AnalyticsViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _startDateMillis = MutableStateFlow<Long?>(null)
    private val _endDateMillis = MutableStateFlow<Long?>(null)
    private val _groupBy = MutableStateFlow(GroupingType.DAILY)
    private val _viewType = MutableStateFlow(AnalyticsViewType.SPENDS)
    private val _selectedCategory = MutableStateFlow("All")
    private val _selectedAccount = MutableStateFlow("All")

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
        combine(_selectedCategory, _selectedAccount) { c, a -> c to a }
    ) { start, end, groupBy, viewType, catAcc ->
        AnalyticsFilters(start, end, groupBy, viewType, catAcc.first, catAcc.second)
    }

    val uiState: StateFlow<AnalyticsUiState> = combine(
        repository.getAllTransactions(),
        repository.getAllCustomCategories(),
        filters
    ) { allTxns, customCategories, f ->
        val billableTxns = allTxns.filter { it.isBillable }
        
        val cats = listOf("All") + billableTxns.map { CategoryEngine.normalize(it.category) }.distinct().sorted()
        val accs = listOf("All") + billableTxns.map { it.accountHint.ifBlank { "Unknown" } }.distinct().sorted()

        var filteredTxns = billableTxns
        if (f.start != null) filteredTxns = filteredTxns.filter { it.transactionTime >= f.start }
        if (f.end != null) filteredTxns = filteredTxns.filter { it.transactionTime <= (f.end + 86400000L - 1) }
        if (f.category != "All") filteredTxns = filteredTxns.filter { CategoryEngine.normalize(it.category) == f.category }
        if (f.account != "All") filteredTxns = filteredTxns.filter { it.accountHint.ifBlank { "Unknown" } == f.account }

        val income = filteredTxns.filter { it.type == "CREDIT" }.sumOf { it.amount }
        val expense = filteredTxns.filter { it.type == "DEBIT" }.sumOf { it.amount }
        val debits = filteredTxns.filter { it.type == "DEBIT" }
        val credits = filteredTxns.filter { it.type == "CREDIT" }
        val avg = if (debits.isNotEmpty()) debits.sumOf { it.amount } / debits.size else 0.0

        val trendFormat = formats[f.groupBy]!!
        val trendSummaries = filteredTxns
            .groupBy {
                val cal = Calendar.getInstance().apply { timeInMillis = it.transactionTime }
                trendFormat.format(cal.time)
            }
            .map { (label, txns) -> label to txns }
            .sortedBy { (_, txns) -> txns.minOf { it.transactionTime } }
            .takeLast(if (f.groupBy == GroupingType.DAILY) 14 else 12)
            .map { (label, txns) ->
                TrendSummary(
                    label = label,
                    income = txns.filter { it.type == "CREDIT" }.sumOf { it.amount },
                    expense = txns.filter { it.type == "DEBIT" }.sumOf { it.amount }
                )
            }

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
            avgDebit = avg,
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
            availableCategories = cats,
            availableAccounts = accs,
            filteredTransactions = filteredTxns.sortedByDescending { it.transactionTime }
        )
    }.stateIn(
     scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalyticsUiState(isLoading = true)
    )

    private val _drillDownTransactions = MutableStateFlow<List<SmsTransactionEntity>?>(null)
    val drillDownTransactions: StateFlow<List<SmsTransactionEntity>?> = _drillDownTransactions.asStateFlow()

    private val _drillDownTitle = MutableStateFlow("")
    val drillDownTitle: StateFlow<String> = _drillDownTitle.asStateFlow()

    fun setDateRange(start: Long?, end: Long?) {
        _startDateMillis.value = start
        _endDateMillis.value = end
    }

    fun setViewType(type: AnalyticsViewType) { _viewType.value = type }
    fun setAccountFilter(account: String) { _selectedAccount.value = account }

    fun clearFilters() {
        _startDateMillis.value = null
        _endDateMillis.value = null
        _selectedCategory.value = "All"
        _selectedAccount.value = "All"
        _groupBy.value = GroupingType.DAILY
        _viewType.value = AnalyticsViewType.SPENDS
    }

    fun openDrillDown(type: DrillDownType) {
        val currentTxns = uiState.value.filteredTransactions

        when (type) {
            is DrillDownType.Income -> {
                _drillDownTitle.value = "Income Details"
                _drillDownTransactions.value = currentTxns.filter { it.type == "CREDIT" }
            }
            is DrillDownType.Expense -> {
                _drillDownTitle.value = "Expense Details"
                _drillDownTransactions.value = currentTxns.filter { it.type == "DEBIT" }
            }
            is DrillDownType.Category -> {
                _drillDownTitle.value = "${type.categoryName} Details"
                _drillDownTransactions.value = currentTxns.filter {
                    val typeMatches = if (uiState.value.viewType == AnalyticsViewType.SPENDS) it.type == "DEBIT" else it.type == "CREDIT"
                    typeMatches && CategoryEngine.normalize(it.category) == type.categoryName
                }
            }
            is DrillDownType.Trend -> {
                _drillDownTitle.value = "Details for ${type.label}"
                val trendFormat = formats[uiState.value.groupBy]!!
                _drillDownTransactions.value = currentTxns.filter {
                    val cal = Calendar.getInstance().apply { timeInMillis = it.transactionTime }
                    trendFormat.format(cal.time) == type.label
                }
            }
        }
    }

    fun closeDrillDown() {
        _drillDownTransactions.value = null
        _drillDownTitle.value = ""
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
                val txns = state.filteredTransactions
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

                    val isExpenseReported = if (txn.isBillable && txn.type == "DEBIT") "Yes" else "No"
                    val isIncomeReported = if (txn.isBillable && txn.type == "CREDIT") "Yes" else "No"

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

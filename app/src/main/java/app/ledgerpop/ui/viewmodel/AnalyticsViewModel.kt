package app.ledgerpop.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.data.repository.TransactionRepository
import app.ledgerpop.ui.state.AnalyticsUiState
import app.ledgerpop.ui.state.CategorySummary
import app.ledgerpop.ui.state.GroupingType
import app.ledgerpop.ui.state.TrendSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class DrillDownType {
    object Income : DrillDownType()
    object Expense : DrillDownType()
    data class Category(val categoryName: String) : DrillDownType()
    data class Trend(val label: String) : DrillDownType()
}

class AnalyticsViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    // FIX: Set default grouping type to DAILY
    private val _uiState = MutableStateFlow(AnalyticsUiState(groupBy = GroupingType.DAILY))
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    private val _drillDownTransactions = MutableStateFlow<List<SmsTransactionEntity>?>(null)
    val drillDownTransactions: StateFlow<List<SmsTransactionEntity>?> = _drillDownTransactions.asStateFlow()

    private val _drillDownTitle = MutableStateFlow("")
    val drillDownTitle: StateFlow<String> = _drillDownTitle.asStateFlow()

    private var allTransactionsList: List<SmsTransactionEntity> = emptyList()

    init {
        viewModelScope.launch {
            repository.getAllTransactions().collect { allTxns ->
                allTransactionsList = allTxns.filter { it.isBillable }

                val cats = listOf("All") + allTransactionsList.map { it.category.ifBlank { "Other" } }.distinct().sorted()
                val accs = listOf("All") + allTransactionsList.map { it.accountHint.ifBlank { "Unknown" } }.distinct().sorted()

                _uiState.update { state ->
                    state.copy(
                        availableCategories = cats,
                        availableAccounts = accs
                    )
                }

                processAnalytics()
            }
        }
    }

    // ── Filter Updates ────────────────────────────────────────────────────────

    fun setDateRange(start: Long?, end: Long?) {
        _uiState.update { it.copy(startDateMillis = start, endDateMillis = end) }
        processAnalytics()
    }

    fun setGroupingType(type: GroupingType) {
        _uiState.update { it.copy(groupBy = type) }
        processAnalytics()
    }

    fun setCategoryFilter(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
        processAnalytics()
    }

    fun setAccountFilter(account: String) {
        _uiState.update { it.copy(selectedAccount = account) }
        processAnalytics()
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(
                startDateMillis = null,
                endDateMillis = null,
                selectedCategory = "All",
                selectedAccount = "All",
                groupBy = GroupingType.DAILY // Reset to Daily
            )
        }
        processAnalytics()
    }

    // ── Drill Down Logic ──────────────────────────────────────────────────────

    fun openDrillDown(type: DrillDownType) {
        val currentTxns = _uiState.value.filteredTransactions

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
                _drillDownTitle.value = "${type.categoryName} Expenses"
                _drillDownTransactions.value = currentTxns.filter {
                    it.type == "DEBIT" && it.category.ifBlank { "Other" } == type.categoryName
                }
            }
            is DrillDownType.Trend -> {
                _drillDownTitle.value = "Details for ${type.label}"

                val trendFormat = when (_uiState.value.groupBy) {
                    GroupingType.DAILY -> SimpleDateFormat("dd MMM", Locale.getDefault())
                    GroupingType.WEEKLY -> SimpleDateFormat("'W'W, MMM yy", Locale.getDefault())
                    GroupingType.MONTHLY -> SimpleDateFormat("MMM yy", Locale.getDefault())
                }

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

    // ── Core Processing Logic ─────────────────────────────────────────────────

    private fun processAnalytics() {
        val state = _uiState.value
        var filteredTxns = allTransactionsList

        if (state.startDateMillis != null) {
            filteredTxns = filteredTxns.filter { it.transactionTime >= state.startDateMillis }
        }
        if (state.endDateMillis != null) {
            val endOfDay = state.endDateMillis + 86400000L - 1
            filteredTxns = filteredTxns.filter { it.transactionTime <= endOfDay }
        }
        if (state.selectedCategory != "All") {
            filteredTxns = filteredTxns.filter { it.category.ifBlank { "Other" } == state.selectedCategory }
        }
        if (state.selectedAccount != "All") {
            filteredTxns = filteredTxns.filter { it.accountHint.ifBlank { "Unknown" } == state.selectedAccount }
        }

        val income = filteredTxns.filter { it.type == "CREDIT" }.sumOf { it.amount }
        val expense = filteredTxns.filter { it.type == "DEBIT" }.sumOf { it.amount }
        val debits = filteredTxns.filter { it.type == "DEBIT" }
        val avg = if (debits.isNotEmpty()) debits.sumOf { it.amount } / debits.size else 0.0

        val trendFormat = when (state.groupBy) {
            GroupingType.DAILY -> SimpleDateFormat("dd MMM", Locale.getDefault())
            GroupingType.WEEKLY -> SimpleDateFormat("'W'W, MMM yy", Locale.getDefault())
            GroupingType.MONTHLY -> SimpleDateFormat("MMM yy", Locale.getDefault())
        }

        val trendSummaries = filteredTxns
            .groupBy {
                val cal = Calendar.getInstance().apply { timeInMillis = it.transactionTime }
                trendFormat.format(cal.time)
            }
            .entries
            .sortedBy { entry ->
                filteredTxns
                    .filter {
                        val cal = Calendar.getInstance().apply { timeInMillis = it.transactionTime }
                        trendFormat.format(cal.time) == entry.key
                    }
                    .minOf { it.transactionTime }
            }
            .takeLast(if (state.groupBy == GroupingType.DAILY) 14 else 12)
            .map { (label, txns) ->
                TrendSummary(
                    label = label,
                    income = txns.filter { it.type == "CREDIT" }.sumOf { it.amount },
                    expense = txns.filter { it.type == "DEBIT" }.sumOf { it.amount }
                )
            }

        val categoryBreakdown = debits
            .groupBy { it.category.ifBlank { "Other" } }
            .map { (cat, txns) ->
                val total = txns.sumOf { it.amount }
                CategorySummary(
                    category = cat,
                    amount = total,
                    percentage = if (expense > 0) (total / expense * 100).toFloat() else 0f
                )
            }
            .sortedByDescending { it.amount }

        _uiState.update {
            it.copy(
                totalIncome = income,
                totalExpense = expense,
                net = income - expense,
                transactionCount = filteredTxns.size,
                avgDebit = avg,
                trendSummaries = trendSummaries,
                categoryBreakdown = categoryBreakdown,
                filteredTransactions = filteredTxns.sortedByDescending { t -> t.transactionTime },
                isLoading = false
            )
        }
    }

    // ── Export Logic ──────────────────────────────────────────────────────────

    fun exportToCsv(context: Context) {
        viewModelScope.launch {
            try {
                val txns = _uiState.value.filteredTransactions
                if (txns.isEmpty()) return@launch

                val csvHeader = "DATE,TIME,MERCHANT,AMOUNT,DR/CR,ACCOUNT,EXPENSE,INCOME,CATEGORY,NOTE\n"

                val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

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
                    val note = if (txn.sender == "Manual") "\"Manual Entry\"" else "\"SMS Import\""

                    csvData.append("$date,$time,$merchant,$amount,$drCr,$account,$isExpenseReported,$isIncomeReported,$category,$note\n")
                }

                val fileDateFormatter = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                val state = _uiState.value

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
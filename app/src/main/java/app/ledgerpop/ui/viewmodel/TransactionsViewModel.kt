package app.ledgerpop.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.data.repository.TransactionRepository
import app.ledgerpop.ui.state.TrendSummary
import app.ledgerpop.ui.state.TransactionsUiState
import app.ledgerpop.ui.state.TransactionSortOrder
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.ledgerpop.data.category.CategoryEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TransactionsViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val searchQuery = _query.asStateFlow()

    private val _selectedFilter = MutableStateFlow("All")
    private val _selectedCategory = MutableStateFlow("All")
    private val _selectedAccount = MutableStateFlow("All")
    private val _selectedSortOrder = MutableStateFlow(TransactionSortOrder.DATE_DESC)
    private val _selectedMonth = MutableStateFlow<String?>(null)
    private val _dateRange = MutableStateFlow<Pair<Long?, Long?>>(null to null)
    private val monthFormat = SimpleDateFormat("MMM yy", Locale.getDefault())

    private data class InternalFilters(
        val query: String,
        val filter: String,
        val category: String,
        val account: String,
        val sortOrder: TransactionSortOrder,
        val month: String?,
        val dateRange: Pair<Long?, Long?>
    )

    private data class SecondaryFilters(
        val account: String,
        val sortOrder: TransactionSortOrder,
        val month: String?,
        val dateRange: Pair<Long?, Long?>
    )

    private val filters = combine(
        combine(
            _query.debounce(100L).onStart { emit(_query.value) }.distinctUntilChanged(),
            _selectedFilter,
            _selectedCategory
        ) { q, f, c -> Triple(q, f, c) },
        combine(_selectedAccount, _selectedSortOrder, _selectedMonth, _dateRange) { a, s, m, r -> 
            SecondaryFilters(a, s, m, r)
        }
    ) { t1, t2 ->
        InternalFilters(
            query = t1.first,
            filter = t1.second,
            category = t1.third,
            account = t2.account,
            sortOrder = t2.sortOrder,
            month = t2.month,
            dateRange = t2.dateRange
        )
    }

    val uiState: StateFlow<TransactionsUiState> = filters.flatMapLatest { f ->
        combine(
            repository.getAllTransactions(),
            repository.getAllCustomCategories(),
            repository.getAllAccounts()
        ) { transactions, customCategories, accounts ->
            val (start, end) = f.dateRange
            val query = f.query.trim()
            val isQueryBlank = query.isBlank()
            
            val baseFiltered = transactions.filter { txn ->
                val matchesQuery = isQueryBlank ||
                        txn.merchant.contains(query, ignoreCase = true) ||
                        txn.sender.contains(query, ignoreCase = true) ||
                        txn.body.contains(query, ignoreCase = true)

                if (!matchesQuery) return@filter false

                val matchesFilter = when (f.filter) {
                    "Debit" -> txn.type == "DEBIT"
                    "Credit" -> txn.type == "CREDIT"
                    else -> true
                }
                if (!matchesFilter) return@filter false

                val matchesCategory = f.category == "All" || txn.category == f.category
                if (!matchesCategory) return@filter false

                val matchesAccount = f.account == "All" || txn.accountHint == f.account
                if (!matchesAccount) return@filter false

                true
            }

            val filteredWithDates = baseFiltered.filter { txn ->
                val matchesDateStart = start == null || txn.transactionTime >= start
                val matchesDateEnd = end == null || txn.transactionTime <= (end + 86400000L - 1)
                matchesDateStart && matchesDateEnd
            }

            val finalFiltered = if (f.month == null) {
                filteredWithDates
            } else {
                filteredWithDates.filter {
                    val cal = Calendar.getInstance().apply { timeInMillis = it.transactionTime }
                    monthFormat.format(cal.time) == f.month
                }
            }.let { list ->
                when (f.sortOrder) {
                    TransactionSortOrder.DATE_DESC -> list.sortedByDescending { it.transactionTime }
                    TransactionSortOrder.DATE_ASC -> list.sortedBy { it.transactionTime }
                    TransactionSortOrder.AMOUNT_DESC -> list.sortedByDescending { it.amount }
                    TransactionSortOrder.AMOUNT_ASC -> list.sortedBy { it.amount }
                    TransactionSortOrder.MERCHANT_ASC -> list.sortedBy { it.merchant.lowercase() }
                    TransactionSortOrder.MERCHANT_DESC -> list.sortedByDescending { it.merchant.lowercase() }
                }
            }

            // Trend chart shows all historical data for the current search/category/account, ignoring date range filters
            val trendSummaries = baseFiltered.filter { it.isBillable }
                .groupBy {
                    val cal = Calendar.getInstance().apply { timeInMillis = it.transactionTime }
                    monthFormat.format(cal.time)
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

            val availableCategories = (listOf("All") +
                    transactions.map { it.category } +
                    customCategories.map { it.name }
                    ).filter { it.isNotBlank() }
                .distinct()
                .sorted()

            val availableAccounts = (listOf("All") +
                    transactions.map { it.accountHint } +
                    accounts.map { it.name }
                    ).filter { it.isNotBlank() }
                .distinct()
                .sorted()

            TransactionsUiState(
                allTransactions = transactions,
                filteredTransactions = finalFiltered,
                availableCategories = availableCategories,
                availableAccounts = availableAccounts,
                trendSummaries = trendSummaries,
                customCategories = customCategories,
                query = f.query,
                selectedFilter = f.filter,
                selectedCategory = f.category,
                selectedAccount = f.account,
                selectedSortOrder = f.sortOrder,
                startDateMillis = start,
                endDateMillis = end,
                selectedMonth = f.month,
                isLoading = false
            )
        }
    }.flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionsUiState(isLoading = true)
    )

    fun onQueryChange(query: String) { _query.value = query }
    fun onFilterChange(filter: String) { _selectedFilter.value = filter }
    fun onCategoryChange(category: String) { _selectedCategory.value = category }
    fun onAccountChange(account: String) { _selectedAccount.value = account }
    fun onSortOrderChange(order: TransactionSortOrder) { _selectedSortOrder.value = order }

    fun onMonthToggle(month: String) {
        _selectedMonth.value = if (_selectedMonth.value == month) null else month
    }

    fun setDateRange(start: Long?, end: Long?) {
        _dateRange.value = start to end
    }

    fun clearDates() {
        _dateRange.value = null to null
    }

    fun saveTransaction(txn: SmsTransactionEntity) {
        viewModelScope.launch { repository.update(txn) }
    }

    fun deleteTransaction(txn: SmsTransactionEntity) {
        viewModelScope.launch { repository.delete(txn) }
    }

    fun deleteTransactions(ids: List<Int>) {
        viewModelScope.launch { repository.deleteByIds(ids) }
    }

    fun updateAnalytics(ids: List<Int>, include: Boolean) {
        viewModelScope.launch { repository.updateBillableForIds(ids, include) }
    }

    fun addTransaction(txn: SmsTransactionEntity) {
        viewModelScope.launch { repository.insert(txn) }
    }

    fun exportToCsv(context: Context) {
        viewModelScope.launch {
            try {
                val state = uiState.value
                val txns = state.filteredTransactions.sortedByDescending { it.transactionTime }
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
                    val repository = TransactionRepository(
                        database.smsTransactionDao(),
                        database.customCategoryDao(),
                        database.accountDao()
                    )
                    return TransactionsViewModel(repository) as T
                }
            }
    }
}
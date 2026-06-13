package app.ledgerpop.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.data.repository.TransactionRepository
import app.ledgerpop.ui.state.TrendSummary
import app.ledgerpop.ui.state.TransactionsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    private val _selectedMonth = MutableStateFlow<String?>(null)
    private val _dateRange = MutableStateFlow<Pair<Long?, Long?>>(null to null)
    private val monthFormat = SimpleDateFormat("MMM yy", Locale.getDefault())

    private data class InternalFilters(
        val query: String,
        val filter: String,
        val category: String,
        val account: String,
        val month: String?,
        val dateRange: Pair<Long?, Long?>
    )

    private val filters = combine(
        combine(
            _query.debounce(100L).onStart { emit(_query.value) }.distinctUntilChanged(),
            _selectedFilter,
            _selectedCategory
        ) { q, f, c -> Triple(q, f, c) },
        combine(_selectedAccount, _selectedMonth, _dateRange) { a, m, r -> Triple(a, m, r) }
    ) { t1, t2 ->
        InternalFilters(
            query = t1.first,
            filter = t1.second,
            category = t1.third,
            account = t2.first,
            month = t2.second,
            dateRange = t2.third
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

    fun addTransaction(txn: SmsTransactionEntity) {
        viewModelScope.launch { repository.insert(txn) }
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
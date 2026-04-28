package app.ledgerpop.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.data.repository.TransactionRepository
import app.ledgerpop.ui.state.TransactionsUiState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TransactionsViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _selectedFilter = MutableStateFlow("All")
    private val _selectedCategory = MutableStateFlow("All")
    private val _dateRange = MutableStateFlow<Pair<Long?, Long?>>(null to null)

    val uiState: StateFlow<TransactionsUiState> = combine(
        repository.getAllTransactions(),
        _query,
        _selectedFilter,
        _selectedCategory,
        _dateRange
    ) { transactions, query, filter, category, dateRange ->
        val (start, end) = dateRange
        
        val filtered = transactions.filter { txn ->
            val matchesQuery = query.isBlank() ||
                    txn.merchant.contains(query, ignoreCase = true) ||
                    txn.sender.contains(query, ignoreCase = true) ||
                    txn.body.contains(query, ignoreCase = true)

            val matchesFilter = when (filter) {
                "Debit" -> txn.type == "DEBIT"
                "Credit" -> txn.type == "CREDIT"
                else -> true
            }

            val matchesCategory = category == "All" || txn.category == category

            val matchesDateStart = start == null || txn.transactionTime >= start
            val matchesDateEnd = end == null || txn.transactionTime <= (end + 86400000L - 1)

            matchesQuery && matchesFilter && matchesCategory && matchesDateStart && matchesDateEnd
        }

        TransactionsUiState(
            allTransactions = transactions,
            filteredTransactions = filtered,
            query = query,
            selectedFilter = filter,
            selectedCategory = category,
            startDateMillis = start,
            endDateMillis = end,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionsUiState(isLoading = true)
    )

    fun onQueryChange(query: String) { _query.value = query }
    fun onFilterChange(filter: String) { _selectedFilter.value = filter }
    fun onCategoryChange(category: String) { _selectedCategory.value = category }

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
                        database.customCategoryDao()
                    )
                    return TransactionsViewModel(repository) as T
                }
            }
    }
}
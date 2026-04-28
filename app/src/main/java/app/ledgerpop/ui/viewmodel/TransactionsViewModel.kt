package app.ledgerpop.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.data.repository.TransactionRepository
import app.ledgerpop.ui.state.TransactionsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TransactionsViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState(isLoading = true))
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllTransactions().collect { transactions ->
                _uiState.update {
                    it.copy(allTransactions = transactions, isLoading = false)
                }
            }
        }
    }

    fun onQueryChange(query: String) = _uiState.update { it.copy(query = query) }
    fun onFilterChange(filter: String) = _uiState.update { it.copy(selectedFilter = filter) }
    fun onCategoryChange(category: String) = _uiState.update { it.copy(selectedCategory = category) }

    // ── Date Filter Functions ────────────────────────────────────────────────

    fun setDateRange(start: Long?, end: Long?) {
        _uiState.update { it.copy(startDateMillis = start, endDateMillis = end) }
    }

    fun clearDates() {
        _uiState.update { it.copy(startDateMillis = null, endDateMillis = null) }
    }

    fun filteredTransactions(): List<SmsTransactionEntity> {
        val state = _uiState.value
        return state.allTransactions.filter { txn ->
            // 1. Text Query
            val matchesQuery = state.query.isBlank() ||
                    txn.merchant.contains(state.query, ignoreCase = true) ||
                    txn.sender.contains(state.query, ignoreCase = true) ||
                    txn.body.contains(state.query, ignoreCase = true)

            // 2. Type Filter
            val matchesFilter = when (state.selectedFilter) {
                "Debit" -> txn.type == "DEBIT"
                "Credit" -> txn.type == "CREDIT"
                else -> true
            }

            // 3. Category Filter
            val matchesCategory = state.selectedCategory == "All" ||
                    txn.category == state.selectedCategory

            // 4. Date Range Filter
            val matchesDateStart = state.startDateMillis == null || txn.transactionTime >= state.startDateMillis
            val matchesDateEnd = state.endDateMillis == null || txn.transactionTime <= (state.endDateMillis + 86400000L - 1) // include to end of day

            matchesQuery && matchesFilter && matchesCategory && matchesDateStart && matchesDateEnd
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
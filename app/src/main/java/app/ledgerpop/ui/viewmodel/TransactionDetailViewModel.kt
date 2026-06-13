package app.ledgerpop.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.repository.TransactionRepository
import app.ledgerpop.ui.state.TransactionDetailUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TransactionDetailViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionDetailUiState())
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()

    fun loadTransaction(id: Int) {
        viewModelScope.launch {
            val txn = repository.getById(id)
            _uiState.update { it.copy(transaction = txn, isLoading = false) }
        }
    }

    fun toggleBillable() {
        val txn = _uiState.value.transaction ?: return
        viewModelScope.launch {
            val updated = txn.copy(isBillable = !txn.isBillable)
            repository.update(updated)
            _uiState.update { it.copy(transaction = updated) }
        }
    }

    fun dismiss() {
        _uiState.update { TransactionDetailUiState() }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun factory(database: LedgerPopDatabase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TransactionDetailViewModel(
                        TransactionRepository(
                            database.smsTransactionDao(),
                            database.customCategoryDao(),
                            database.accountDao()
                        )
                    ) as T
            }
    }
}
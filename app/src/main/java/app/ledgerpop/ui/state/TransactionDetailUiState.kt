package app.ledgerpop.ui.state

import app.ledgerpop.data.local.SmsTransactionEntity

data class TransactionDetailUiState(
    val transaction: SmsTransactionEntity? = null,
    val isLoading: Boolean = true
)
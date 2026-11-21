package app.ledgerpop.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.ledgerpop.data.category.CategoryEngine
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.data.repository.TransactionRepository
import app.ledgerpop.ui.state.TrendSummary
import app.ledgerpop.ui.state.TransactionsUiState
import app.ledgerpop.ui.state.TransactionSortOrder
import app.ledgerpop.utils.AmountUtils
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
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
    private val _selectedCategories = MutableStateFlow(setOf("All"))
    private val _selectedAccounts = MutableStateFlow(setOf("All"))
    private val _selectedSortOrder = MutableStateFlow(TransactionSortOrder.DATE_DESC)
    private val _selectedMonth = MutableStateFlow<String?>(null)
    private val _dateRange = MutableStateFlow<Pair<Long?, Long?>>(null to null)
    private val monthFormat = SimpleDateFormat("MMM yy", Locale.getDefault())

    private data class InternalFilters(
        val query: String,
        val filter: String,
        val categories: Set<String>,
        val accounts: Set<String>,
        val sortOrder: TransactionSortOrder,
        val month: String?,
        val dateRange: Pair<Long?, Long?>
    )

    private data class SecondaryFilters(
        val accounts: Set<String>,
        val sortOrder: TransactionSortOrder,
        val month: String?,
        val dateRange: Pair<Long?, Long?>
    )

    private val filters = combine(
        combine(
            _query.debounce(100L).onStart { emit(_query.value) }.distinctUntilChanged(),
            _selectedFilter,
            _selectedCategories
        ) { q, f, c -> Triple(q, f, c) },
        combine(_selectedAccounts, _selectedSortOrder, _selectedMonth, _dateRange) { a, s, m, r ->
            SecondaryFilters(a, s, m, r)
        }
    ) { t1, t2 ->
        InternalFilters(
            query = t1.first,
            filter = t1.second,
            categories = t1.third,
            accounts = t2.accounts,
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
            
            val searchDateFormatter = if (!isQueryBlank) SimpleDateFormat("d MMM yyyy MMMM", Locale.getDefault()) else null
            val queryTokens = if (isQueryBlank) emptyList() else query.split("\\s+".toRegex()).filter { it.isNotBlank() }
            val cleanTokens = queryTokens.map { token ->
                token.removePrefix("₹")
                    .removePrefix("$")
                    .removePrefix("INR")
                    .removePrefix("inr")
                    .removePrefix("Rs.")
                    .removePrefix("rs.")
                    .removePrefix("Rs")
                    .removePrefix("rs")
                    .trim()
            }

            val baseFiltered = transactions.filter { txn ->
                val matchesQuery = isQueryBlank || matchesUniversalSearch(
                    txn = txn,
                    query = query,
                    queryTokens = queryTokens,
                    cleanTokens = cleanTokens,
                    dateFormatter = searchDateFormatter
                )

                if (!matchesQuery) return@filter false

                // If a query is active, ignore other filters to search across all transactions
                if (!isQueryBlank) return@filter true

                val matchesFilter = when (f.filter) {
                    "Debit" -> txn.type == "DEBIT"
                    "Credit" -> txn.type == "CREDIT"
                    else -> true
                }
                if (!matchesFilter) return@filter false

                val matchesCategory = f.categories.contains("All") || f.categories.contains(txn.category)
                if (!matchesCategory) return@filter false

                val matchesAccount = f.accounts.contains("All") || f.accounts.contains(txn.accountHint)
                if (!matchesAccount) return@filter false

                true
            }

            val filteredWithDates = if (!isQueryBlank) baseFiltered else {
                baseFiltered.filter { txn ->
                    val matchesDateStart = start == null || txn.transactionTime >= start
                    val matchesDateEnd = end == null || txn.transactionTime <= (end + 86400000L - 1)
                    matchesDateStart && matchesDateEnd
                }
            }

            val finalFiltered = if (f.month == null || !isQueryBlank) {
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
                selectedCategories = f.categories,
                selectedAccounts = f.accounts,
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
    fun onCategoryChange(category: String) {
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

    fun onAccountChange(account: String) {
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

    fun updateCategories(ids: List<Int>, category: String) {
        viewModelScope.launch { repository.updateCategoryForIds(ids, category) }
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
        internal fun matchesUniversalSearch(
            txn: SmsTransactionEntity,
            query: String,
            queryTokens: List<String> = query.trim().split("\\s+".toRegex()).filter { it.isNotBlank() },
            cleanTokens: List<String> = queryTokens.map { token ->
                token.removePrefix("₹")
                    .removePrefix("$")
                    .removePrefix("INR")
                    .removePrefix("inr")
                    .removePrefix("Rs.")
                    .removePrefix("rs.")
                    .removePrefix("Rs")
                    .removePrefix("rs")
                    .trim()
            },
            dateFormatter: SimpleDateFormat? = SimpleDateFormat("d MMM yyyy MMMM", Locale.getDefault())
        ): Boolean {
            val title = txn.merchant.ifBlank { txn.sender }
            val normalizedMerchant = CategoryEngine.normalizeMerchant(txn.merchant)
            val categoryEmoji = CategoryEngine.emoji(txn.category)
            val rawAmount = AmountUtils.formatRaw(txn.amount)
            val formattedAmount = AmountUtils.formatAmount(txn.amount)
            val formattedCurrency = AmountUtils.formatWithCurrency(txn.amount)
            val compactAmount = AmountUtils.formatCompact(txn.amount)
            val amountStr = txn.amount.toString()

            val origRaw = txn.originalAmount?.let { AmountUtils.formatRaw(it) }.orEmpty()
            val origFormatted = txn.originalAmount?.let { AmountUtils.formatAmount(it) }.orEmpty()
            val origCurrency = txn.originalAmount?.let { AmountUtils.formatWithCurrency(it) }.orEmpty()
            val origCompact = txn.originalAmount?.let { AmountUtils.formatCompact(it) }.orEmpty()
            val origStr = txn.originalAmount?.toString().orEmpty()

            val dateFormatted = try {
                dateFormatter?.format(Date(txn.transactionTime)).orEmpty()
            } catch (_: Exception) {
                ""
            }

            val typeFriendly = when (txn.type.uppercase()) {
                "DEBIT" -> "Debit Expense Dr"
                "CREDIT" -> "Credit Income Cr"
                else -> txn.type
            }

            val searchFields = listOf(
                title,
                txn.merchant,
                normalizedMerchant,
                txn.sender,
                txn.body,
                txn.note,
                txn.category,
                categoryEmoji,
                txn.accountHint,
                txn.bank,
                typeFriendly,
                dateFormatted,
                rawAmount,
                formattedAmount,
                formattedCurrency,
                compactAmount,
                amountStr,
                origRaw,
                origFormatted,
                origCurrency,
                origCompact,
                origStr,
                "₹ Rs Rs. INR $"
            )

            // 1. Direct contiguous match across individual fields
            if (searchFields.any { it.contains(query, ignoreCase = true) }) {
                return true
            }

            // 2. Direct contiguous match across combined contents
            val combinedContent = "$title ${txn.merchant} $normalizedMerchant ${txn.sender} ${txn.note} ${txn.category} ${txn.accountHint} ${txn.bank} ${txn.body}"
            if (combinedContent.contains(query, ignoreCase = true)) {
                return true
            }

            // 3. Multi-token match: Every whitespace-separated token in the query must match at least one field
            if (queryTokens.isNotEmpty()) {
                val allTokensMatch = queryTokens.indices.all { i ->
                    val token = queryTokens[i]
                    val clean = cleanTokens[i]
                    searchFields.any { field ->
                        field.contains(token, ignoreCase = true) ||
                            (clean.isNotEmpty() && field.contains(clean, ignoreCase = true))
                    }
                }
                if (allTokensMatch) return true
            }

            return false
        }

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
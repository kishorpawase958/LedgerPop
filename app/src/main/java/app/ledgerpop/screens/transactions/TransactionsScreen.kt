package app.ledgerpop.screens.transactions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ledgerpop.data.category.CategoryEngine
import app.ledgerpop.data.local.CustomCategoryEntity
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.ui.state.TrendSummary
import app.ledgerpop.ui.viewmodel.TransactionsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen() {
    val context = LocalContext.current
    val db = remember { LedgerPopDatabase.getInstance(context) }
    val viewModel: TransactionsViewModel = viewModel(
        factory = TransactionsViewModel.factory(db)
    )
    val uiState by viewModel.uiState.collectAsState()
    val filtered = uiState.filteredTransactions

    var selectedTxn by remember { mutableStateOf<SmsTransactionEntity?>(null) }
    var quickCategoryTxn by remember { mutableStateOf<SmsTransactionEntity?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val onTransactionClick = remember { { txn: SmsTransactionEntity -> selectedTxn = txn } }
    val onQuickCategoryClick = remember { { txn: SmsTransactionEntity -> quickCategoryTxn = txn } }
    val filterOptions = remember { listOf("All", "Debit", "Credit") }

    val categories = uiState.availableCategories
    val accounts = uiState.availableAccounts

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ── Top bar ────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(top = 16.dp, bottom = 8.dp)
            ) {
                Text(
                    text = "Transactions",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )

                // Search
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text("Search merchant, sender…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )

                // ── Date Range Filter Button ─────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        val dateText = if (uiState.startDateMillis != null && uiState.endDateMillis != null) {
                            val sdf = SimpleDateFormat("dd MMM yy", Locale.getDefault())
                            "${sdf.format(Date(uiState.startDateMillis!!))} - ${sdf.format(Date(uiState.endDateMillis!!))}"
                        } else {
                            "Select Date Range"
                        }
                        Text(dateText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    if (uiState.startDateMillis != null) {
                        IconButton(
                            onClick = { viewModel.clearDates() },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Clear Dates",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Type & Category filter chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    items(filterOptions, key = { it }) { filter ->
                        FilterChip(
                            selected = uiState.selectedFilter == filter,
                            onClick = { viewModel.onFilterChange(filter) },
                            label = { Text(filter) },
                            leadingIcon = if (uiState.selectedFilter == filter) ({
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }) else null
                        )
                    }

                    // Category chips
                    if (categories.size > 1) {
                        items(categories.drop(1), key = { it }) { cat ->
                            FilterChip(
                                selected = uiState.selectedCategory == cat,
                                onClick = {
                                    viewModel.onCategoryChange(
                                        if (uiState.selectedCategory == cat) "All" else cat
                                    )
                                },
                                label = { Text(cat) },
                                leadingIcon = if (uiState.selectedCategory == cat) ({
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }) else null
                            )
                        }
                    }
                }

                // Account filter chips
                if (accounts.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        items(accounts, key = { it }) { acc ->
                            FilterChip(
                                selected = uiState.selectedAccount == acc,
                                onClick = {
                                    viewModel.onAccountChange(
                                        if (uiState.selectedAccount == acc) "All" else acc
                                    )
                                },
                                label = { Text(acc) },
                                leadingIcon = if (uiState.selectedAccount == acc) ({
                                    Icon(
                                        Icons.Rounded.AccountBalanceWallet,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }) else null
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            )

            // ── List ────────────────────────────────────────────────────────
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (uiState.allTransactions.isEmpty())
                                "No transactions yet"
                            else "No results found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (uiState.allTransactions.isEmpty())
                                "Go to Settings → Import SMS"
                            else "Try a different search or filter",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                // Count bar
                Text(
                    text = "${filtered.size} transaction${if (filtered.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    if (uiState.trendSummaries.isNotEmpty()) {
                        item {
                            Text(
                                text = "Monthly Trend",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)
                            )
                            ScrollableBarChart(
                                summaries = uiState.trendSummaries,
                                selectedMonth = uiState.selectedMonth,
                                onBarClick = { viewModel.onMonthToggle(it) }
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    items(
                        items = filtered,
                        key = { it.id }
                    ) { txn ->
                        TransactionRow(
                            txn = txn,
                            customCategories = uiState.customCategories,
                            onClick = onTransactionClick,
                            onCategoryClick = onQuickCategoryClick
                        )
                    }
                }
            }
        }

        // ── FAB ─────────────────────────────────────────────────────────────
        ExtendedFloatingActionButton(
            onClick = { showAddSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 135.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("Add") }
        )
    }

    // ── Quick Category Update Dialog ─────────────────────────────────────────
    quickCategoryTxn?.let { txn ->
        QuickCategoryUpdateDialog(
            txn = txn,
            customCategories = uiState.customCategories,
            onDismiss = { quickCategoryTxn = null },
            onSave = { updatedTxn ->
                viewModel.saveTransaction(updatedTxn)
                quickCategoryTxn = null
            }
        )
    }

    // ── Date Range Picker Dialog ──────────────────────────────────────────────
    if (showDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = uiState.startDateMillis,
            initialSelectedEndDateMillis = uiState.endDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setDateRange(
                        start = dateRangePickerState.selectedStartDateMillis,
                        end = dateRangePickerState.selectedEndDateMillis
                    )
                    showDatePicker = false
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier.weight(1f)
            )
        }
    }

    // ── Detail / edit sheet ──────────────────────────────────────────────────
    selectedTxn?.let { txn ->
        TransactionDetailSheet(
            txn = txn,
            onDismiss = { selectedTxn = null },
            onSave = { updated ->
                viewModel.saveTransaction(updated)
                selectedTxn = null
            },
            onDelete = { toDelete ->
                viewModel.deleteTransaction(toDelete)
                selectedTxn = null
            }
        )
    }

    // ── Add transaction sheet ────────────────────────────────────────────────
    if (showAddSheet) {
        AddTransactionSheet(
            onDismiss = { showAddSheet = false },
            onAdd = { viewModel.addTransaction(it) }
        )
    }
}

@Composable
fun ScrollableBarChart(
    summaries: List<TrendSummary>,
    selectedMonth: String? = null,
    onBarClick: (String) -> Unit
) {
    val maxAmount = remember(summaries) {
        maxOf(
            summaries.maxOfOrNull { it.income } ?: 0.0,
            summaries.maxOfOrNull { it.expense } ?: 0.0,
            1.0 // Prevent division by zero
        ).toFloat()
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val incomeColor = Color(0xFF00B894)
    val expenseColor = MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(summaries, key = { it.label }) { summary ->
                val isSelected = summary.label == selectedMonth
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
                        .clickable { onBarClick(summary.label) }
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    Canvas(modifier = Modifier
                        .height(120.dp)
                        .width(40.dp)
                        .alpha(if (selectedMonth == null || isSelected) 1f else 0.4f)
                    ) {
                        val canvasHeight = size.height
                        val barWidth = 16.dp.toPx()
                        val spacing = 4.dp.toPx()

                        val incomeHeight = (summary.income.toFloat() / maxAmount) * canvasHeight
                        val expenseHeight = (summary.expense.toFloat() / maxAmount) * canvasHeight

                        drawRoundRect(
                            color = incomeColor,
                            topLeft = Offset(x = 0f, y = canvasHeight - incomeHeight),
                            size = Size(width = barWidth, height = incomeHeight),
                            cornerRadius = CornerRadius(x = 8.dp.toPx(), y = 8.dp.toPx())
                        )

                        drawRoundRect(
                            color = expenseColor,
                            topLeft = Offset(x = barWidth + spacing, y = canvasHeight - expenseHeight),
                            size = Size(width = barWidth, height = expenseHeight),
                            cornerRadius = CornerRadius(x = 8.dp.toPx(), y = 8.dp.toPx())
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = summary.label,
                        style = if (isSelected) MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold) 
                               else MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ── Transaction Row ────────────────────────────────────────────────────────────

private val transactionDateFormatter = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

@Composable
private fun TransactionRow(
    txn: SmsTransactionEntity,
    customCategories: List<CustomCategoryEntity> = emptyList(),
    onClick: (SmsTransactionEntity) -> Unit,
    onCategoryClick: (SmsTransactionEntity) -> Unit
) {
    val isDebit = txn.type == "DEBIT"
    val isBillable = txn.isBillable
    val amountColor = if (isDebit) MaterialTheme.colorScheme.error else Color(0xFF00B894)
    val time = remember(txn.transactionTime) {
        transactionDateFormatter.format(Date(txn.transactionTime))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick(txn) }
            .alpha(if (isBillable) 1f else 0.45f)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon (Clickable for quick category change)
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    if (isBillable) amountColor.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable { onCategoryClick(txn) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = CategoryEngine.emoji(txn.category, customCategories),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.alpha(if (isBillable) 1f else 0.5f)
            )
        }

        Spacer(Modifier.width(14.dp))

        // Labels
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = txn.merchant.ifBlank { txn.sender },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!isBillable) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "excluded",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (txn.category.isNotBlank()) {
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = txn.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Amount
        Text(
            text = "${if (isDebit) "−" else "+"} ₹${"%,.0f".format(txn.amount)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (isBillable) amountColor
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Quick Category Update Dialog ───────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickCategoryUpdateDialog(
    txn: SmsTransactionEntity,
    customCategories: List<CustomCategoryEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (SmsTransactionEntity) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(txn.category.ifBlank { CategoryEngine.OTHER }) }

    val categories = remember(txn.type, customCategories) {
        val standard = if (txn.type == "CREDIT") {
            CategoryEngine.creditCategories()
        } else {
            CategoryEngine.debitCategories()
        }
        val custom = customCategories.filter { it.type == txn.type }.map { it.name }
        (standard + custom).distinct()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Update Category",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Select a new category for ${txn.merchant}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text("${CategoryEngine.emoji(cat, customCategories)} $cat") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(txn.copy(category = selectedCategory)) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Discard")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

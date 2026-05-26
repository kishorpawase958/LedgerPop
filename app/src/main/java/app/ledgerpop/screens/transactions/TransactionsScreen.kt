package app.ledgerpop.screens.transactions

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import app.ledgerpop.ui.theme.Purple700
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen() {
    val context = LocalContext.current
    val db = remember { LedgerPopDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
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

                // Search & Date Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text("Search transactions…") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    )

                    val hasDates = uiState.startDateMillis != null
                    OutlinedIconButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.size(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = IconButtonDefaults.outlinedIconButtonColors(
                            containerColor = if (hasDates) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else Color.Transparent,
                            contentColor = if (hasDates) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (hasDates) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    ) {
                        Icon(Icons.Rounded.DateRange, contentDescription = "Date Range")
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
                            allTransactions = uiState.allTransactions,
                            onClick = onTransactionClick,
                            onCategoryClick = onQuickCategoryClick
                        )
                    }
                }
            }
        }

        // ── FAB ─────────────────────────────────────────────────────────────
        FloatingActionButton(
            onClick = { showAddSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 135.dp),
            containerColor = Purple700,
            contentColor = Color.White,
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Add",
                modifier = Modifier.size(28.dp)
            )
        }
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
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.startDateMillis != null) {
                        TextButton(
                            onClick = {
                                viewModel.clearDates()
                                showDatePicker = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Clear") }
                    }
                    TextButton(onClick = {
                        viewModel.setDateRange(
                            start = dateRangePickerState.selectedStartDateMillis,
                            end = dateRangePickerState.selectedEndDateMillis
                        )
                        showDatePicker = false
                    }) { Text("Apply") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier.weight(1f),
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                    selectedYearContainerColor = MaterialTheme.colorScheme.primary,
                    todayContentColor = MaterialTheme.colorScheme.primary,
                    todayDateBorderColor = MaterialTheme.colorScheme.primary,
                    dayInSelectionRangeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    dayInSelectionRangeContentColor = MaterialTheme.colorScheme.primary
                )
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
            },
            onNavigateToTransaction = { id ->
                scope.launch {
                    val target = db.smsTransactionDao().getById(id)
                    selectedTxn = target
                }
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
    val density = LocalDensity.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val creditColor = Color(0xFF00B894)
    val debitColor = Color(0xFF9C27B0) // Purple

    val itemWidth = 52.dp
    val spacing = 16.dp
    val chartHeight = 120.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor)
    ) {
        val scrollState = rememberScrollState()

        // Auto-scroll to latest (rightmost) data on first load
        var hasAutoScrolled by remember(summaries.isEmpty()) { mutableStateOf(false) }
        LaunchedEffect(summaries, scrollState.maxValue) {
            if (summaries.isNotEmpty() && scrollState.maxValue > 0 && !hasAutoScrolled) {
                scrollState.scrollTo(scrollState.maxValue)
                hasAutoScrolled = true
            }
        }
        
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            val viewportWidth = with(density) { maxWidth.toPx() }
            val itemWidthPx = with(density) { itemWidth.toPx() }
            val spacingPx = with(density) { spacing.toPx() }
            val totalItemStepPx = itemWidthPx + spacingPx

            // Dynamically calculate visible range and its max value for scaling
            val visibleMax by remember(summaries) {
                derivedStateOf {
                    val scrollX = scrollState.value.toFloat()
                    val startIndex = (scrollX / totalItemStepPx).toInt().coerceAtLeast(0)
                    val endIndex = ((scrollX + viewportWidth) / totalItemStepPx).toInt().coerceAtMost(summaries.size - 1)

                    if (summaries.isEmpty()) 1.0 else {
                        var maxV = 0.0
                        for (i in startIndex..endIndex) {
                            maxV = maxOf(maxV, summaries[i].income, summaries[i].expense)
                        }
                        if (maxV <= 0) 1.0 else maxV
                    }
                }
            }

            // Scale so visibleMax is at 85% of chart height
            val chartMaxAmount by animateFloatAsState(
                targetValue = (visibleMax / 0.85f).toFloat(),
                label = "ChartScale"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 16.dp)
            ) {
                val totalWidth = if (summaries.isEmpty()) 0.dp else (itemWidth + spacing) * summaries.size - spacing
                
                Canvas(
                    modifier = Modifier
                        .width(totalWidth)
                        .height(chartHeight)
                ) {
                    val canvasHeight = size.height
                    val points = mutableListOf<Offset>()

                    summaries.forEachIndexed { index, summary ->
                        val isSelected = summary.label == selectedMonth
                        val alpha = if (selectedMonth == null || isSelected) 1f else 0.4f
                        val xCenter = index * (itemWidthPx + spacingPx) + itemWidthPx / 2
                        
                        // Debit Bar (Purple)
                        val expenseHeight = (summary.expense.toFloat() / chartMaxAmount) * canvasHeight
                        val barWidth = 18.dp.toPx()
                        drawRoundRect(
                            color = debitColor,
                            topLeft = Offset(x = xCenter - barWidth / 2, y = canvasHeight - expenseHeight),
                            size = Size(width = barWidth, height = expenseHeight),
                            cornerRadius = CornerRadius(x = 4.dp.toPx(), y = 4.dp.toPx()),
                            alpha = alpha
                        )

                        // Credit Point (for Line Chart)
                        val incomeHeight = (summary.income.toFloat() / chartMaxAmount) * canvasHeight
                        points.add(Offset(x = xCenter, y = canvasHeight - incomeHeight))
                    }

                    // Draw Credit Line
                    if (points.size > 1) {
                        for (i in 0 until points.size - 1) {
                            val isSelected = selectedMonth == null ||
                                           summaries[i].label == selectedMonth ||
                                           summaries[i+1].label == selectedMonth
                            val lineAlpha = if (isSelected) 1f else 0.3f

                            drawLine(
                                color = creditColor,
                                start = points[i],
                                end = points[i+1],
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                alpha = lineAlpha
                            )
                        }
                    }

                    // Draw Credit Points
                    points.forEachIndexed { index, point ->
                        val monthSelected = selectedMonth == null || summaries[index].label == selectedMonth
                        val alpha = if (monthSelected) 1f else 0.4f

                        drawCircle(
                            color = creditColor,
                            radius = 5.dp.toPx(),
                            center = point,
                            alpha = alpha
                        )
                        drawCircle(
                            color = surfaceColor,
                            radius = 2.5.dp.toPx(),
                            center = point,
                            alpha = alpha
                        )
                    }
                }

                // Interactive layer and Labels
                Row {
                    summaries.forEach { summary ->
                        val isSelected = summary.label == selectedMonth
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(itemWidth)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
                                .clickable { onBarClick(summary.label) }
                                .padding(vertical = 4.dp)
                        ) {
                            Spacer(Modifier.height(chartHeight + 8.dp))
                            Text(
                                text = summary.label,
                                style = if (isSelected) MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                       else MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        if (summary != summaries.last()) {
                            Spacer(Modifier.width(spacing))
                        }
                    }
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
    allTransactions: List<SmsTransactionEntity> = emptyList(),
    onClick: (SmsTransactionEntity) -> Unit,
    onCategoryClick: (SmsTransactionEntity) -> Unit
) {
    val isDebit = txn.type == "DEBIT"
    val isBillable = txn.isBillable
    val amountColor = if (isDebit) MaterialTheme.colorScheme.onSurface else Color(0xFF00B894)
    val time = remember(txn.transactionTime) {
        transactionDateFormatter.format(Date(txn.transactionTime))
    }

    val linkedAmount = remember(txn, allTransactions) {
        if (isDebit) {
            allTransactions.filter { it.linkedTransactionId == txn.id }.sumOf { it.amount }
        } else 0.0
    }
    val hasLinks = remember(txn, allTransactions) {
        if (isDebit) linkedAmount > 0
        else txn.linkedTransactionId != null
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
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${if (txn.amount < 0) "−" else ""}₹${"%,.0f".format(kotlin.math.abs(txn.amount))}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isBillable) amountColor
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (txn.originalAmount != null) {
                Text(
                    text = "₹${"%,.0f".format(txn.originalAmount)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    ),
                    color = MaterialTheme.colorScheme.outline
                )
            } else if (hasLinks && !isDebit) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Link,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "Linked",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
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

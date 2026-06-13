package app.ledgerpop.screens.analytics

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ledgerpop.ui.components.ScrollableBarChart
import app.ledgerpop.ui.theme.MidnightPrimary
import app.ledgerpop.ui.theme.Purple700
import app.ledgerpop.ui.theme.Purple500
import app.ledgerpop.data.category.CategoryEngine
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.ui.state.CategorySummary
import app.ledgerpop.ui.viewmodel.AnalyticsViewModel
import app.ledgerpop.ui.viewmodel.DrillDownType
import app.ledgerpop.screens.transactions.TransactionDetailSheet
import app.ledgerpop.screens.transactions.QuickCategoryUpdateDialog
import app.ledgerpop.utils.AmountUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.atan2

private val SPENDS_COLORS = listOf(
    Purple700,
    Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFF43F5E),
    Color(0xFFF97316), Color(0xFFEAB308), Color(0xFF22C55E), Color(0xFF06B6D4),
    Color(0xFF4A5E7E), Color(0xFF775843), Color(0xFFBDB18F), Color(0xFF80D1DC),
    Color(0xFF7E3603), Color(0xFF3B2E05), Color(0xFF1E5633), Color(0xFF0C3138),
    Color(0xFFF89ABA), Color(0xFFFDB39B), Color(0xFFAFC595), Color(0xFFCFB6D3),
    Color(0xFF887567), Color(0xFF65635B), Color(0xFF81AF95), Color(0xFF7DBDCC),
)

private val INCOME_COLORS = listOf(
    Purple700,
    Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFF43F5E),
    Color(0xFFF97316), Color(0xFFEAB308), Color(0xFF22C55E), Color(0xFF06B6D4),
    Color(0xFF4A5E7E), Color(0xFF775843), Color(0xFFBDB18F), Color(0xFF80D1DC),
    Color(0xFF7E3603), Color(0xFF3B2E05), Color(0xFF1E5633), Color(0xFF0C3138),
    Color(0xFFF89ABA), Color(0xFFFDB39B), Color(0xFFAFC595), Color(0xFFCFB6D3),
    Color(0xFF887567), Color(0xFF65635B), Color(0xFF81AF95), Color(0xFF7DBDCC),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { configuration.locales[0] }
    val db = remember { LedgerPopDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val viewModel: AnalyticsViewModel = viewModel(factory = AnalyticsViewModel.factory(db))
    val uiState by viewModel.uiState.collectAsState()
    
    val isMidnight = MaterialTheme.colorScheme.primary == MidnightPrimary
    val accentColor = if (isMidnight) MaterialTheme.colorScheme.primaryContainer else Purple700
    val accentLight = if (isMidnight) MaterialTheme.colorScheme.primary else Purple500

    val drillDownData by viewModel.drillDownTransactions.collectAsState()
    val drillDownTitle by viewModel.drillDownTitle.collectAsState()

    val selectedTxnState = remember { mutableStateOf<SmsTransactionEntity?>(null) }
    val quickCategoryTxnState = remember { mutableStateOf<SmsTransactionEntity?>(null) }

    val showFiltersState = remember { mutableStateOf(false) }
    val showDatePickerState = remember { mutableStateOf(false) }
    val isBreakdownExpanded = remember { mutableStateOf(false) }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 220.dp)
        ) {
            // ── Header & Filter Toggle ────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Analytics",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(
                        onClick = { showFiltersState.value = !showFiltersState.value },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (showFiltersState.value || uiState.startDateMillis != null || uiState.selectedCategory != "All" || uiState.selectedAccount != "All")
                                MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                    ) {
                        Icon(
                            Icons.Rounded.FilterList,
                            contentDescription = "Filters",
                            tint = if (showFiltersState.value || uiState.startDateMillis != null)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Expandable Filter Section ─────────────────────────────────────
            item {
                AnimatedVisibility(
                    visible = showFiltersState.value,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Filters",
                                style = MaterialTheme.typography.titleMedium
                            )
                            TextButton(
                                onClick = { viewModel.clearFilters() },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Reset All", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        // Date Range Card
                        Surface(
                            onClick = { showDatePickerState.value = true },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.CalendarToday,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(
                                        "Time Period",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val dateText = remember(uiState.startDateMillis, uiState.endDateMillis, locale) {
                                        if (uiState.startDateMillis != null && uiState.endDateMillis != null) {
                                            val sdf = SimpleDateFormat("dd MMM", locale)
                                            "${sdf.format(Date(uiState.startDateMillis!!))} - ${sdf.format(Date(uiState.endDateMillis!!))}"
                                        } else "All Time"
                                    }
                                    Text(
                                        dateText,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }

                        // Account Selector
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Account",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                uiState.availableAccounts.drop(1).forEach { acc ->
                                    val isSelected = uiState.selectedAccount == acc
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setAccountFilter(acc) },
                                        label = { Text(acc) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        border = null,
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                                        } else null
                                    )
                                }
                            }
                        }

                        // Category Selector
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Category",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                uiState.availableCategories.drop(1).forEach { cat ->
                                    val isSelected = uiState.selectedCategory == cat
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setCategoryFilter(cat) },
                                        label = { Text(cat) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        border = null,
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── KPIs (Clickable) ──────────────────────────────────────────────
            item {
                val incomeText = remember(uiState.totalIncome) {
                    "${if (uiState.totalIncome < 0) "−" else ""}₹${AmountUtils.formatAmount(uiState.totalIncome)}"
                }
                val expenseText = remember(uiState.totalExpense) {
                    "${if (uiState.totalExpense < 0) "−" else ""}₹${AmountUtils.formatAmount(uiState.totalExpense)}"
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KpiCard(
                        label = "Income",
                        value = incomeText,
                        color = Color(0xFF00B894),
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.openDrillDown(DrillDownType.Income) }
                    )
                    KpiCard(
                        label = "Expenses",
                        value = expenseText,
                        color = accentLight,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.openDrillDown(DrillDownType.Expense) }
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            item {
                val netText = remember(uiState.net) {
                    "${if (uiState.net < 0) "−" else ""}₹${AmountUtils.formatAmount(uiState.net)}"
                }
                val netColor = when {
                    uiState.net > 0 -> Color(0xFF00B894)
                    uiState.net < 0 -> MaterialTheme.colorScheme.error
                    else -> Color.White
                }
                val avgDebitText = remember(uiState.avgDebit) {
                    "${if (uiState.avgDebit < 0) "−" else ""}₹${AmountUtils.formatAmount(uiState.avgDebit)}"
                }
                val avgCreditText = remember(uiState.avgCredit) {
                    "${if (uiState.avgCredit < 0) "−" else ""}₹${AmountUtils.formatAmount(uiState.avgCredit)}"
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KpiCard("Net", netText, netColor, Modifier.weight(1f))
                    KpiCard("Avg Credit", avgCreditText, Color(0xFF00B894), Modifier.weight(1f))
                    KpiCard("Avg Debit", avgDebitText, color = accentLight, Modifier.weight(1f))
                }
                Spacer(Modifier.height(24.dp))
            }

            // ── Monthly Trend Chart ───────────────────────────────────────────
            if (uiState.trendSummaries.isNotEmpty()) {
                item {
                    Text(
                        text = "Monthly Trend",
                        style = MaterialTheme.typography.titleSmall,
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

            // ── Category Distribution Header & Switch ───────────────────────
            if (uiState.totalIncome > 0 || uiState.totalExpense > 0) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal =30.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (uiState.viewType == app.ledgerpop.ui.state.AnalyticsViewType.SPENDS)
                                "Spending Distribution"
                            else
                                "Income Distribution",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        val isIncome = uiState.viewType == app.ledgerpop.ui.state.AnalyticsViewType.INCOME
                        val themeColor by animateColorAsState(
                            if (isIncome) Color(0xFF00B894) else accentColor,
                            label = "ThemeColor"
                        )
                        val switchBgColor by animateColorAsState(
                            themeColor.copy(alpha = 0.08f),
                            label = "SwitchBg"
                        )
                        val thumbOffset by animateDpAsState(
                            if (isIncome) 52.dp else 0.dp,
                            label = "ThumbOffset"
                        )

                        Box(
                            modifier = Modifier
                                .width(112.dp)
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(switchBgColor)
                                .border(1.dp, themeColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .clickable {
                                    viewModel.setViewType(
                                        if (isIncome) app.ledgerpop.ui.state.AnalyticsViewType.SPENDS
                                        else app.ledgerpop.ui.state.AnalyticsViewType.INCOME
                                    )
                                }
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(x = thumbOffset.roundToPx(), y = 0) }
                                    .fillMaxHeight()
                                    .width(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(themeColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isIncome) "Income" else "Spends",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // ── Category Donut Chart ──────────────────────────────────────────
            if (uiState.categoryBreakdown.isNotEmpty()) {
                item {
                    CategoryDonutChart(
                        summaries = uiState.categoryBreakdown,
                        customCategories = uiState.customCategories,
                        viewType = uiState.viewType,
                        onCategoryClick = { cat -> viewModel.openDrillDown(DrillDownType.Category(cat)) }
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

            // ── Category Breakdown (Clickable) ────────────────────────────────
            if (uiState.categoryBreakdown.isNotEmpty()) {
                item {
                    val breakdownTitle = if (uiState.viewType == app.ledgerpop.ui.state.AnalyticsViewType.SPENDS)
                        "Spending by Category"
                    else
                        "Income by Category"

                    Text(
                        breakdownTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }

                val breakdownItems = if (isBreakdownExpanded.value) uiState.categoryBreakdown else uiState.categoryBreakdown.take(7)

                items(
                    items = breakdownItems,
                    key = { it.category }
                ) { summary ->
                    CategoryRow(
                        summary = summary,
                        customCategories = uiState.customCategories,
                        viewType = uiState.viewType,
                        onClick = { viewModel.openDrillDown(DrillDownType.Category(summary.category)) }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (uiState.categoryBreakdown.size > 7) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = { isBreakdownExpanded.value = !isBreakdownExpanded.value }) {
                                Icon(
                                    if (isBreakdownExpanded.value) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.trendSummaries.isEmpty() && uiState.categoryBreakdown.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No transactions found for these filters.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ── Export FAB ────────────────────────────────────────────────────────
        FloatingActionButton(
            onClick = { viewModel.exportToCsv(context) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 135.dp),
            containerColor = accentColor,
            contentColor = Color.White,
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = "Export",
                modifier = Modifier.size(28.dp)
            )
        }
    }

    // ── Drill Down Bottom Sheet ───────────────────────────────────────────────
    if (drillDownData != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeDrillDown() },
            containerColor = MaterialTheme.colorScheme.surface, // Solid
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxHeight(0.9f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = drillDownTitle,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                Text(
                    text = "${drillDownData?.size ?: 0} transactions",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                if (drillDownData?.isEmpty() == true) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No matching transactions", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val allTxns = uiState.allTransactions
                    val data = drillDownData ?: emptyList()
                    
                    // Pre-calculate linked amounts to avoid O(N^2) complexity in the list
                    val linkedAmounts = remember(data, allTxns) {
                        val debitIds = data.filter { it.type == "DEBIT" }.map { it.id }.toSet()
                        if (debitIds.isEmpty()) emptyMap()
                        else {
                            allTxns
                                .filter { it.linkedTransactionId != null && it.linkedTransactionId in debitIds }
                                .groupBy { it.linkedTransactionId!! }
                                .mapValues { entry -> entry.value.sumOf { it.amount } }
                        }
                    }

                    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                        items(
                            items = data,
                            key = { it.id }
                        ) { txn ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                TransactionRowCompact(
                                    txn = txn,
                                    customCategories = uiState.customCategories,
                                    linkedAmount = linkedAmounts[txn.id] ?: 0.0,
                                    hasCreditLinks = txn.linkedTransactionId != null,
                                    onClick = { selectedTxnState.value = txn },
                                    onCategoryClick = { quickCategoryTxnState.value = txn }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Quick Category Update Dialog ─────────────────────────────────────────
    quickCategoryTxnState.value?.let { txn ->
        QuickCategoryUpdateDialog(
            txn = txn,
            customCategories = uiState.customCategories,
            onDismiss = { quickCategoryTxnState.value = null },
            onSave = { updatedTxn ->
                viewModel.saveTransaction(updatedTxn)
                quickCategoryTxnState.value = null
            }
        )
    }

    // ── Detail / Edit Sheet ──────────────────────────────────────────────────
    selectedTxnState.value?.let { txn ->
        TransactionDetailSheet(
            txn = txn,
            onDismiss = { selectedTxnState.value = null },
            onSave = { updated ->
                viewModel.saveTransaction(updated)
                selectedTxnState.value = null
            },
            onDelete = { toDelete ->
                viewModel.deleteTransaction(toDelete)
                selectedTxnState.value = null
            },
            onNavigateToTransaction = { id ->
                scope.launch {
                    val target = db.smsTransactionDao().getById(id)
                    selectedTxnState.value = target
                }
            }
        )
    }

    // ── Date Range Picker Dialog ──────────────────────────────────────────────
    if (showDatePickerState.value) {
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = uiState.startDateMillis,
            initialSelectedEndDateMillis = uiState.endDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerState.value = false },
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            confirmButton = {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.startDateMillis != null) {
                        TextButton(
                            onClick = {
                                viewModel.setDateRange(null, null)
                                showDatePickerState.value = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Clear") }
                    }
                    TextButton(onClick = {
                        viewModel.setDateRange(
                            start = dateRangePickerState.selectedStartDateMillis,
                            end = dateRangePickerState.selectedEndDateMillis
                        )
                        showDatePickerState.value = false
                    }) { Text("Apply") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerState.value = false }) { Text("Cancel") }
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
}

// ── Donut Chart Component ────────────────────────────────────────────────────

@Composable
private fun CategoryDonutChart(
    summaries: List<CategorySummary>,
    customCategories: List<app.ledgerpop.data.local.CustomCategoryEntity>,
    viewType: app.ledgerpop.ui.state.AnalyticsViewType = app.ledgerpop.ui.state.AnalyticsViewType.SPENDS,
    onCategoryClick: (String) -> Unit
) {
    if (summaries.isEmpty()) return

    val locale = LocalConfiguration.current.locales[0]
    var expanded by remember { mutableStateOf(false) }
    val isMidnight = MaterialTheme.colorScheme.primary == MidnightPrimary
    val displaySummaries = if (expanded) summaries else summaries.take(7)
    
    val colors = remember(viewType, isMidnight) {
        val baseColors = if (viewType == app.ledgerpop.ui.state.AnalyticsViewType.INCOME) INCOME_COLORS else SPENDS_COLORS
        if (isMidnight) {
            val midnightAccent = Color(0xFF003FA4) // Midnight Navy
            listOf(midnightAccent) + baseColors.drop(1)
        } else {
            baseColors
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Donut Chart (Left - 70%)
                Box(
                    modifier = Modifier.weight(0.7f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .pointerInput(summaries) {
                                detectTapGestures { offset ->
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val angle = (atan2(offset.y - center.y, offset.x - center.x) * 180 / Math.PI).toFloat()
                                    val normalizedAngle = (angle + 90 + 360) % 360

                                    var currentAngle = 0f
                                    summaries.forEach { summary ->
                                        val sweepAngle = (summary.percentage / 100f) * 360f
                                        if (normalizedAngle >= currentAngle && normalizedAngle < currentAngle + sweepAngle) {
                                            onCategoryClick(summary.category)
                                            return@detectTapGestures
                                        }
                                        currentAngle += sweepAngle
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            var startAngle = -90f
                            summaries.forEachIndexed { index, summary ->
                                val sweepAngle = (summary.percentage / 100f) * 360f
                                drawArc(
                                    color = colors[index % colors.size],
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = false,
                                    style = Stroke(
                                        width = 32.dp.toPx(),
                                        cap = StrokeCap.Butt
                                    )
                                )
                                startAngle += sweepAngle
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Total",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val totalAmount = remember(summaries) { summaries.sumOf { it.amount } }
                            val formattedTotal = remember(totalAmount) {
                                "${if (totalAmount < 0) "−" else ""}₹${AmountUtils.formatAmount(totalAmount)}"
                            }
                            Text(
                                text = formattedTotal,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.width(16.dp))

                // Legend (Right - 30%)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(0.3f)
                ) {
                    displaySummaries.forEachIndexed { index, summary ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onCategoryClick(summary.category) }
                                .padding(vertical = 1.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(colors[index % colors.size], RoundedCornerShape(2.dp))
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = CategoryEngine.emoji(summary.category, customCategories),
                                    fontSize = 10.sp
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = summary.category,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                text = "${summary.percentage.toInt()}%",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (summaries.size > 7) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (expanded) "Show less" else "Show more",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

// ── Components ───────────────────────────────────────────────────────────────

@Composable
private fun KpiCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable { onClick() } else Modifier
        ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CategoryRow(
    summary: CategorySummary,
    customCategories: List<app.ledgerpop.data.local.CustomCategoryEntity> = emptyList(),
    viewType: app.ledgerpop.ui.state.AnalyticsViewType = app.ledgerpop.ui.state.AnalyticsViewType.SPENDS,
    onClick: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val amountColor = if (viewType == app.ledgerpop.ui.state.AnalyticsViewType.SPENDS)
        MaterialTheme.colorScheme.onSurface
    else
        Color(0xFF00B894)

    val labelText = if (viewType == app.ledgerpop.ui.state.AnalyticsViewType.SPENDS)
        "of expenses"
    else
        "of income"

    val formattedAmount = remember(summary.amount) {
        "${if (summary.amount < 0) "−" else ""}₹${AmountUtils.formatAmount(summary.amount)}"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(CategoryEngine.emoji(summary.category, customCategories), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(summary.category, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(formattedAmount, style = MaterialTheme.typography.bodyMedium, color = amountColor)
                }
                Spacer(Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth((summary.percentage / 100f).coerceIn(0f, 1f)).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
                }
                Spacer(Modifier.height(4.dp))
                Text("${summary.percentage.toInt()}% $labelText", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TransactionRowCompact(
    txn: SmsTransactionEntity,
    customCategories: List<app.ledgerpop.data.local.CustomCategoryEntity> = emptyList(),
    linkedAmount: Double = 0.0,
    hasCreditLinks: Boolean = false,
    onClick: () -> Unit,
    onCategoryClick: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val isDebit = txn.type == "DEBIT"
    val isBillable = txn.isBillable
    val amountColor = if (isDebit) MaterialTheme.colorScheme.onSurface else Color(0xFF00B894)
    val time = remember(txn.transactionTime, locale) {
        SimpleDateFormat("d MMM, h:mm a", locale).format(Date(txn.transactionTime))
    }

    val hasLinks = remember(isDebit, linkedAmount, hasCreditLinks) {
        if (isDebit) linkedAmount > 0
        else hasCreditLinks
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .alpha(if (isBillable) 1f else 0.45f)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isBillable) amountColor.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable { onCategoryClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = CategoryEngine.emoji(txn.category, customCategories),
                fontSize = 16.sp
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = txn.merchant.ifBlank { txn.sender },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (txn.category.isNotBlank()) {
                    Text(
                        text = " · ${txn.category}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (txn.note.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = txn.note.take(50),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            val formattedAmount = remember(txn.amount) {
                "${if (txn.amount < 0) "−" else ""}₹${AmountUtils.formatAmount(txn.amount)}"
            }
            Text(
                text = formattedAmount,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isBillable) amountColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (txn.originalAmount != null) {
                val formattedOriginal = remember(txn.originalAmount) {
                    "₹${AmountUtils.formatAmount(txn.originalAmount)}"
                }
                Text(
                    text = formattedOriginal,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    ),
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 9.sp
                )
            } else if (hasLinks && !isDebit) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Link,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "Linked",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp
                    )
                }
            }

            if (txn.note.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = txn.note.take(50),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

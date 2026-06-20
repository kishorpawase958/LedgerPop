package app.ledgerpop.screens.analytics

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ledgerpop.ui.components.ScrollableBarChart
import app.ledgerpop.ui.components.SpeedDialFab
import app.ledgerpop.ui.components.SpeedDialAction
import app.ledgerpop.ui.components.FloatingFilterPopup
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Close
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
    val listState = rememberLazyListState()
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }
    
    val isMidnight = MaterialTheme.colorScheme.primary == MidnightPrimary
    val accentColor = if (isMidnight) MaterialTheme.colorScheme.primaryContainer else Purple700
    val accentLight = if (isMidnight) MaterialTheme.colorScheme.primary else Purple500

    val drillDownData by viewModel.drillDownTransactions.collectAsState()
    val drillDownTitle by viewModel.drillDownTitle.collectAsState()
    val density = LocalDensity.current
    
    // FAB and Action Offsets (Matching SpeedDialFab layout)
    // To align next to the main FAB '+', we use the FAB's vertical center as our anchor
    val fabCenterYOffset = with(density) { (-(135 + 28)).dp.roundToPx() }
    val popupYOffset = fabCenterYOffset

    val selectedTxnState = remember { mutableStateOf<SmsTransactionEntity?>(null) }
    val quickCategoryTxnState = remember { mutableStateOf<SmsTransactionEntity?>(null) }

    val showDatePickerState = remember { mutableStateOf(false) }
    val isBreakdownExpanded = remember { mutableStateOf(false) }

    var isFabExpanded by remember { mutableStateOf(false) }
    var showAccountFilterPopup by remember { mutableStateOf(false) }
    var showCategoryFilterPopup by remember { mutableStateOf(false) }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 220.dp)
        ) {
            // ── Header ───────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Analytics",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    // Small filter status row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.selectedAccount != "All") {
                            FilterStatusChip(uiState.selectedAccount) { viewModel.setAccountFilter("All") }
                        }
                        if (uiState.selectedCategory != "All") {
                            FilterStatusChip(uiState.selectedCategory) { viewModel.setCategoryFilter("All") }
                        }
                        if (uiState.startDateMillis != null) {
                            FilterStatusChip("Date") { viewModel.setDateRange(null, null) }
                        }
                    }
                }
            }

            // ── KPIs (Clickable) ──────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KpiCard(
                        label = "Income",
                        value = AmountUtils.formatWithCurrency(uiState.totalIncome),
                        color = Color(0xFF00B894),
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.openDrillDown(DrillDownType.Income) }
                    )
                    KpiCard(
                        label = "Expenses",
                        value = AmountUtils.formatWithCurrency(uiState.totalExpense),
                        color = accentLight,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.openDrillDown(DrillDownType.Expense) }
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            item {
                val netColor = when {
                    uiState.net > 0 -> Color(0xFF00B894)
                    uiState.net < 0 -> MaterialTheme.colorScheme.error
                    else -> Color.White
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KpiCard("Net", AmountUtils.formatWithCurrency(uiState.net), netColor, Modifier.weight(1f))
                    KpiCard("Avg Credit", AmountUtils.formatWithCurrency(uiState.avgCredit), Color(0xFF00B894), Modifier.weight(1f))
                    KpiCard("Avg Debit", AmountUtils.formatWithCurrency(uiState.avgDebit), color = accentLight, Modifier.weight(1f))
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

        // ── Scroll to Top Button ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 210.dp)
                .width(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                    containerColor = if (isMidnight) MaterialTheme.colorScheme.primaryContainer else Purple700,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Scroll to top", modifier = Modifier.size(20.dp))
                }
            }
        }

        // ── SpeedDial FAB ─────────────────────────────────────────────────────────────
        SpeedDialFab(
            isExpanded = isFabExpanded,
            onExpandedChange = { isFabExpanded = it },
            actions = listOf(
                SpeedDialAction(
                    icon = Icons.Rounded.Download,
                    label = "Export",
                    onClick = { viewModel.exportToCsv(context) }
                ),
                SpeedDialAction(
                    icon = Icons.Rounded.DateRange,
                    label = "Date",
                    onClick = { showDatePickerState.value = true }
                ),
                SpeedDialAction(
                    icon = Icons.Rounded.AccountBalanceWallet,
                    label = "Account",
                    onClick = { showAccountFilterPopup = true }
                ),
                SpeedDialAction(
                    icon = Icons.Rounded.Category,
                    label = "Category",
                    onClick = { showCategoryFilterPopup = true }
                )
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 135.dp)
        )
    }

    if (showAccountFilterPopup) {
        FloatingFilterPopup(
            title = "Filter Account",
            options = uiState.availableAccounts,
            selected = uiState.selectedAccount,
            onSelect = { viewModel.setAccountFilter(it) },
            onDismiss = { showAccountFilterPopup = false },
            yOffset = popupYOffset
        )
    }

    if (showCategoryFilterPopup) {
        FloatingFilterPopup(
            title = "Filter Category",
            options = uiState.availableCategories,
            selected = uiState.selectedCategory,
            onSelect = { viewModel.setCategoryFilter(it) },
            onDismiss = { showCategoryFilterPopup = false },
            emojiProvider = { CategoryEngine.emoji(it, uiState.customCategories) },
            yOffset = popupYOffset
        )
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

                    val monthHeaderFormatter = remember(locale) { SimpleDateFormat("MMMM yyyy", locale) }
                    val groupedDrillDown = remember(data, monthHeaderFormatter) {
                        data.groupBy { monthHeaderFormatter.format(Date(it.transactionTime)) }
                    }

                    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                        groupedDrillDown.forEach { (month, transactions) ->
                            item(key = month) {
                                Text(
                                    text = month,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(horizontal = 20.dp)
                                        .padding(top = 16.dp, bottom = 8.dp)
                                )
                            }

                            items(
                                items = transactions,
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

@Composable
private fun FilterStatusChip(
    text: String,
    onClear: () -> Unit
) {
    Surface(
        onClick = onClear,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 80.dp)
            )
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Clear",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

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
                            Text(
                                text = AmountUtils.formatWithCurrency(totalAmount),
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
                    Text(AmountUtils.formatWithCurrency(summary.amount), style = MaterialTheme.typography.bodyMedium, color = amountColor)
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
            Text(
                text = AmountUtils.formatWithCurrency(txn.amount),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isBillable) amountColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (txn.originalAmount != null) {
                Text(
                    text = AmountUtils.formatWithCurrency(txn.originalAmount),
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

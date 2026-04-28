package app.ledgerpop.screens.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ledgerpop.data.category.CategoryEngine
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.ui.state.CategorySummary
import app.ledgerpop.ui.state.GroupingType
import app.ledgerpop.ui.state.TrendSummary
import app.ledgerpop.ui.viewmodel.AnalyticsViewModel
import app.ledgerpop.ui.viewmodel.DrillDownType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen() {
    val context = LocalContext.current
    val db = remember { LedgerPopDatabase.getInstance(context) }
    val viewModel: AnalyticsViewModel = viewModel(factory = AnalyticsViewModel.factory(db))
    val uiState by viewModel.uiState.collectAsState()

    val drillDownData by viewModel.drillDownTransactions.collectAsState()
    val drillDownTitle by viewModel.drillDownTitle.collectAsState()

    var showFilters by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

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
            contentPadding = PaddingValues(bottom = 96.dp)
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
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(
                        onClick = { showFilters = !showFilters },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (showFilters || uiState.startDateMillis != null || uiState.selectedCategory != "All" || uiState.selectedAccount != "All")
                                MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                    ) {
                        Icon(
                            Icons.Rounded.FilterList,
                            contentDescription = "Filters",
                            tint = if (showFilters || uiState.startDateMillis != null)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Expandable Filter Section ─────────────────────────────────────
            item {
                AnimatedVisibility(
                    visible = showFilters,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Filters",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Clear All",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { viewModel.clearFilters() }
                                    .padding(4.dp)
                            )
                        }

                        // Date Range Button
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
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
                            Text(dateText)
                        }

                        // Group By Segmented Button
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = uiState.groupBy == GroupingType.DAILY,
                                onClick = { viewModel.setGroupingType(GroupingType.DAILY) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                            ) { Text("Daily") }
                            SegmentedButton(
                                selected = uiState.groupBy == GroupingType.WEEKLY,
                                onClick = { viewModel.setGroupingType(GroupingType.WEEKLY) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                            ) { Text("Weekly") }
                            SegmentedButton(
                                selected = uiState.groupBy == GroupingType.MONTHLY,
                                onClick = { viewModel.setGroupingType(GroupingType.MONTHLY) },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                            ) { Text("Monthly") }
                        }

                        // Dropdowns for Category and Account
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            var catExpanded by remember { mutableStateOf(false) }
                            var accExpanded by remember { mutableStateOf(false) }

                            ExposedDropdownMenuBox(
                                expanded = catExpanded,
                                onExpandedChange = { catExpanded = !catExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = uiState.selectedCategory,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Category") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                                    modifier = Modifier.menuAnchor(),
                                    shape = RoundedCornerShape(12.dp),
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                                ExposedDropdownMenu(
                                    expanded = catExpanded,
                                    onDismissRequest = { catExpanded = false }
                                ) {
                                    uiState.availableCategories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat) },
                                            onClick = {
                                                viewModel.setCategoryFilter(cat)
                                                catExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            ExposedDropdownMenuBox(
                                expanded = accExpanded,
                                onExpandedChange = { accExpanded = !accExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = uiState.selectedAccount,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Account") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accExpanded) },
                                    modifier = Modifier.menuAnchor(),
                                    shape = RoundedCornerShape(12.dp),
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                                ExposedDropdownMenu(
                                    expanded = accExpanded,
                                    onDismissRequest = { accExpanded = false }
                                ) {
                                    uiState.availableAccounts.forEach { acc ->
                                        DropdownMenuItem(
                                            text = { Text(acc) },
                                            onClick = {
                                                viewModel.setAccountFilter(acc)
                                                accExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── KPIs (Clickable) ──────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KpiCard(
                        label = "Income",
                        value = "₹${"%,.0f".format(uiState.totalIncome)}",
                        color = Color(0xFF00B894),
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.openDrillDown(DrillDownType.Income) }
                    )
                    KpiCard(
                        label = "Expenses",
                        value = "₹${"%,.0f".format(uiState.totalExpense)}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.openDrillDown(DrillDownType.Expense) }
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KpiCard("Net", "₹${"%,.0f".format(uiState.net)}", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    KpiCard("Avg Debit", "₹${"%,.0f".format(uiState.avgDebit)}", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                }
                Spacer(Modifier.height(24.dp))
            }

            // ── Scrollable Vertical Bar Chart ─────────────────────────────────
            if (uiState.trendSummaries.isNotEmpty()) {
                item {
                    Text(
                        "${uiState.groupBy.name.lowercase().replaceFirstChar { it.uppercase() }} Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )

                    ScrollableBarChart(
                        summaries = uiState.trendSummaries,
                        onBarClick = { label -> viewModel.openDrillDown(DrillDownType.Trend(label)) }
                    )

                    Spacer(Modifier.height(16.dp))
                }
            }

            // ── Category Breakdown (Clickable) ────────────────────────────────
            if (uiState.categoryBreakdown.isNotEmpty()) {
                item {
                    Text(
                        "Spending by Category",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                items(uiState.categoryBreakdown) { summary ->
                    CategoryRow(
                        summary = summary,
                        onClick = { viewModel.openDrillDown(DrillDownType.Category(summary.category)) }
                    )
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
        ExtendedFloatingActionButton(
            onClick = { viewModel.exportToCsv(context) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
            text = { Text("Export CSV") },
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }

    // ── Drill Down Bottom Sheet ───────────────────────────────────────────────
    if (drillDownData != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeDrillDown() },
            modifier = Modifier.fillMaxHeight(0.9f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = drillDownTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
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
                    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                        // FIX: Provide a fallback emptyList() to prevent NPE during dismiss animation
                        items(drillDownData ?: emptyList()) { txn ->
                            TransactionRowCompact(txn)
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Date Range Picker Dialog ──────────────────────────────────────────────
    if (showDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = uiState.startDateMillis,
            initialSelectedEndDateMillis = uiState.endDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
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
                fontWeight = FontWeight.Bold,
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
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(CategoryEngine.emoji(summary.category), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(summary.category, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text("₹${"%,.0f".format(summary.amount)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth((summary.percentage / 100f).coerceIn(0f, 1f)).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
                }
                Spacer(Modifier.height(4.dp))
                Text("${summary.percentage.toInt()}% of expenses", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ScrollableBarChart(
    summaries: List<TrendSummary>,
    onBarClick: (String) -> Unit
) {
    val maxAmount = maxOf(
        summaries.maxOfOrNull { it.income } ?: 0.0,
        summaries.maxOfOrNull { it.expense } ?: 0.0,
        1.0 // Prevent division by zero
    ).toFloat()

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
            items(summaries) { summary ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onBarClick(summary.label) }
                ) {
                    Canvas(modifier = Modifier
                        .height(140.dp)
                        .width(40.dp)
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
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionRowCompact(txn: SmsTransactionEntity) {
    val isDebit = txn.type == "DEBIT"
    val isBillable = txn.isBillable
    val amountColor = if (isDebit) MaterialTheme.colorScheme.error else Color(0xFF00B894)
    val time = remember(txn.transactionTime) {
        SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(txn.transactionTime))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isDebit) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                contentDescription = null,
                tint = if (isBillable) amountColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = txn.merchant.ifBlank { txn.sender },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
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
        }

        Text(
            text = "${if (isDebit) "−" else "+"} ₹${"%,.0f".format(txn.amount)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isBillable) amountColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
package app.ledgerpop.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ledgerpop.data.category.CategoryEngine
import app.ledgerpop.data.local.CustomCategoryEntity
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.screens.transactions.AddTransactionDialog
import app.ledgerpop.screens.transactions.TransactionDetailScreen
import app.ledgerpop.ui.components.BulkUpdateDialog
import app.ledgerpop.ui.theme.MidnightPrimary
import app.ledgerpop.ui.theme.Purple700
import app.ledgerpop.ui.viewmodel.CategoryAggregate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import app.ledgerpop.ui.viewmodel.IncomeBenchmark
import app.ledgerpop.ui.viewmodel.HomeViewModel
import app.ledgerpop.utils.AmountUtils
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onNavigateToTransactions: () -> Unit = {}
) {
    val context = LocalContext.current
    val db = remember { LedgerPopDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(db, context))
    val uiState by viewModel.uiState.collectAsState()
    val isMidnight = MaterialTheme.colorScheme.primary == MidnightPrimary
    val accentColor = if (isMidnight) MaterialTheme.colorScheme.primaryContainer else Purple700
    val hazeState = remember { HazeState() }

    val customCategories by produceState(initialValue = emptyList<CustomCategoryEntity>()) {
        db.customCategoryDao().getAllCategories().collect { value = it }
    }

    var selectedTxn by remember { mutableStateOf<SmsTransactionEntity?>(null) }
    var quickCategoryTxn by remember { mutableStateOf<SmsTransactionEntity?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 3.dp)
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .hazeSource(state = hazeState)
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 150.dp)
        ) {
            // ── Header
            item {
                HomeHeader(uiState.userName)
            }

            // ── Balance Card
            item {
                val baselineIncome = if (uiState.incomeBenchmark == IncomeBenchmark.PREVIOUS_MONTH && uiState.lastMonthIncome > 0) {
                    uiState.lastMonthIncome
                } else {
                    uiState.thisMonthIncome
                }
                val displayBalance = if (uiState.monthlyBudget > 0) {
                    uiState.monthlyBudget - uiState.thisMonthExpense
                } else {
                    baselineIncome - uiState.thisMonthExpense
                }
                BalanceRingCard(
                    totalBalance = displayBalance,
                    totalIncome = uiState.thisMonthIncome,
                    totalExpense = uiState.thisMonthExpense,
                    lastMonthIncome = uiState.lastMonthIncome,
                    incomeBenchmark = uiState.incomeBenchmark,
                    monthlyBudget = uiState.monthlyBudget,
                    accentColor = accentColor,
                    onUpdateBudget = { viewModel.updateBudget(it) },
                    onUpdateBenchmark = { viewModel.updateIncomeBenchmark(it) }
                )
            }
            // ── Comparison
            item {
                MonthCompareCard(
                    thisMonth = uiState.thisMonthExpense,
                    lastMonth = uiState.lastMonthExpense
                )
                Spacer(Modifier.height(24.dp))
            }
            // ── Insights Row (Horizontal)
            if (uiState.insights.isNotEmpty()) {
                item {
                    SectionHeader(title = "Insights", action = null)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uiState.insights) { insight ->
                            InsightCard(insight.icon, insight.title, insight.message)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            // ── Top Categories
            if (uiState.topCategories.isNotEmpty()) {
                item {
                    SectionHeader(title = "Top Spending Categories", action = null)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        uiState.topCategories.forEach { aggregate ->
                            CategoryCompactCard(
                                aggregate = aggregate,
                                customCategories = customCategories,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }


            // ── Recent Transactions
            item {
                SectionHeader(
                    title = "Recent Transactions",
                    action = "View All",
                    onActionClick = onNavigateToTransactions
                )
            }

            val recentTransactions = uiState.recentTransactions.take(3)
            if (recentTransactions.isNotEmpty()) {
                items(recentTransactions) { txn ->
                    HomeTransactionRow(
                        txn = txn,
                        customCategories = customCategories,
                        onClick = { selectedTxn = txn },
                        onCategoryClick = { quickCategoryTxn = txn }
                    )
                }
            } else {
                item {
                    EmptyTransactionsPlaceholder()
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 135.dp),
            containerColor = accentColor,
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

    // ── Dialogs & Sheets ──────────────────────────────────────────────────────

    quickCategoryTxn?.let { txn ->
        QuickCategoryUpdateDialog(
            txn = txn,
            customCategories = customCategories,
            onDismiss = { quickCategoryTxn = null },
            onSave = { updatedTxn ->
                viewModel.saveTransaction(updatedTxn)
                quickCategoryTxn = null
            },
            onUpdateEmoji = { name, emoji ->
                scope.launch {
                    val existing = db.customCategoryDao().getByName(name)
                    if (existing != null) {
                        db.customCategoryDao().insert(existing.copy(emoji = emoji))
                    } else {
                        db.customCategoryDao().insert(
                            CustomCategoryEntity(
                                name = name,
                                type = txn.type,
                                emoji = emoji
                            )
                        )
                    }
                }
            }
        )
    }

    selectedTxn?.let { txn ->
        TransactionDetailScreen(
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
                    if (target != null) {
                        selectedTxn = target
                    }
                }
            }
        )
    }

    if (showAddSheet) {
        AddTransactionDialog(
            onDismiss = { showAddSheet = false },
            onAdd = { newTxn -> viewModel.addTransaction(newTxn) }
        )
    }
}

// ── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun HomeHeader(userName: String) {
    val currentMonthYear = remember {
        SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date())
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = greeting(userName),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Monthly Overview, $currentMonthYear",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String?,
    onActionClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (action != null) {
            TextButton(onClick = onActionClick) {
                Text(action)
            }
        }
    }
}

@Composable
private fun InsightCard(emoji: String, title: String, message: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .width(220.dp)
            .height(90.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun CategoryCompactCard(
    aggregate: CategoryAggregate,
    customCategories: List<CustomCategoryEntity>,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .height(130.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Large background icon/emoji
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = CategoryEngine.emoji(aggregate.category, customCategories),
                    fontSize = 50.sp,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .alpha(0.5f)
                )
            }

            // Text Overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = aggregate.category.uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Text(
                    text = "₹${formatAmount(aggregate.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BalanceRingCard(
    totalBalance: Double,
    totalIncome: Double,
    totalExpense: Double,
    lastMonthIncome: Double,
    incomeBenchmark: IncomeBenchmark,
    monthlyBudget: Double,
    accentColor: Color,
    onUpdateBudget: (Double) -> Unit,
    onUpdateBenchmark: (IncomeBenchmark) -> Unit
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    val baselineIncome = if (incomeBenchmark == IncomeBenchmark.PREVIOUS_MONTH && lastMonthIncome > 0) lastMonthIncome else totalIncome
    val effectiveBudget = if (monthlyBudget > 0) monthlyBudget else baselineIncome
    val spendRatio = if (effectiveBudget > 0) (totalExpense / effectiveBudget).toFloat() else 0f

    val animatedSweep by animateFloatAsState(
        targetValue = if (started) (spendRatio * 360f) else 0f,
        animationSpec = tween(1200),
        label = "spend_sweep"
    )

    val redColor = Color(0xFFD63031)
    val surfaceColor = MaterialTheme.colorScheme.surface
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showBenchmarkMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        color = surfaceColor,
        tonalElevation = 2.dp,
        border = BorderStroke(
            1.dp,
            accentColor.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ring (Left 50%)
                Box(
                    modifier = Modifier
                        .weight(0.5f)
                        .aspectRatio(1f)
                        .drawBehind {
                            val strokeWidth = 14.dp.toPx()
                            val inset = strokeWidth / 2
                            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                            val topLeft = Offset(inset, inset)
                            val startAngle = -90f

                            // 1. Background Track (Dynamic White/Black)
                            drawArc(
                                color = if (surfaceColor.luminance() < 0.5f)
                                    Color.White.copy(alpha = 0.08f)
                                else
                                    Color.Black.copy(alpha = 0.08f),
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(strokeWidth)
                            )

                            // 2. Spend Progress
                            if (animatedSweep <= 360f) {
                                // Within budget: accentColor arc
                                drawArc(
                                    color = accentColor,
                                    startAngle = startAngle,
                                    sweepAngle = animatedSweep,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                                )
                            } else {
                                // Over budget: Full accentColor ring + Red overflow
                                drawArc(
                                    color = accentColor,
                                    startAngle = startAngle,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(strokeWidth)
                                )
                                drawArc(
                                    color = redColor,
                                    startAngle = startAngle,
                                    sweepAngle = (animatedSweep - 360f).coerceAtMost(360f),
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                                )
                            }

                            // 3. Pointers
                            val centerX = size.width / 2
                            val centerY = size.height / 2
                            val radius = (size.width - strokeWidth) / 2

                            // Budget Pointer (100% Mark at the Top)
                            if (effectiveBudget > 0) {
                                val budgetAngleRad = Math.toRadians(startAngle.toDouble())
                                val bx = centerX + radius * Math.cos(budgetAngleRad).toFloat()
                                val by = centerY + radius * Math.sin(budgetAngleRad).toFloat()

                                // Vertical-ish notch for budget
                                drawCircle(
                                    color = Color.Black,
                                    radius = 3.dp.toPx(),
                                    center = Offset(bx, by)
                                )
                            }

                            // Current Spend Pointer
                            if (animatedSweep > 0) {
                                val currentAngleRad = Math.toRadians((startAngle + animatedSweep).toDouble())
                                val sx = centerX + radius * Math.cos(currentAngleRad).toFloat()
                                val sy = centerY + radius * Math.sin(currentAngleRad).toFloat()

                                drawCircle(
                                    color = Color.White,
                                    radius = 6.dp.toPx(),
                                    center = Offset(sx, sy)
                                )
                                drawCircle(
                                    color = if (animatedSweep > 360f) redColor else accentColor,
                                    radius = 4.dp.toPx(),
                                    center = Offset(sx, sy)
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (totalBalance >= 0) "Available" else "Over",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = AmountUtils.formatAmount(totalBalance),
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (totalBalance < 0) redColor else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(Modifier.width(24.dp))

                // Labels (Right 50%)
                Column(
                    modifier = Modifier.weight(0.5f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Box(contentAlignment = Alignment.CenterEnd) {
                            Text(
                                text = if (incomeBenchmark == IncomeBenchmark.PREVIOUS_MONTH) "Income Last Month" else "Income this Month",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                modifier = Modifier.clickable { showBenchmarkMenu = true }
                            )
                            DropdownMenu(
                                expanded = showBenchmarkMenu,
                                onDismissRequest = { showBenchmarkMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Income this Month") },
                                    onClick = {
                                        onUpdateBenchmark(IncomeBenchmark.CURRENT_MONTH)
                                        showBenchmarkMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Income Last Month") },
                                    onClick = {
                                        onUpdateBenchmark(IncomeBenchmark.PREVIOUS_MONTH)
                                        showBenchmarkMenu = false
                                    }
                                )
                            }
                        }
                        Text(
                            AmountUtils.formatWithCurrency(if (incomeBenchmark == IncomeBenchmark.PREVIOUS_MONTH) lastMonthIncome else totalIncome),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF00B894),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "Spent this Month",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            AmountUtils.formatWithCurrency(totalExpense),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "Monthly Budget",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (monthlyBudget > 0) AmountUtils.formatWithCurrency(monthlyBudget) else "Not Set",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { showBudgetDialog = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = "Edit Budget",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBudgetDialog) {
        var tempBudget by remember { mutableStateOf(if (monthlyBudget > 0) monthlyBudget.toString() else "") }
        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            title = { Text("Set Monthly Budget") },
            text = {
                OutlinedTextField(
                    value = tempBudget,
                    onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) tempBudget = it },
                    label = { Text("Budget Amount") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateBudget(tempBudget.toDoubleOrNull() ?: 0.0)
                    showBudgetDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MonthCompareCard(thisMonth: Double, lastMonth: Double) {
    val diff = if (lastMonth > 0)
        ((thisMonth - lastMonth) / lastMonth * 100).toInt()
    else 0
    val isUp = diff > 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "VS LAST MONTH",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isUp)
                            Icons.AutoMirrored.Rounded.TrendingUp
                        else
                            Icons.AutoMirrored.Rounded.TrendingDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isUp) Color(0xFFD63031) else Color(0xFF00B894)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${if (isUp) "+" else ""}$diff%",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isUp) Color(0xFFD63031) else Color(0xFF00B894)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    AmountUtils.formatWithCurrency(lastMonth),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "PREVIOUS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun HomeTransactionRow(
    txn: SmsTransactionEntity,
    customCategories: List<CustomCategoryEntity> = emptyList(),
    onClick: () -> Unit,
    onCategoryClick: () -> Unit
) {
    val isDebit = txn.type == "DEBIT"
    val isBillable = txn.isBillable
    val time = SimpleDateFormat("d MMM, h:mm a", LocalLocale.current.platformLocale)
        .format(Date(txn.transactionTime))
    val amountColor = if (isDebit)
        MaterialTheme.colorScheme.onSurface
    else
        Color(0xFF00B894)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .alpha(if (isBillable) 1f else 0.45f)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isBillable) amountColor.copy(alpha = 0.08f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable { onCategoryClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = CategoryEngine.emoji(txn.category, customCategories),
                fontSize = 20.sp,
                modifier = Modifier.alpha(if (isBillable) 1f else 0.5f)
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = txn.merchant.ifBlank { txn.sender },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$time · ${txn.category.ifBlank { "Uncategorized" }}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = if (isBillable) amountColor
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isBillable) {
                Text(
                    text = "excluded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            } else if (txn.originalAmount != null) {
                Text(
                    text = AmountUtils.formatWithCurrency(txn.originalAmount),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    ),
                    color = MaterialTheme.colorScheme.outline
                )
            } else if (!isDebit && txn.linkedTransactionId != null) {
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

@Composable
private fun EmptyTransactionsPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.Inbox,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No transactions yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun greeting(userName: String): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val base = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else      -> "Good evening"
    }
    return "$base, $userName 👋"
}

private fun formatAmount(amount: Double): String {
    return AmountUtils.formatAmount(amount)
}


// ── Quick Category Update Dialog ───────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickCategoryUpdateDialog(
    txn: SmsTransactionEntity,
    customCategories: List<CustomCategoryEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (SmsTransactionEntity) -> Unit,
    onUpdateEmoji: ((String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val db = remember { LedgerPopDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    var selectedCategory by remember { mutableStateOf(txn.category.ifBlank { CategoryEngine.OTHER }) }
    var editingEmojiFor by remember { mutableStateOf<String?>(null) }
    
    var showBulkUpdateDialog by remember { mutableStateOf(false) }
    var similarTxnsToUpdate by remember { mutableStateOf<List<SmsTransactionEntity>>(emptyList()) }
    var pendingSavedTxn by remember { mutableStateOf<SmsTransactionEntity?>(null) }

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
                text = "Change Category",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Text(
                    text = "Pick a category for ${txn.merchant}",
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
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = CategoryEngine.emoji(cat, customCategories),
                                        modifier = Modifier.clickable { editingEmojiFor = cat }
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(cat)
                                }
                            },
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
                onClick = {
                    val updated = txn.copy(category = selectedCategory)
                    if (txn.merchant.isNotBlank() && txn.merchant.length >= 3) {
                        scope.launch {
                            val similar = db.smsTransactionDao().getSimilarTransactions(txn.merchant, txn.id)
                            val filtered = similar.filter { it.category != selectedCategory }
                            if (filtered.isNotEmpty()) {
                                pendingSavedTxn = updated
                                similarTxnsToUpdate = filtered
                                showBulkUpdateDialog = true
                            } else {
                                onSave(updated)
                            }
                        }
                    } else {
                        onSave(updated)
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(28.dp)
    )

    editingEmojiFor?.let { categoryName ->
        var tempEmoji by remember { mutableStateOf(customCategories.find { it.name == categoryName }?.emoji ?: CategoryEngine.emoji(categoryName, customCategories)) }
        AlertDialog(
            onDismissRequest = { editingEmojiFor = null },
            title = { Text("Edit Emoji for $categoryName") },
            text = {
                OutlinedTextField(
                    value = tempEmoji,
                    onValueChange = { tempEmoji = it },
                    label = { Text("Emoji") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateEmoji?.invoke(categoryName, tempEmoji)
                    editingEmojiFor = null
                }) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingEmojiFor = null }) { Text("Cancel") }
            }
        )
    }

    if (showBulkUpdateDialog && pendingSavedTxn != null) {
        BulkUpdateDialog(
            newMerchantName = txn.merchant,
            newCategory = selectedCategory,
            similarTransactions = similarTxnsToUpdate,
            onDismiss = {
                onSave(pendingSavedTxn!!)
                showBulkUpdateDialog = false
            },
            onApply = { selectedIds, updateMerchant, updateCategory ->
                scope.launch {
                    when {
                        updateMerchant && updateCategory -> db.smsTransactionDao().updateMerchantAndCategoryForIds(selectedIds, txn.merchant, selectedCategory)
                        updateMerchant -> db.smsTransactionDao().updateMerchantForIds(selectedIds, txn.merchant)
                        updateCategory -> db.smsTransactionDao().updateCategoryForIds(selectedIds, selectedCategory)
                    }
                    onSave(pendingSavedTxn!!)
                    showBulkUpdateDialog = false
                }
            }
        )
    }
}

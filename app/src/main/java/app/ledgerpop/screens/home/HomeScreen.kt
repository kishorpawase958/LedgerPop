package app.ledgerpop.screens.home

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ledgerpop.data.category.CategoryEngine
import app.ledgerpop.data.local.CustomCategoryEntity
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.screens.transactions.AddTransactionSheet
import app.ledgerpop.screens.transactions.TransactionDetailSheet
import app.ledgerpop.ui.viewmodel.HomeViewModel
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
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(db))
    val uiState by viewModel.uiState.collectAsState()

    val customCategories by produceState(initialValue = emptyList<CustomCategoryEntity>()) {
        db.customCategoryDao().getAllCategories().collect { value = it }
    }

    var selectedTxn by remember { mutableStateOf<SmsTransactionEntity?>(null) }
    var quickCategoryTxn by remember { mutableStateOf<SmsTransactionEntity?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }

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
            // ── Greeting
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = greeting(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Your Overview",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            // ── Balance ring card
            item {
                BalanceRingCard(
                    totalBalance = uiState.totalBalance,
                    totalIncome = uiState.totalIncome,
                    totalExpense = uiState.totalExpense
                )
                Spacer(Modifier.height(16.dp))
            }

            // ── Month compare
            if (uiState.thisMonthExpense > 0 || uiState.lastMonthExpense > 0) {
                item {
                    MonthCompareCard(
                        thisMonth = uiState.thisMonthExpense,
                        lastMonth = uiState.lastMonthExpense
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

            // ── Insights
            if (uiState.insights.isNotEmpty()) {
                item {
                    Text(
                        text = "Insights",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
                items(uiState.insights) { insight ->
                    InsightRow(insight.icon, insight.message)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            // ── Recent header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "See All",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onNavigateToTransactions() }
                            .padding(4.dp)
                    )
                }
            }

            // ── Transactions list
            if (uiState.recentTransactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.Inbox,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No transactions yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Go to Settings → Import SMS",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(
                    items = uiState.recentTransactions,
                    key = { it.id }
                ) { txn ->
                    HomeTransactionRow(
                        txn = txn,
                        customCategories = customCategories,
                        onClick = { selectedTxn = txn },
                        onCategoryClick = { quickCategoryTxn = txn }
                    )
                }
            }
        }

        // ── FAB
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

    // ── Detail / edit sheet
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

    // ── Add transaction sheet
    if (showAddSheet) {
        AddTransactionSheet(
            onDismiss = { showAddSheet = false },
            onAdd = { newTxn -> viewModel.addTransaction(newTxn) }
        )
    }
}

// ── Home Transaction Row ──────────────────────────────────────────────────────

@Composable
private fun HomeTransactionRow(
    txn: SmsTransactionEntity,
    customCategories: List<CustomCategoryEntity> = emptyList(),
    onClick: () -> Unit,
    onCategoryClick: () -> Unit
) {
    val isDebit = txn.type == "DEBIT"
    val isBillable = txn.isBillable
    val time = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
        .format(Date(txn.transactionTime))
    val amountColor = if (isDebit)
        MaterialTheme.colorScheme.error
    else
        Color(0xFF00B894)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .clickable { onClick() }
            .alpha(if (isBillable) 1f else 0.45f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon (Clickable for quick category update)
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    if (isBillable) amountColor.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable { onCategoryClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = CategoryEngine.emoji(txn.category, customCategories),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.alpha(if (isBillable) 1f else 0.5f)
            )
        }

        Spacer(Modifier.width(14.dp))

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

        Text(
            text = "${if (isDebit) "−" else "+"} ₹${"%,.0f".format(txn.amount)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (isBillable) amountColor
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Balance Ring Card ─────────────────────────────────────────────────────────

@Composable
private fun BalanceRingCard(
    totalBalance: Double,
    totalIncome: Double,
    totalExpense: Double
) {
    val total = totalIncome + totalExpense
    val expenseRatio = if (total > 0) (totalExpense / total).toFloat() else 0f
    val incomeRatio = 1f - expenseRatio

    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    val expenseSweep by animateFloatAsState(
        targetValue = if (started) expenseRatio * 300f else 0f,
        animationSpec = tween(900),
        label = "expense_sweep"
    )
    val incomeSweep by animateFloatAsState(
        targetValue = if (started) incomeRatio * 300f else 0f,
        animationSpec = tween(900),
        label = "income_sweep"
    )

    val errorColor = MaterialTheme.colorScheme.error
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ring
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .drawBehind {
                        val strokeWidth = 14.dp.toPx()
                        val inset = strokeWidth / 2
                        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                        val topLeft = Offset(inset, inset)

                        // Track
                        drawArc(
                            color = surfaceVariantColor.copy(alpha = 0.3f),
                            startAngle = 120f,
                            sweepAngle = 300f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(strokeWidth, cap = StrokeCap.Round)
                        )
                        // Income arc
                        drawArc(
                            color = Color(0xFF00B894),
                            startAngle = 120f,
                            sweepAngle = incomeSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(strokeWidth, cap = StrokeCap.Round)
                        )
                        // Expense arc
                        drawArc(
                            color = errorColor.copy(alpha = 0.9f),
                            startAngle = 120f + incomeSweep,
                            sweepAngle = expenseSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(strokeWidth, cap = StrokeCap.Round)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (totalBalance >= 0) "Saved" else "Over",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatAmount(kotlin.math.abs(totalBalance)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(Modifier.width(24.dp))

            // Labels
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Text(
                        "Net Balance",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                    Text(
                        "₹${formatAmount(totalBalance)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    LegendItem(
                        color = Color(0xFF00B894),
                        label = "Income",
                        value = "₹${formatAmount(totalIncome)}",
                        onPrimaryColor = MaterialTheme.colorScheme.onPrimary
                    )
                    LegendItem(
                        color = MaterialTheme.colorScheme.error,
                        label = "Spent",
                        value = "₹${formatAmount(totalExpense)}",
                        onPrimaryColor = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    value: String,
    onPrimaryColor: Color
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = onPrimaryColor.copy(alpha = 0.7f)
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = onPrimaryColor
        )
    }
}

// ── Month Compare Card ────────────────────────────────────────────────────────

@Composable
private fun MonthCompareCard(thisMonth: Double, lastMonth: Double) {
    val diff = if (lastMonth > 0)
        ((thisMonth - lastMonth) / lastMonth * 100).toInt()
    else 0
    val isUp = diff > 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "This month",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "₹${formatAmount(thisMonth)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isUp)
                    MaterialTheme.colorScheme.errorContainer
                else
                    Color(0xFF00B894).copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = if (isUp)
                            Icons.AutoMirrored.Rounded.TrendingUp
                        else
                            Icons.AutoMirrored.Rounded.TrendingDown,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isUp) MaterialTheme.colorScheme.error
                        else Color(0xFF00B894)
                    )
                    Text(
                        text = "${if (isUp) "+" else ""}$diff%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isUp) MaterialTheme.colorScheme.error
                        else Color(0xFF00B894)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "Last month",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "₹${formatAmount(lastMonth)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ── Insight Row ───────────────────────────────────────────────────────────────

@Composable
private fun InsightRow(emoji: String, message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(10.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun greeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good morning 👋"
        hour < 17 -> "Good afternoon 👋"
        else      -> "Good evening 👋"
    }
}

private fun formatAmount(amount: Double): String {
    return when {
        amount >= 100_000 -> "${"%.1f".format(amount / 100_000)}L"
        amount >= 1_000   -> "${"%.1f".format(amount / 1_000)}K"
        else              -> "%,.0f".format(amount)
    }
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
    var selectedCategory by remember { mutableStateOf(txn.category.ifBlank { CategoryEngine.OTHER }) }
    var editingEmojiFor by remember { mutableStateOf<String?>(null) }

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
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingEmojiFor = null }) { Text("Cancel") }
            }
        )
    }
}

package app.ledgerpop.screens.transactions

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ledgerpop.data.category.CategoryEngine
import app.ledgerpop.data.local.CustomCategoryEntity
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.ui.state.TransactionSortOrder
import app.ledgerpop.ui.viewmodel.TransactionsViewModel
import app.ledgerpop.ui.components.ScrollableBarChart
import app.ledgerpop.ui.components.BulkUpdateDialog
import app.ledgerpop.ui.components.SpeedDialFab
import app.ledgerpop.ui.components.SpeedDialAction
import app.ledgerpop.ui.components.FloatingFilterPopup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import app.ledgerpop.ui.theme.MidnightPrimary
import app.ledgerpop.ui.theme.Purple700
import app.ledgerpop.utils.AmountUtils
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    initialTransactionId: Int? = null,
    onClearInitialId: () -> Unit = {}
) {
    val context = LocalContext.current
    val db = remember { LedgerPopDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val viewModel: TransactionsViewModel = viewModel(
        factory = TransactionsViewModel.factory(db)
    )
    val uiState by viewModel.uiState.collectAsState()
    val isMidnight = MaterialTheme.colorScheme.primary == MidnightPrimary
    val filtered = uiState.filteredTransactions
    val listState = rememberLazyListState()
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }

    val locale = LocalConfiguration.current.locales[0]
    val monthHeaderFormatter = remember(locale) { SimpleDateFormat("MMMM yyyy", locale) }
    val groupedTransactions = remember(filtered, monthHeaderFormatter) {
        filtered.groupBy { monthHeaderFormatter.format(Date(it.transactionTime)) }
    }

    var selectedTxn by remember { mutableStateOf<SmsTransactionEntity?>(null) }
    var quickCategoryTxn by remember { mutableStateOf<SmsTransactionEntity?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(false) }
    var showAccountFilterPopup by remember { mutableStateOf(false) }
    var showCategoryFilterPopup by remember { mutableStateOf(false) }
    var showSortPopup by remember { mutableStateOf(false) }

    // Track which ID was last handled to avoid re-opening on tab switches
    var lastHandledId by rememberSaveable { mutableIntStateOf(-1) }

    var selectedIds by remember { mutableStateOf(setOf<Int>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAnalyticsConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedIds.isNotEmpty()) {
        selectedIds = emptySet()
    }

    // Auto-select transaction if initialTransactionId is provided
    LaunchedEffect(initialTransactionId) {
        if (initialTransactionId != null && initialTransactionId != -1 && (initialTransactionId != lastHandledId)) {
            val txn = db.smsTransactionDao().getById(initialTransactionId)
            if (txn != null) {
                selectedTxn = txn
                lastHandledId = initialTransactionId
            }
            // Clear the ID from the navigation arguments as a backup
            onClearInitialId()
        }
    }

    val onTransactionClick = remember(selectedIds) { { txn: SmsTransactionEntity -> 
        if (selectedIds.isNotEmpty()) {
            selectedIds = if (selectedIds.contains(txn.id)) {
                selectedIds - txn.id
            } else {
                selectedIds + txn.id
            }
        } else {
            selectedTxn = txn
        }
    } }
    val onTransactionLongClick = remember { { txn: SmsTransactionEntity -> 
        selectedIds = selectedIds + txn.id
    } }
    val onQuickCategoryClick = remember { { txn: SmsTransactionEntity -> quickCategoryTxn = txn } }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    
    // FAB and Action Offsets (Matching SpeedDialFab layout)
    // To align next to the main FAB '+', we use the FAB's vertical center as our anchor
    val fabCenterYOffset = with(density) { (-(135 + 28)).dp.roundToPx() }
    
    // We'll use a consistent Y offset for all popups to keep them "next to" the FAB
    val popupYOffset = fabCenterYOffset

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
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
            ) {
                if (selectedIds.isNotEmpty()) {
                    SelectionTopBar(
                        count = selectedIds.size,
                        onClearSelection = { selectedIds = emptySet() },
                        onSelectAll = { selectedIds = filtered.map { it.id }.toSet() },
                        onDelete = { showDeleteConfirm = true },
                        onRemoveFromAnalytics = { showAnalyticsConfirm = true }
                    )
                } else {
                    AnimatedContent(
                        targetState = isSearchActive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        label = "search_transition"
                    ) { active ->
                        if (!active) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Transactions",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                // Small filter status row (Moved inside row to match Analytics styling)
                                Row(
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (uiState.selectedSortOrder != TransactionSortOrder.DATE_DESC) {
                                        FilterStatusChip(uiState.selectedSortOrder.label) {
                                            viewModel.onSortOrderChange(
                                                TransactionSortOrder.DATE_DESC
                                            )
                                        }
                                    }
                                    if (uiState.selectedFilter != "All") {
                                        FilterStatusChip(uiState.selectedFilter) {
                                            viewModel.onFilterChange(
                                                "All"
                                            )
                                        }
                                    }
                                    if (uiState.selectedAccount != "All") {
                                        FilterStatusChip(uiState.selectedAccount) {
                                            viewModel.onAccountChange(
                                                "All"
                                            )
                                        }
                                    }
                                    if (uiState.selectedCategory != "All") {
                                        FilterStatusChip(uiState.selectedCategory) {
                                            viewModel.onCategoryChange(
                                                "All"
                                            )
                                        }
                                    }
                                    if (uiState.startDateMillis != null) {
                                        FilterStatusChip("Date") { viewModel.clearDates() }
                                    }
                                }

                                IconButton(onClick = { isSearchActive = true }) {
                                    Icon(
                                        Icons.Rounded.Search,
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        } else {
                            LaunchedEffect(Unit) {
                                focusRequester.requestFocus()
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = viewModel::onQueryChange,
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        RoundedCornerShape(24.dp)
                                    ),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                decorationBox = { innerTextField ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = {
                                            isSearchActive = false
                                            viewModel.onQueryChange("")
                                        }) {
                                            Icon(
                                                Icons.AutoMirrored.Rounded.ArrowBack,
                                                contentDescription = "Back",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Box(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    "Search transactions...",
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = 15.sp,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                )
                                            }
                                            innerTextField()
                                        }
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                                Icon(
                                                    Icons.Rounded.Close,
                                                    null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        } else {
                                            Spacer(Modifier.width(16.dp))
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }


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
                if (uiState.trendSummaries.isNotEmpty()) {
                    ScrollableBarChart(
                        summaries = uiState.trendSummaries,
                        selectedMonth = uiState.selectedMonth,
                        onBarClick = { viewModel.onMonthToggle(it) }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // Filter row with count
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Box {
                        IconButton(
                            onClick = { showSortPopup = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = "Sort",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showSortPopup,
                            onDismissRequest = { showSortPopup = false },
                            shape = RoundedCornerShape(20.dp),
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.width(200.dp)
                        ) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = "Sort By",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                TransactionSortOrder.entries.forEach { order ->
                                    val isSelected = uiState.selectedSortOrder == order
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = order.label,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        },
                                        onClick = {
                                            viewModel.onSortOrderChange(order)
                                            showSortPopup = false
                                        },
                                        trailingIcon = {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        },
                                        colors = MenuDefaults.itemColors(
                                            textColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("Debit", "Credit").forEach { type ->
                            val isSelected = uiState.selectedFilter == type
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.onFilterChange(if (isSelected) "All" else type) },
                                label = { Text(type, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = Color.Transparent,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    selectedBorderColor = Color.Transparent,
                                    borderWidth = 1.dp,
                                    selectedBorderWidth = 0.dp
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                    }

                    Text(
                        text = "${filtered.size} transactions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val isAmountSort = uiState.selectedSortOrder == TransactionSortOrder.AMOUNT_DESC || 
                                 uiState.selectedSortOrder == TransactionSortOrder.AMOUNT_ASC

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    if (isAmountSort) {
                        items(
                            items = filtered,
                            key = { it.id }
                        ) { txn ->
                            TransactionRow(
                                txn = txn,
                                isSelected = selectedIds.contains(txn.id),
                                isSelectionMode = selectedIds.isNotEmpty(),
                                customCategories = uiState.customCategories,
                                allTransactions = uiState.allTransactions,
                                onClick = onTransactionClick,
                                onLongClick = onTransactionLongClick,
                                onCategoryClick = onQuickCategoryClick
                            )
                        }
                    } else {
                        groupedTransactions.forEach { (month, transactions) ->
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
                                TransactionRow(
                                    txn = txn,
                                    isSelected = selectedIds.contains(txn.id),
                                    isSelectionMode = selectedIds.isNotEmpty(),
                                    customCategories = uiState.customCategories,
                                    allTransactions = uiState.allTransactions,
                                    onClick = onTransactionClick,
                                    onLongClick = onTransactionLongClick,
                                    onCategoryClick = onQuickCategoryClick
                                )
                            }
                        }
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
                    icon = Icons.Rounded.AccountBalanceWallet,
                    label = "Account",
                    onClick = { showAccountFilterPopup = true }
                ),
                SpeedDialAction(
                    icon = Icons.Rounded.Category,
                    label = "Category",
                    onClick = { showCategoryFilterPopup = true }
                ),
                SpeedDialAction(
                    icon = Icons.Rounded.DateRange,
                    label = "Date",
                    onClick = { showDatePicker = true }
                ),
                SpeedDialAction(
                    icon = Icons.Rounded.Download,
                    label = "Export",
                    onClick = { viewModel.exportToCsv(context) }
                ),
                SpeedDialAction(
                    icon = Icons.Rounded.Add,
                    label = "Add",
                    onClick = { showAddSheet = true }
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
            onSelect = { viewModel.onAccountChange(it) },
            onDismiss = { showAccountFilterPopup = false },
            yOffset = popupYOffset
        )
    }

    if (showCategoryFilterPopup) {
        FloatingFilterPopup(
            title = "Filter Category",
            options = uiState.availableCategories,
            selected = uiState.selectedCategory,
            onSelect = { viewModel.onCategoryChange(it) },
            onDismiss = { showCategoryFilterPopup = false },
            emojiProvider = { CategoryEngine.emoji(it, uiState.customCategories) },
            yOffset = popupYOffset
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
                    selectedTxn = target
                }
            }
        )
    }

    // ── Add transaction sheet ────────────────────────────────────────────────
    if (showAddSheet) {
        AddTransactionDialog(
            onDismiss = { showAddSheet = false },
            onAdd = { viewModel.addTransaction(it) }
        )
    }

    // ── Bulk Actions Confirmation Dialogs ────────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Transactions") },
            text = { Text("Are you sure you want to delete ${selectedIds.size} transactions? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTransactions(selectedIds.toList())
                        selectedIds = emptySet()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showAnalyticsConfirm) {
        AlertDialog(
            onDismissRequest = { showAnalyticsConfirm = false },
            title = { Text("Exclude from Analytics") },
            text = { Text("Exclude ${selectedIds.size} transactions from analytics?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateAnalytics(selectedIds.toList(), false)
                        selectedIds = emptySet()
                        showAnalyticsConfirm = false
                    }
                ) { Text("Exclude") }
            },
            dismissButton = {
                TextButton(onClick = { showAnalyticsConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SelectionTopBar(
    count: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onRemoveFromAnalytics: () -> Unit
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear Selection")
            }
            Text(
                text = "$count selected",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Rounded.SelectAll, contentDescription = "Select All")
            }
            IconButton(onClick = onRemoveFromAnalytics) {
                Icon(
                    Icons.Rounded.VisibilityOff,
                    contentDescription = "Exclude from Analytics",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRow(
    txn: SmsTransactionEntity,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    customCategories: List<CustomCategoryEntity> = emptyList(),
    allTransactions: List<SmsTransactionEntity> = emptyList(),
    onClick: (SmsTransactionEntity) -> Unit,
    onLongClick: (SmsTransactionEntity) -> Unit,
    onCategoryClick: (SmsTransactionEntity) -> Unit
) {
    val isDebit = txn.type == "DEBIT"
    val isBillable = txn.isBillable
    val amountColor = if (isDebit) MaterialTheme.colorScheme.onSurface else Color(0xFF00B894)
    
    val locale = LocalConfiguration.current.locales[0]
    val transactionDateFormatter = remember(locale) { SimpleDateFormat("d MMM, h:mm a", locale) }
    
    val time = remember(txn.transactionTime, transactionDateFormatter) {
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
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surface
            )
            .combinedClickable(
                onClick = { onClick(txn) },
                onLongClick = { onLongClick(txn) }
            )
            .alpha(if (isBillable) 1f else 0.45f)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox for selection
        if (isSelectionMode || isSelected) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick(txn) },
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(24.dp)
            )
        }

        // Icon (Clickable for quick category change)

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

        // Amount
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = AmountUtils.formatWithCurrency(txn.amount),
                style = MaterialTheme.typography.bodyLarge,
                color = if (isBillable) amountColor
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (txn.originalAmount != null) {
                Text(
                    text = AmountUtils.formatWithCurrency(txn.originalAmount),
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
    val context = LocalContext.current
    val db = remember { LedgerPopDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    var selectedCategory by remember { mutableStateOf(txn.category.ifBlank { CategoryEngine.OTHER }) }
    
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
                text = "Update Category",
                style = MaterialTheme.typography.titleLarge
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

    if (showBulkUpdateDialog && pendingSavedTxn != null) {
        BulkUpdateDialog(
            newMerchantName = txn.merchant,
            newCategory = selectedCategory,
            newIsBillable = txn.isBillable,
            similarTransactions = similarTxnsToUpdate,
            onDismiss = {
                onSave(pendingSavedTxn!!)
                showBulkUpdateDialog = false
            },
            onApply = { selectedIds, updateMerchant, updateCategory, updateBillable ->
                scope.launch {
                    when {
                        updateMerchant && updateCategory && updateBillable -> 
                            db.smsTransactionDao().updateBulk(selectedIds, txn.merchant, selectedCategory, txn.isBillable)
                        updateMerchant && updateCategory -> db.smsTransactionDao().updateMerchantAndCategoryForIds(selectedIds, txn.merchant, selectedCategory)
                        updateMerchant && updateBillable -> {
                            db.smsTransactionDao().updateMerchantForIds(selectedIds, txn.merchant)
                            db.smsTransactionDao().updateBillableForIds(selectedIds, txn.isBillable)
                        }
                        updateCategory && updateBillable -> {
                            db.smsTransactionDao().updateCategoryForIds(selectedIds, selectedCategory)
                            db.smsTransactionDao().updateBillableForIds(selectedIds, txn.isBillable)
                        }
                        updateMerchant -> db.smsTransactionDao().updateMerchantForIds(selectedIds, txn.merchant)
                        updateCategory -> db.smsTransactionDao().updateCategoryForIds(selectedIds, selectedCategory)
                        updateBillable -> db.smsTransactionDao().updateBillableForIds(selectedIds, txn.isBillable)
                    }
                    onSave(pendingSavedTxn!!)
                    showBulkUpdateDialog = false
                }
            }
        )
    }
}

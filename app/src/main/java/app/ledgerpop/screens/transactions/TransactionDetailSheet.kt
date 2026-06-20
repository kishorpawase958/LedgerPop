package app.ledgerpop.screens.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ledgerpop.data.category.CategoryEngine
import app.ledgerpop.data.local.AccountEntity
import app.ledgerpop.data.local.CustomCategoryEntity
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.ui.components.BulkUpdateDialog
import app.ledgerpop.utils.AmountUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalLocale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    txn: SmsTransactionEntity,
    onDismiss: () -> Unit,
    onSave: (SmsTransactionEntity) -> Unit,
    onDelete: (SmsTransactionEntity) -> Unit,
    onNavigateToTransaction: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val db = remember { LedgerPopDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    val linkedCredits by produceState(initialValue = emptyList<SmsTransactionEntity>(), txn.id) {
        db.smsTransactionDao().getLinkedCredits(txn.id).collect { value = it }
    }

    val linkedDebit by produceState<SmsTransactionEntity?>(initialValue = null, txn.linkedTransactionId) {
        val id = txn.linkedTransactionId
        if (id != null) {
            val d = db.smsTransactionDao().getById(id)
            value = d
        } else {
            value = null
        }
    }

    val availableCredits by produceState(initialValue = emptyList<SmsTransactionEntity>(), txn.transactionTime) {
        db.smsTransactionDao().getAvailableCredits(txn.transactionTime).collect { value = it }
    }

    val existingTransactions by produceState(initialValue = emptyList()) {
        db.smsTransactionDao().getAllTransactions().collect { value = it }
    }

    val customCategories by produceState(initialValue = emptyList()) {
        db.customCategoryDao().getAllCategories().collect { value = it }
    }
    
    val accounts by produceState(initialValue = emptyList<AccountEntity>()) {
        db.accountDao().getAllAccounts().collect { value = it }
    }

    // Expense/Income toggle removed; type is derived from the transaction
    val isExpense = remember(txn) { txn.type == "DEBIT" }

    val categories = remember(isExpense, existingTransactions, customCategories) {
        val type = if (isExpense) "DEBIT" else "CREDIT"
        val engineCats = if (isExpense) CategoryEngine.debitCategories() else CategoryEngine.creditCategories()
        val customOfType = customCategories.filter { it.type == type }.map { it.name }
        val existingOfType = existingTransactions
            .filter { it.type == type }
            .map { it.category }
            .filter { it.isNotBlank() }
        (engineCats + customOfType + existingOfType).distinct().sorted()
    }
    
    val accountOptions = remember(accounts, existingTransactions) {
        val managed = accounts.map { it.name }
        val existing = existingTransactions.map { it.accountHint }.filter { it.isNotBlank() }
        (managed + existing).distinct().sorted()
    }

    var amount by remember(txn) { 
        mutableStateOf(AmountUtils.formatRaw(txn.amount)) 
    }
    var merchant by remember(txn) { mutableStateOf(txn.merchant) }
    var category by remember(txn) { mutableStateOf(txn.category) }
    var account by remember(txn) { mutableStateOf(txn.accountHint) }
    var note by remember(txn) { mutableStateOf(txn.note) }
    var isBillable by remember(txn) { mutableStateOf(txn.isBillable) }
    var selectedDateMillis by remember(txn) { mutableLongStateOf(txn.transactionTime) }
    var originalAmount by remember(txn) { mutableStateOf(txn.originalAmount) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLinkPicker by remember { mutableStateOf(false) }
    
    var showBulkUpdateDialog by remember { mutableStateOf(false) }
    var similarTxnsToUpdate by remember { mutableStateOf<List<SmsTransactionEntity>>(emptyList()) }
    var pendingSavedTxn by remember { mutableStateOf<SmsTransactionEntity?>(null) }

    val dateStr = remember(selectedDateMillis) {
        SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(selectedDateMillis))
    }
    val timeStr = remember(selectedDateMillis) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(selectedDateMillis))
    }

    val amountColor = if (isExpense) MaterialTheme.colorScheme.error else Color(0xFF00B894)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "Transaction",
                    style = MaterialTheme.typography.titleMedium
                )
                Button(
                    onClick = {
                        val amt = amount.toDoubleOrNull() ?: return@Button
                        val updatedTxn = txn.copy(
                            amount = amt,
                            type = if (isExpense) "DEBIT" else "CREDIT",
                            merchant = merchant,
                            category = category,
                            accountHint = account,
                            transactionTime = selectedDateMillis,
                            isBillable = isBillable,
                            note = note.trim(),
                            originalAmount = originalAmount
                        )

                        // Check if merchant or category changed
                        val oldMerchant = txn.merchant.trim()
                        val newMerchant = merchant.trim()
                        val merchantChanged = newMerchant != oldMerchant
                        val categoryChanged = category.trim() != txn.category.trim()

                        if ((merchantChanged || categoryChanged) && newMerchant.length >= 3) {
                            scope.launch {
                                val similarToNew = db.smsTransactionDao().getSimilarTransactions(newMerchant, txn.id)
                                val similarToOld = if (merchantChanged && oldMerchant.length >= 3) {
                                    db.smsTransactionDao().getSimilarTransactions(oldMerchant, txn.id)
                                } else emptyList()
                                
                                val allSimilar = (similarToNew + similarToOld).distinctBy { it.id }
                                val filtered = allSimilar.filter { 
                                    it.category != category.trim() || (merchantChanged && it.merchant.trim() != newMerchant)
                                }

                                if (filtered.isNotEmpty()) {
                                    pendingSavedTxn = updatedTxn
                                    similarTxnsToUpdate = filtered
                                    showBulkUpdateDialog = true
                                } else {
                                    onSave(updatedTxn)
                                }
                            }
                        } else {
                            onSave(updatedTxn)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Save")
                }
            }

            Spacer(Modifier.height(24.dp))

            // Large Amount Input Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isExpense) "PAID VIA" else "RECEIVED IN",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    letterSpacing = 1.sp
                )
                Text(
                    text = account.ifBlank { "Unspecified Account" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(Modifier.height(16.dp))
                
                // Amount Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isNeg = amount.startsWith("-")
                    val absAmount = if (isNeg) amount.substring(1) else amount

                    if (isNeg) {
                        Text(
                            text = "-",
                            style = MaterialTheme.typography.displayMedium,
                            color = amountColor
                        )
                    }
                    Text(
                        text = "₹",
                        style = MaterialTheme.typography.displayMedium,
                        color = amountColor
                    )
                    Spacer(Modifier.width(4.dp))
                    BasicTextField(
                        value = absAmount,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() || it == '.' }
                            if (filtered.count { it == '.' } <= 1) {
                                val afterDecimal = filtered.substringAfter(".", "")
                                if (afterDecimal.length <= 2 && filtered.length <= 12) {
                                    // Preserve the negative sign if it exists when editing the absolute part
                                    amount = (if (isNeg) "-" else "") + filtered
                                }
                            }
                        },
                        textStyle = MaterialTheme.typography.displayLarge.copy(
                            color = amountColor
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        visualTransformation = AmountUtils.indianCurrencyTransformation,
                        singleLine = true,
                        modifier = Modifier.width(IntrinsicSize.Min).defaultMinSize(minWidth = 20.dp),
                        decorationBox = { innerTextField ->
                            if (absAmount.isEmpty()) {
                                Text(
                                    text = "0",
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        color = amountColor.copy(alpha = 0.3f)
                                    )
                                )
                            }
                            innerTextField()
                        }
                    )
                }
                
                if (originalAmount != null && originalAmount != amount.toDoubleOrNull()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = "Original: ${AmountUtils.formatWithCurrency(originalAmount ?: 0.0)}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Main Info Card (Merchant, Category, Account)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Merchant Field
                    InfoField(
                        icon = Icons.Rounded.Store,
                        label = "Merchant",
                        value = merchant,
                        onValueChange = { merchant = it },
                        placeholder = "Enter merchant name"
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Category Field (Picker)
                    InfoPickerField(
                        icon = Icons.Rounded.Category,
                        label = "Category",
                        value = if (category.isBlank()) "Select Category" else category,
                        emoji = CategoryEngine.emoji(category, customCategories),
                        onClick = { showCategoryPicker = true }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Account Field (Picker)
                    InfoPickerField(
                        icon = Icons.Rounded.AccountBalanceWallet,
                        label = "Account",
                        value = if (account.isBlank()) "Select Account" else account,
                        onClick = { showAccountPicker = true }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Date & Time Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f).clickable { showDatePicker = true },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.CalendarToday, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(dateStr, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Card(
                    modifier = Modifier.weight(0.8f).clickable { showTimePicker = true },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Schedule, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(timeStr, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Notes
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Add a note") },
                placeholder = { Text("What was this for?") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Notes, null, modifier = Modifier.size(20.dp)) }
            )

            Spacer(Modifier.height(16.dp))

            // Reporting Switch
            Surface(
                onClick = { isBillable = !isBillable },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            if (isBillable) Icons.Rounded.Analytics else Icons.Rounded.VisibilityOff,
                            null,
                            tint = if (isBillable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                        Column {
                            Text("Include in Analytics", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (isBillable) "Counted in your totals" else "Hidden from totals",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    Switch(checked = isBillable, onCheckedChange = { isBillable = it })
                }
            }

            // Linked Transactions Section
            if (isExpense || linkedCredits.isNotEmpty() || linkedDebit != null) {
                Spacer(Modifier.height(24.dp))
                Text(
                    if (isExpense) "LINKED REFUNDS" else "LINKED EXPENSE",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.outline,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(12.dp))
                
                if (isExpense) {
                    linkedCredits.forEach { credit ->
                        LinkRow(credit, customCategories = customCategories, onNavigate = { onNavigateToTransaction(credit.id) }, onUnlink = {
                            scope.launch {
                                db.smsTransactionDao().linkCreditToDebit(credit.id, null)
                                val remainingLinked = db.smsTransactionDao().getLinkedCreditsSync(txn.id)
                                val baseOriginal = txn.originalAmount ?: txn.amount
                                val sumOfRemaining = remainingLinked.sumOf { it.amount }
                                val newAmount = baseOriginal - sumOfRemaining
                                val finalOriginal = if (newAmount == baseOriginal) null else baseOriginal
                                db.smsTransactionDao().update(txn.copy(amount = newAmount, originalAmount = finalOriginal))
                                amount = AmountUtils.formatRaw(newAmount)
                                originalAmount = finalOriginal
                            }
                        })
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedButton(
                        onClick = { showLinkPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Icon(Icons.Rounded.AddLink, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Link a Refund/Credit")
                    }
                } else if (linkedDebit != null) {
                    LinkRow(linkedDebit!!, customCategories = customCategories, onNavigate = { onNavigateToTransaction(linkedDebit!!.id) }, onUnlink = null)
                }
            }

            // Raw SMS Section
            if (txn.body.isNotBlank()) {
                Spacer(Modifier.height(24.dp))
                Column {
                    Text(
                        "ORIGINAL SMS",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Surface(
                        modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = txn.body,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Delete Button
            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Rounded.DeleteOutline, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Delete Transaction")
            }
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────
    
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { picked ->
                        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                        val pickedCal = Calendar.getInstance().apply { timeInMillis = picked }
                        cal[Calendar.YEAR] = pickedCal[Calendar.YEAR]
                        cal[Calendar.MONTH] = pickedCal[Calendar.MONTH]
                        cal[Calendar.DAY_OF_MONTH] = pickedCal[Calendar.DAY_OF_MONTH]
                        selectedDateMillis = cal.timeInMillis
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                    selectedYearContainerColor = MaterialTheme.colorScheme.primary,
                    todayContentColor = MaterialTheme.colorScheme.primary,
                    todayDateBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }

    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val c = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                    c.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    c.set(Calendar.MINUTE, timePickerState.minute)
                    selectedDateMillis = c.timeInMillis
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = {
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(
                        selectorColor = MaterialTheme.colorScheme.primary,
                        periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        periodSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        timeSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        )
    }

    if (showCategoryPicker) {
        CategoryAccountPicker(
            title = "Select Category",
            options = categories,
            selected = category,
            onSelect = { category = it },
            onDismiss = { showCategoryPicker = false },
            customCategories = customCategories,
            onAdd = { name ->
                scope.launch {
                    db.customCategoryDao().insert(
                        CustomCategoryEntity(name = name, type = if (isExpense) "DEBIT" else "CREDIT")
                    )
                }
            },
            onUpdate = { oldName, newName, emoji ->
                scope.launch {
                    val existing = db.customCategoryDao().getByName(oldName)
                    if (existing != null) {
                        db.customCategoryDao().insert(existing.copy(name = newName, emoji = emoji))
                    } else {
                        db.customCategoryDao().insert(
                            CustomCategoryEntity(name = newName, type = if (isExpense) "DEBIT" else "CREDIT", emoji = emoji)
                        )
                    }
                    if (oldName != newName) {
                        db.smsTransactionDao().updateCategoryName(oldName, newName)
                    }
                }
            }
        )
    }

    if (showAccountPicker) {
        CategoryAccountPicker(
            title = "Select Account",
            options = accountOptions,
            selected = account,
            onSelect = { account = it },
            onDismiss = { showAccountPicker = false },
            accounts = accounts,
            onAdd = { name ->
                scope.launch {
                    db.accountDao().insert(AccountEntity(name = name))
                }
            },
            onUpdate = { oldName, newName, icon ->
                scope.launch {
                    val existing = db.accountDao().getByName(oldName)
                    if (existing != null) {
                        db.accountDao().insert(existing.copy(name = newName, icon = icon))
                    } else {
                        db.accountDao().insert(AccountEntity(name = newName, icon = icon))
                    }
                    if (oldName != newName) {
                        db.smsTransactionDao().updateAccountName(oldName, newName)
                    }
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete transaction?") },
            text = { Text("This will permanently remove this transaction.") },
            confirmButton = {
                TextButton(onClick = { onDelete(txn) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showLinkPicker && isExpense) {
        LinkPickerDialog(
            title = "Select Refund to Link",
            availableTransactions = availableCredits,
            onDismiss = { showLinkPicker = false },
            onSelect = { selected ->
                scope.launch {
                    db.smsTransactionDao().linkCreditToDebit(selected.id, txn.id)
                    val allLinked = db.smsTransactionDao().getLinkedCreditsSync(txn.id)
                    val baseOriginal = txn.originalAmount ?: txn.amount
                    val sumOfCredits = allLinked.sumOf { it.amount }
                    val newAmount = baseOriginal - sumOfCredits
                    db.smsTransactionDao().update(txn.copy(amount = newAmount, originalAmount = baseOriginal))
                    amount = AmountUtils.formatRaw(newAmount)
                    originalAmount = baseOriginal
                }
                showLinkPicker = false
            }
        )
    }

    if (showBulkUpdateDialog && pendingSavedTxn != null) {
        BulkUpdateDialog(
            newMerchantName = merchant,
            newCategory = category,
            similarTransactions = similarTxnsToUpdate,
            onDismiss = {
                onSave(pendingSavedTxn!!)
                showBulkUpdateDialog = false
            },
            onApply = { selectedIds, updateMerchant, updateCategory ->
                scope.launch {
                    when {
                        updateMerchant && updateCategory -> db.smsTransactionDao().updateMerchantAndCategoryForIds(selectedIds, merchant, category)
                        updateMerchant -> db.smsTransactionDao().updateMerchantForIds(selectedIds, merchant)
                        updateCategory -> db.smsTransactionDao().updateCategoryForIds(selectedIds, category)
                    }
                    onSave(pendingSavedTxn!!)
                    showBulkUpdateDialog = false
                }
            }
        )
    }
}

@Composable
fun InfoField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outlineVariant)
                    innerTextField()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun InfoPickerField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    emoji: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (emoji != null && emoji.isNotBlank()) {
                Text(emoji, style = MaterialTheme.typography.titleMedium)
            } else {
                Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun LinkRow(
    txn: SmsTransactionEntity,
    customCategories: List<CustomCategoryEntity> = emptyList(),
    onNavigate: () -> Unit,
    onUnlink: (() -> Unit)? = null
) {
    val dateStr = SimpleDateFormat("d MMM yy", LocalLocale.current.platformLocale).format(Date(txn.transactionTime))
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onNavigate() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(CategoryEngine.emoji(txn.category, customCategories), style = MaterialTheme.typography.titleMedium)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(txn.merchant.ifBlank { txn.sender }, style = MaterialTheme.typography.bodyMedium)
                Text("$dateStr · ${AmountUtils.formatWithCurrency(txn.amount)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
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
            if (onUnlink != null) {
                IconButton(onClick = onUnlink) {
                    Icon(Icons.Rounded.LinkOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            } else {
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
fun LinkPickerDialog(
    title: String,
    availableTransactions: List<SmsTransactionEntity>,
    onSelect: (SmsTransactionEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (availableTransactions.isEmpty()) {
                Text("No available transactions to link.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(availableTransactions) { txn ->
                        val dateStr = SimpleDateFormat("d MMM yy", LocalLocale.current.platformLocale).format(Date(txn.transactionTime))
                        ListItem(
                            headlineContent = { Text(txn.merchant.ifBlank { txn.sender }) },
                            supportingContent = { Text("$dateStr · ${AmountUtils.formatWithCurrency(txn.amount)}") },
                            modifier = Modifier.clickable { onSelect(txn) }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

package app.ledgerpop.screens.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ledgerpop.data.category.CategoryEngine
import app.ledgerpop.data.local.AccountEntity
import app.ledgerpop.data.local.CustomCategoryEntity
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.local.SmsTransactionEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    txn: SmsTransactionEntity,
    onDismiss: () -> Unit,
    onSave: (SmsTransactionEntity) -> Unit,
    onDelete: (SmsTransactionEntity) -> Unit
) {
    val context = LocalContext.current
    val db = remember { LedgerPopDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    val existingTransactions by produceState(initialValue = emptyList<SmsTransactionEntity>()) {
        db.smsTransactionDao().getAllTransactions().collect { value = it }
    }

    val customCategories by produceState(initialValue = emptyList<CustomCategoryEntity>()) {
        db.customCategoryDao().getAllCategories().collect { value = it }
    }
    
    val accounts by produceState(initialValue = emptyList<AccountEntity>()) {
        db.accountDao().getAllAccounts().collect { value = it }
    }

    var isExpense by remember { mutableStateOf(txn.type == "DEBIT") }

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
    var amount by remember { mutableStateOf(txn.amount.toString()) }
    var merchant by remember { mutableStateOf(txn.merchant) }
    var sender by remember { mutableStateOf(txn.sender) }
    var category by remember { mutableStateOf(txn.category) }
    var account by remember { mutableStateOf(txn.accountHint) }
    var note by remember { mutableStateOf(txn.note) }
    var isBillable by remember { mutableStateOf(txn.isBillable) }
    var selectedDateMillis by remember { mutableStateOf(txn.transactionTime) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val dateStr = remember(selectedDateMillis) {
        SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(selectedDateMillis))
    }
    val timeStr = remember(selectedDateMillis) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(selectedDateMillis))
    }

    val amountColor = if (isExpense) MaterialTheme.colorScheme.error else Color(0xFF00897B)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Transaction Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            // Expense / Income toggle
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = isExpense,
                    onClick = { isExpense = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("Expense") }
                )
                SegmentedButton(
                    selected = !isExpense,
                    onClick = { isExpense = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("Income") }
                )
            }

            // Amount
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount (₹)") },
                leadingIcon = {
                    Icon(
                        if (isExpense) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                        contentDescription = null,
                        tint = amountColor
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Merchant
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Merchant / Description") },
                leadingIcon = { Icon(Icons.Rounded.Store, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Sender
            OutlinedTextField(
                value = sender,
                onValueChange = { sender = it },
                label = { Text("From (sender / bank)") },
                leadingIcon = { Icon(Icons.Rounded.AccountBalance, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Category — read-only, opens picker
            OutlinedTextField(
                value = category,
                onValueChange = {},
                readOnly = true,
                label = { Text("Category") },
                leadingIcon = { Icon(Icons.Rounded.Category, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showCategoryPicker = true }) {
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Pick category")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Account — read-only, opens picker
            OutlinedTextField(
                value = account,
                onValueChange = {},
                readOnly = true,
                label = { Text("Account (last 4 digits / UPI ID)") },
                leadingIcon = { Icon(Icons.Rounded.CreditCard, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showAccountPicker = true }) {
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Pick account")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Note
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Notes, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3
            )

            // Date & Time — both editable
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = dateStr,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date") },
                    leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit date",
                                modifier = Modifier.size(16.dp))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = timeStr,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Time") },
                    leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showTimePicker = true }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit time",
                                modifier = Modifier.size(16.dp))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Include in reports toggle
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Include in reports",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (isBillable) "Counted in totals & analytics"
                            else "Excluded from totals & analytics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isBillable, onCheckedChange = { isBillable = it })
                }
            }
            // ── Raw SMS ─────────────────────────────────────────────────────────────────
            if (txn.body.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "ORIGINAL MESSAGE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = txn.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            // Delete + Save buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }
                Button(
                    onClick = {
                        val amt = amount.toDoubleOrNull() ?: return@Button
                        onSave(
                            txn.copy(
                                amount = amt,
                                type = if (isExpense) "DEBIT" else "CREDIT",
                                merchant = merchant,
                                sender = sender,
                                category = category,
                                accountHint = account,
                                transactionTime = selectedDateMillis,
                                isBillable = isBillable,
                                note = note.trim()
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save")
                }
            }
        }
    }

    // ── Date Picker Dialog ───────────────────────────────────────────────────
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { picked ->
                        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                        val pickedCal = Calendar.getInstance().apply { timeInMillis = picked }
                        cal.set(Calendar.YEAR, pickedCal.get(Calendar.YEAR))
                        cal.set(Calendar.MONTH, pickedCal.get(Calendar.MONTH))
                        cal.set(Calendar.DAY_OF_MONTH, pickedCal.get(Calendar.DAY_OF_MONTH))
                        selectedDateMillis = cal.timeInMillis
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ── Time Picker Dialog ───────────────────────────────────────────────────
    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            confirmButton = {
                TextButton(onClick = {
                    val c = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                    c.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    c.set(Calendar.MINUTE, timePickerState.minute)
                    selectedDateMillis = c.timeInMillis
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }

    // ── Category Picker ──────────────────────────────────────────────────────
    if (showCategoryPicker) {
        CategoryAccountPicker(
            title = "Select Category",
            options = categories,
            selected = category,
            onSelect = { category = it },
            onDismiss = { showCategoryPicker = false },
            customCategories = customCategories,
            onUpdateEmoji = { name, emoji ->
                scope.launch {
                    val existing = db.customCategoryDao().getByName(name)
                    if (existing != null) {
                        db.customCategoryDao().insert(existing.copy(emoji = emoji))
                    } else {
                        db.customCategoryDao().insert(
                            CustomCategoryEntity(
                                name = name,
                                type = if (isExpense) "DEBIT" else "CREDIT",
                                emoji = emoji
                            )
                        )
                    }
                }
            }
        )
    }

    // ── Account Picker ───────────────────────────────────────────────────────
    if (showAccountPicker) {
        CategoryAccountPicker(
            title = "Select Account",
            options = accountOptions,
            selected = account,
            onSelect = { account = it },
            onDismiss = { showAccountPicker = false },
            accounts = accounts,
            onUpdateEmoji = { name, icon ->
                scope.launch {
                    val existing = db.accountDao().getByName(name)
                    if (existing != null) {
                        db.accountDao().insert(existing.copy(icon = icon))
                    } else {
                        db.accountDao().insert(AccountEntity(name = name, icon = icon))
                    }
                }
            }
        )
    }

    // ── Delete Confirmation Dialog ───────────────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Delete transaction?") },
            text = { Text("This will permanently remove this transaction and cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(txn) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
package app.ledgerpop.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.ledgerpop.data.category.CategoryEngine
import app.ledgerpop.data.local.CustomCategoryEntity
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.local.SmsTransactionEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    onDismiss: () -> Unit,
    onAdd: (SmsTransactionEntity) -> Unit
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
    
    var isExpense by remember { mutableStateOf(true) }

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
    
    val existingAccounts = remember(existingTransactions) {
        existingTransactions.map { it.accountHint }.filter { it.isNotBlank() }.distinct().sorted()
    }
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var accountHint by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var isBillable by remember { mutableStateOf(true) }
    var amountError by remember { mutableStateOf(false) }

    // Date & Time State
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }

    val dateStr = remember(selectedDateMillis) {
        SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(selectedDateMillis))
    }
    val timeStr = remember(selectedDateMillis) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(selectedDateMillis))
    }

    val type = if (isExpense) "DEBIT" else "CREDIT"
    val accentColor = if (isExpense) MaterialTheme.colorScheme.error else Color(0xFF00897B)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface, // Solid surface background
        tonalElevation = 0.dp, // Disable tonal transparency blending
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add Transaction",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            // Type toggle
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

            Spacer(Modifier.height(12.dp))

            // Amount
            OutlinedTextField(
                value = amount,
                onValueChange = {
                    amount = it.filter { c -> c.isDigit() || c == '.' }
                    amountError = false
                },
                label = { Text("Amount (₹) *") },
                isError = amountError,
                supportingText = if (amountError) ({ Text("Enter a valid amount") }) else null,
                leadingIcon = {
                    Icon(
                        if (isExpense) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                        contentDescription = null,
                        tint = accentColor
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(Modifier.height(10.dp))

            // Merchant / Description
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Merchant / Description") },
                leadingIcon = { Icon(Icons.Rounded.Store, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(Modifier.height(10.dp))

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

            Spacer(Modifier.height(10.dp))

            // Account — read-only, opens picker
            OutlinedTextField(
                value = accountHint,
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

            Spacer(Modifier.height(10.dp))

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

            Spacer(Modifier.height(10.dp))

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
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit date", modifier = Modifier.size(16.dp))
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
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit time", modifier = Modifier.size(16.dp))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

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

            Spacer(Modifier.height(20.dp))

            // Save Button
            Button(
                onClick = {
                    val parsed = amount.toDoubleOrNull()
                    if (parsed == null || parsed <= 0) {
                        amountError = true
                        return@Button
                    }
                    onAdd(
                        SmsTransactionEntity(
                            sender = "Manual",
                            body = "Manually added transaction",
                            amount = parsed,
                            type = type,
                            merchant = merchant.trim(),
                            category = category.trim().ifBlank { "Manual" },
                            bank = "Manual",
                            accountHint = accountHint.trim(),
                            transactionTime = selectedDateMillis,
                            hashKey = UUID.randomUUID().toString(),
                            isBillable = isBillable,
                            note = note.trim()
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Add ${if (isExpense) "Expense" else "Income"}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
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
            options = existingAccounts,
            selected = accountHint,
            onSelect = { accountHint = it },
            onDismiss = { showAccountPicker = false }
        )
    }
}
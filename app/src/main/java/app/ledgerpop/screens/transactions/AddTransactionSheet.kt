package app.ledgerpop.screens.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onAdd: (SmsTransactionEntity) -> Unit,
) {
    val context = LocalContext.current
    val db = remember { LedgerPopDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    val existingTransactions by produceState(initialValue = emptyList()) {
        db.smsTransactionDao().getAllTransactions().collect { value = it }
    }

    val customCategories by produceState(initialValue = emptyList()) {
        db.customCategoryDao().getAllCategories().collect { value = it }
    }
    
    val accounts by produceState(initialValue = emptyList<AccountEntity>()) {
        db.accountDao().getAllAccounts().collect { value = it }
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
    
    val accountOptions = remember(accounts, existingTransactions) {
        val managed = accounts.map { it.name }
        val existing = existingTransactions.map { it.accountHint }.filter { it.isNotBlank() }
        (managed + existing).distinct().sorted()
    }

    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var accountHint by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var isBillable by remember { mutableStateOf(true) }
    var amountError by remember { mutableStateOf(false) }

    // Lookback logic: Auto-fill category when merchant is entered
    LaunchedEffect(merchant) {
        val trimmed = merchant.trim()
        if (trimmed.length >= 3) {
            val normalized = CategoryEngine.normalizeMerchant(trimmed)
            val historicalCategory = db.smsTransactionDao().getLastCategoryForMerchant(normalized)
                ?: db.smsTransactionDao().getLastCategoryForMerchant(trimmed)
                ?: db.smsTransactionDao().getLastCategoryForMerchantFuzzy(normalized)
                ?: db.smsTransactionDao().getLastCategoryForMerchantFuzzy(trimmed)

            if (historicalCategory != null) {
                category = historicalCategory
            } else {
                val engineCat = CategoryEngine.categorize(trimmed, "", "")
                if (engineCat != "Other") category = engineCat
            }
        } else if (trimmed.isEmpty()) {
            category = ""
        }
    }

    // Account mapping logic
    LaunchedEffect(accountHint) {
        if (accountHint.isNotBlank()) {
            val resolved = db.accountAliasDao().getTargetName(accountHint.trim())
            if (resolved != null && resolved != accountHint) {
                accountHint = resolved
            }
        }
    }

    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    
    var showBulkUpdateDialog by remember { mutableStateOf(false) }
    var similarTxnsToUpdate by remember { mutableStateOf<List<SmsTransactionEntity>>(emptyList()) }
    var pendingAddedTxn by remember { mutableStateOf<SmsTransactionEntity?>(null) }

    val dateStr = remember(selectedDateMillis) {
        SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(selectedDateMillis))
    }
    val timeStr = remember(selectedDateMillis) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(selectedDateMillis))
    }

    val amountColor = if (isExpense) MaterialTheme.colorScheme.error else Color(0xFF00B894)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Add Transaction", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(16.dp))

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

                Spacer(Modifier.height(24.dp))

                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isExpense) "PAID VIA" else "RECEIVED IN",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = accountHint.ifBlank { "Unspecified Account" },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "₹",
                            style = MaterialTheme.typography.displayMedium,
                            color = if (amountError) MaterialTheme.colorScheme.error else amountColor
                        )
                        Spacer(Modifier.width(4.dp))
                        BasicTextField(
                            value = amount,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() || it == '.' }
                                if (filtered.count { it == '.' } <= 1) {
                                    val afterDecimal = filtered.substringAfter(".", "")
                                    if (afterDecimal.length <= 2 && filtered.length <= 12) {
                                        amount = filtered
                                        amountError = false
                                    }
                                }
                            },
                            textStyle = MaterialTheme.typography.displayLarge.copy(
                                color = if (amountError) MaterialTheme.colorScheme.error else amountColor,
                                textAlign = TextAlign.Start
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            visualTransformation = AmountUtils.indianCurrencyTransformation,
                            singleLine = true,
                            modifier = Modifier.width(IntrinsicSize.Min).defaultMinSize(minWidth = 20.dp),
                            decorationBox = { innerTextField ->
                                if (amount.isEmpty()) {
                                    Text(
                                        text = "0",
                                        style = MaterialTheme.typography.displayLarge.copy(
                                            color = (if (amountError) MaterialTheme.colorScheme.error else amountColor).copy(alpha = 0.3f)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                    if (amountError) {
                        Text("Enter a valid amount", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(Modifier.height(24.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        InfoField(
                            icon = Icons.Rounded.Store,
                            label = "Merchant / Description",
                            value = merchant,
                            onValueChange = { merchant = it },
                            placeholder = "Enter merchant name"
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        InfoPickerField(
                            icon = Icons.Rounded.Category,
                            label = "Category",
                            value = category.ifBlank { "Select Category" },
                            emoji = CategoryEngine.emoji(category, customCategories),
                            onClick = { showCategoryPicker = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        InfoPickerField(
                            icon = Icons.Rounded.CreditCard,
                            label = "Account",
                            value = accountHint.ifBlank { "Select Account" },
                            onClick = { showAccountPicker = true }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.weight(1f).clickable { showDatePicker = true },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.CalendarToday, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text(dateStr, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Card(
                        modifier = Modifier.weight(0.8f).clickable { showTimePicker = true },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Schedule, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text(timeStr, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

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

                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val parsed = amount.toDoubleOrNull()
                            if (parsed == null || parsed <= 0) {
                                amountError = true
                                return@Button
                            }
                            val newTxn = SmsTransactionEntity(
                                sender = "Manual",
                                body = "Manually added transaction",
                                amount = parsed,
                                type = if (isExpense) "DEBIT" else "CREDIT",
                                merchant = merchant.trim(),
                                category = category.trim().ifBlank { "Manual" },
                                bank = "Manual",
                                accountHint = accountHint.trim(),
                                transactionTime = selectedDateMillis,
                                hashKey = UUID.randomUUID().toString(),
                                isBillable = isBillable,
                                note = note.trim()
                            )

                            if (merchant.trim().length >= 3) {
                                scope.launch {
                                    val similar = db.smsTransactionDao().getSimilarTransactions(merchant.trim(), -1)
                                    val filtered = similar.filter { it.category != category.trim() }
                                    if (filtered.isNotEmpty()) {
                                        pendingAddedTxn = newTxn
                                        similarTxnsToUpdate = filtered
                                        showBulkUpdateDialog = true
                                    } else {
                                        onAdd(newTxn)
                                        onDismiss()
                                    }
                                }
                            } else {
                                onAdd(newTxn)
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Add") }
                }
            }
        }

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
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
                DatePicker(state = datePickerState)
            }
        }

        if (showTimePicker) {
            val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
            val timePickerState = rememberTimePickerState(initialHour = cal.get(Calendar.HOUR_OF_DAY), initialMinute = cal.get(Calendar.MINUTE))
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
                text = { TimePicker(state = timePickerState) }
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
                onAdd = { name -> scope.launch { db.customCategoryDao().insert(CustomCategoryEntity(name = name, type = if (isExpense) "DEBIT" else "CREDIT")) } },
                onUpdate = { oldName, newName, emoji ->
                    scope.launch {
                        val existing = db.customCategoryDao().getByName(oldName)
                        if (existing != null) db.customCategoryDao().insert(existing.copy(name = newName, emoji = emoji))
                        else db.customCategoryDao().insert(CustomCategoryEntity(name = newName, type = if (isExpense) "DEBIT" else "CREDIT", emoji = emoji))
                        if (oldName != newName) db.smsTransactionDao().updateCategoryName(oldName, newName)
                    }
                }
            )
        }

        if (showAccountPicker) {
            CategoryAccountPicker(
                title = "Select Account",
                options = accountOptions,
                selected = accountHint,
                onSelect = { accountHint = it },
                onDismiss = { showAccountPicker = false },
                accounts = accounts,
                onAdd = { name -> scope.launch { db.accountDao().insert(AccountEntity(name = name)) } },
                onUpdate = { oldName, newName, icon ->
                    scope.launch {
                        val existing = db.accountDao().getByName(oldName)
                        if (existing != null) db.accountDao().insert(existing.copy(name = newName, icon = icon))
                        else db.accountDao().insert(AccountEntity(name = newName, icon = icon))
                        if (oldName != newName) db.smsTransactionDao().updateAccountName(oldName, newName)
                    }
                }
            )
        }

        if (showBulkUpdateDialog && pendingAddedTxn != null) {
            BulkUpdateDialog(
                newMerchantName = merchant,
                newCategory = category,
                similarTransactions = similarTxnsToUpdate,
                onDismiss = {
                    onAdd(pendingAddedTxn!!)
                    showBulkUpdateDialog = false
                    onDismiss()
                },
                onApply = { selectedIds, updateMerchant, updateCategory ->
                    scope.launch {
                        when {
                            updateMerchant && updateCategory -> db.smsTransactionDao().updateMerchantAndCategoryForIds(selectedIds, merchant, category)
                            updateMerchant -> db.smsTransactionDao().updateMerchantForIds(selectedIds, merchant)
                            updateCategory -> db.smsTransactionDao().updateCategoryForIds(selectedIds, category)
                        }
                        onAdd(pendingAddedTxn!!)
                        showBulkUpdateDialog = false
                        onDismiss()
                    }
                }
            )
        }
    }
}

package app.ledgerpop.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ledgerpop.data.local.AccountEntity
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.ui.viewmodel.SettingsViewModel

@Composable
fun AccountManagementScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { LedgerPopDatabase.getInstance(context) }
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(db, context))
    val uiState by viewModel.uiState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var mergingAccount by remember { mutableStateOf<AccountEntity?>(null) }

    val banks = uiState.accounts.filter { it.type == "BANK" }
    val cards = uiState.accounts.filter { it.type == "CARD" }
    val others = uiState.accounts.filter { it.type == "OTHER" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Accounts",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Account")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Banks
            if (banks.isNotEmpty() || (cards.isEmpty() && others.isEmpty() && uiState.accounts.isEmpty())) {
                item { SectionHeader("1. Banks") }
                if (banks.isEmpty()) {
                    item { EmptySectionMessage("No banks added") }
                } else {
                    items(banks) { account ->
                        AccountRow(
                            account = account,
                            aliases = uiState.accountAliases.filter { it.targetAccountName == account.name },
                            onEdit = { editingAccount = account },
                            onMerge = { mergingAccount = account },
                            onDelete = { viewModel.deleteAccount(account.id) },
                            onMove = { newType -> viewModel.updateAccount(account.id, account.name, account.icon, newType) }
                        )
                    }
                }
            }

            // 2. Cards
            item { SectionHeader("2. Cards") }
            if (cards.isEmpty()) {
                item { EmptySectionMessage("No cards added") }
            } else {
                items(cards) { account ->
                    AccountRow(
                        account = account,
                        aliases = uiState.accountAliases.filter { it.targetAccountName == account.name },
                        onEdit = { editingAccount = account },
                        onMerge = { mergingAccount = account },
                        onDelete = { viewModel.deleteAccount(account.id) },
                        onMove = { newType -> viewModel.updateAccount(account.id, account.name, account.icon, newType) }
                    )
                }
            }

            // 3. Others
            item { SectionHeader("3. Others") }
            if (others.isEmpty()) {
                item { EmptySectionMessage("No other accounts") }
            } else {
                items(others) { account ->
                    AccountRow(
                        account = account,
                        aliases = uiState.accountAliases.filter { it.targetAccountName == account.name },
                        onEdit = { editingAccount = account },
                        onMerge = { mergingAccount = account },
                        onDelete = { viewModel.deleteAccount(account.id) },
                        onMove = { newType -> viewModel.updateAccount(account.id, account.name, account.icon, newType) }
                    )
                }
            }
            
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAddDialog) {
        AddAccountDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, icon, type ->
                viewModel.addAccount(name, icon, type)
                showAddDialog = false
            }
        )
    }

    editingAccount?.let { account ->
        EditAccountDialog(
            currentName = account.name,
            currentIcon = account.icon,
            currentType = account.type,
            onDismiss = { editingAccount = null },
            onSave = { newName, newIcon, newType ->
                viewModel.updateAccount(account.id, newName, newIcon, newType)
                editingAccount = null
            }
        )
    }

    mergingAccount?.let { account ->
        MergeAccountDialog(
            sourceAccount = account,
            allAccounts = uiState.accounts,
            onDismiss = { mergingAccount = null },
            onMerge = { target ->
                viewModel.mergeAccounts(account.id, target.id)
                mergingAccount = null
            }
        )
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun EmptySectionMessage(message: String) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

@Composable
fun AccountRow(
    account: AccountEntity,
    aliases: List<app.ledgerpop.data.local.AccountAliasEntity>,
    onEdit: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit,
    onMove: (String) -> Unit
) {
    var showMoveMenu by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth().clickable { if (aliases.isNotEmpty()) expanded = !expanded }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                        .clickable { onEdit() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(account.icon, fontSize = 20.sp)
                }
                
                Spacer(Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(account.name)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(account.type.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (aliases.isNotEmpty()) {
                            Text(" • ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${aliases.size} linked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                if (aliases.isNotEmpty()) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = "Expand",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Box {
                    IconButton(onClick = { showMoveMenu = true }) {
                        Icon(Icons.Rounded.DragHandle, contentDescription = "Move", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
                    }
                    DropdownMenu(expanded = showMoveMenu, onDismissRequest = { showMoveMenu = false }) {
                        listOf("BANK", "CARD", "OTHER").forEach { type ->
                            DropdownMenuItem(
                                text = { Text("Move to ${type.lowercase().replaceFirstChar { it.uppercase() }}s") },
                                onClick = {
                                    onMove(type)
                                    showMoveMenu = false
                                },
                                enabled = type != account.type
                            )
                        }
                    }
                }

                IconButton(onClick = onMerge) {
                    Icon(Icons.Rounded.Merge, contentDescription = "Merge", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
                }

                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                }
            }

            if (expanded && aliases.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(start = 64.dp, end = 16.dp, bottom = 12.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Linked Duplicates / Aliases:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    aliases.forEach { alias ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Link, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.width(8.dp))
                            Text(alias.alias, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("🏦") }
    var type by remember { mutableStateOf("BANK") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Name / UPI ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { Text("Icon (Emoji)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text("Account Type", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = type == "BANK", onClick = { type = "BANK" })
                    Text("Bank")
                    Spacer(Modifier.width(8.dp))
                    RadioButton(selected = type == "CARD", onClick = { type = "CARD" })
                    Text("Card")
                    Spacer(Modifier.width(8.dp))
                    RadioButton(selected = type == "OTHER", onClick = { type = "OTHER" })
                    Text("Other")
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onSave(name, icon, type) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EditAccountDialog(
    currentName: String,
    currentIcon: String,
    currentType: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var icon by remember { mutableStateOf(currentIcon) }
    var type by remember { mutableStateOf(currentType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Name / UPI ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { Text("Icon (Emoji)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Account Type", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = type == "BANK", onClick = { type = "BANK" })
                    Text("Bank")
                    Spacer(Modifier.width(8.dp))
                    RadioButton(selected = type == "CARD", onClick = { type = "CARD" })
                    Text("Card")
                    Spacer(Modifier.width(8.dp))
                    RadioButton(selected = type == "OTHER", onClick = { type = "OTHER" })
                    Text("Other")
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onSave(name, icon, type) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun MergeAccountDialog(
    sourceAccount: AccountEntity,
    allAccounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onMerge: (AccountEntity) -> Unit
) {
    var selectedTarget by remember { mutableStateOf<AccountEntity?>(null) }
    val otherAccounts = allAccounts.filter { it.id != sourceAccount.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge ${sourceAccount.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select target account to merge into. All transactions will be moved and future matches will be redirected.")
                
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(otherAccounts) { account ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTarget = account }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedTarget?.id == account.id, onClick = { selectedTarget = account })
                            Text(account.icon, modifier = Modifier.padding(horizontal = 8.dp), fontSize = 18.sp)
                            Text(account.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedTarget?.let { onMerge(it) } },
                enabled = selectedTarget != null
            ) {
                Text("Merge")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

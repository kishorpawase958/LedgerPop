package app.ledgerpop.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ledgerpop.data.category.CategoryEngine
import app.ledgerpop.data.local.CustomCategoryEntity
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { LedgerPopDatabase.getInstance(context) }
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(db, context))
    val uiState by viewModel.uiState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CustomCategoryEntity?>(null) }
    var editingStandardCategory by remember { mutableStateOf<Pair<String, String>?>(null) } // Name, Type

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add Category")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Standard Categories",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            val standardDebit = listOf(
                CategoryEngine.FOOD, CategoryEngine.GROCERIES, CategoryEngine.SHOPPING,
                CategoryEngine.TRAVEL, CategoryEngine.FUEL, CategoryEngine.BILLS,
                CategoryEngine.HEALTH, CategoryEngine.INSURANCE, CategoryEngine.INVESTMENTS,
                CategoryEngine.ENTERTAINMENT, CategoryEngine.EMI, CategoryEngine.TRANSFER,
                CategoryEngine.OTHER
            )
            
            items(standardDebit) { name ->
                val custom = uiState.customCategories.find { it.name == name }
                CategoryRow(
                    name = name,
                    emoji = custom?.emoji ?: CategoryEngine.emoji(name),
                    type = "DEBIT",
                    isStandard = true,
                    onEditEmoji = { editingStandardCategory = name to "DEBIT" }
                )
            }

            item {
                Text(
                    "Standard Credit Categories",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }

            val standardCredit = listOf(
                CategoryEngine.SALARY, CategoryEngine.INTEREST, CategoryEngine.DIVIDEND,
                CategoryEngine.REFUND
            )

            items(standardCredit) { name ->
                val custom = uiState.customCategories.find { it.name == name }
                CategoryRow(
                    name = name,
                    emoji = custom?.emoji ?: CategoryEngine.emoji(name),
                    type = "CREDIT",
                    isStandard = true,
                    onEditEmoji = { editingStandardCategory = name to "CREDIT" }
                )
            }

            val trulyCustom = uiState.customCategories.filter { cat ->
                val isStandardDebit = standardDebit.any { it.equals(cat.name, ignoreCase = true) }
                val isStandardCredit = standardCredit.any { it.equals(cat.name, ignoreCase = true) }
                !isStandardDebit && !isStandardCredit
            }

            if (trulyCustom.isNotEmpty()) {
                item {
                    Text(
                        "Your Categories",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }

                items(trulyCustom) { cat ->
                    CategoryRow(
                        name = cat.name,
                        emoji = cat.emoji,
                        type = cat.type,
                        isStandard = false,
                        onEditEmoji = { editingCategory = cat },
                        onDelete = { viewModel.deleteCategory(cat.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddCategoryDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, emoji, type ->
                viewModel.addCustomCategory(name, emoji, type)
                showAddDialog = false
            }
        )
    }

    editingCategory?.let { cat ->
        EmojiEditDialog(
            currentName = cat.name,
            currentEmoji = cat.emoji,
            onDismiss = { editingCategory = null },
            onSave = { newEmoji ->
                viewModel.updateCategoryEmoji(cat.name, newEmoji, cat.type)
                editingCategory = null
            }
        )
    }

    editingStandardCategory?.let { (name, type) ->
        val custom = uiState.customCategories.find { it.name == name }
        EmojiEditDialog(
            currentName = name,
            currentEmoji = custom?.emoji ?: CategoryEngine.emoji(name),
            onDismiss = { editingStandardCategory = null },
            onSave = { newEmoji ->
                viewModel.updateCategoryEmoji(name, newEmoji, type)
                editingStandardCategory = null
            }
        )
    }
}

@Composable
fun CategoryRow(
    name: String,
    emoji: String,
    type: String,
    isStandard: Boolean,
    onEditEmoji: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
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
                    .clickable { onEditEmoji() },
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 20.sp)
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold)
                Text(type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            IconButton(onClick = onEditEmoji) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit Emoji", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }

            if (!isStandard && onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("📁") }
    var type by remember { mutableStateOf("DEBIT") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it },
                    label = { Text("Emoji") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = type == "DEBIT", onClick = { type = "DEBIT" })
                    Text("Debit")
                    Spacer(Modifier.width(16.dp))
                    RadioButton(selected = type == "CREDIT", onClick = { type = "CREDIT" })
                    Text("Credit")
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onSave(name, emoji, type) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EmojiEditDialog(
    currentName: String,
    currentEmoji: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var emoji by remember { mutableStateOf(currentEmoji) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Emoji for $currentName") },
        text = {
            OutlinedTextField(
                value = emoji,
                onValueChange = { emoji = it },
                label = { Text("Emoji") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onSave(emoji) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

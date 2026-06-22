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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ledgerpop.data.category.CategoryEngine
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.ui.viewmodel.SettingsViewModel

private data class CategoryDisplayInfo(
    val name: String,
    val emoji: String,
    val type: String,
    val isStandard: Boolean,
    val id: Int? = null
)

@Composable
fun CategoryManagementScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { LedgerPopDatabase.getInstance(context) }
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(db, context))
    val uiState by viewModel.uiState.collectAsState()

    val standardDebit = listOf(
        CategoryEngine.FOOD, CategoryEngine.GROCERIES, CategoryEngine.SHOPPING,
        CategoryEngine.TRAVEL, CategoryEngine.FUEL, CategoryEngine.BILLS,
        CategoryEngine.HEALTH, CategoryEngine.INSURANCE, CategoryEngine.INVESTMENTS,
        CategoryEngine.ENTERTAINMENT, CategoryEngine.EMI, CategoryEngine.TRANSFER,
        CategoryEngine.OTHER
    )
    val standardCredit = listOf(
        CategoryEngine.SALARY, CategoryEngine.INTEREST, CategoryEngine.DIVIDEND,
        CategoryEngine.REFUND
    )

    val customMap = uiState.customCategories.associateBy { it.name }
    
    val allCategoryInfo = remember(uiState.customCategories) {
        val list = mutableListOf<CategoryDisplayInfo>()
        // Standard
        (standardDebit + standardCredit).forEach { name ->
            val custom = customMap[name]
            val defaultType = if (name in standardCredit) "CREDIT" else "DEBIT"
            list.add(CategoryDisplayInfo(
                name = name,
                emoji = custom?.emoji ?: CategoryEngine.emoji(name),
                type = custom?.type ?: defaultType,
                isStandard = true,
                id = custom?.id
            ))
        }
        // Truly custom
        uiState.customCategories.filter { it.name !in standardDebit && it.name !in standardCredit }.forEach { cat ->
            list.add(CategoryDisplayInfo(
                name = cat.name,
                emoji = cat.emoji,
                type = cat.type,
                isStandard = false,
                id = cat.id
            ))
        }
        list
    }

    val debitCategories = allCategoryInfo.filter { it.type == "DEBIT" }
    val creditCategories = allCategoryInfo.filter { it.type == "CREDIT" }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryDisplayInfo?>(null) }

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
                "Categories",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Category")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (debitCategories.isNotEmpty()) {
                item {
                    Text(
                        "Debit Categories",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(debitCategories) { cat ->
                    CategoryRow(
                        name = cat.name,
                        emoji = cat.emoji,
                        type = cat.type,
                        isStandard = cat.isStandard,
                        onEdit = { editingCategory = cat },
                        onDelete = if (!cat.isStandard && cat.id != null) {
                            { viewModel.deleteCategory(cat.id) }
                        } else null
                    )
                }
            }

            if (creditCategories.isNotEmpty()) {
                item {
                    Text(
                        "Credit Categories",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                items(creditCategories) { cat ->
                    CategoryRow(
                        name = cat.name,
                        emoji = cat.emoji,
                        type = cat.type,
                        isStandard = cat.isStandard,
                        onEdit = { editingCategory = cat },
                        onDelete = if (!cat.isStandard && cat.id != null) {
                            { viewModel.deleteCategory(cat.id) }
                        } else null
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
        EditCategoryDialog(
            currentName = cat.name,
            currentEmoji = cat.emoji,
            currentType = cat.type,
            onDismiss = { editingCategory = null },
            onSave = { newName, newEmoji, newType ->
                viewModel.updateCategory(cat.id, cat.name, newName, newEmoji, newType)
                editingCategory = null
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
    onEdit: () -> Unit,
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
                    .clickable { onEdit() },
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 20.sp)
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name)
                    if (!isStandard) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "CUSTOM",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                Text(type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
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
fun EditCategoryDialog(
    currentName: String,
    currentEmoji: String,
    currentType: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var emoji by remember { mutableStateOf(currentEmoji) }
    var type by remember { mutableStateOf(currentType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Category") },
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
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

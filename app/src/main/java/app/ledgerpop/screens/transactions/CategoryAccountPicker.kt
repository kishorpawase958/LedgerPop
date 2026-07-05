package app.ledgerpop.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ledgerpop.data.category.CategoryEngine
import app.ledgerpop.data.local.AccountEntity
import app.ledgerpop.data.local.CustomCategoryEntity

@Composable
fun CategoryAccountPicker(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    customCategories: List<CustomCategoryEntity> = emptyList(),
    accounts: List<AccountEntity> = emptyList(),
    onUpdate: ((String, String, String) -> Unit)? = null,
    onAdd: ((String) -> Unit)? = null,
) {
    var newValue by remember { mutableStateOf("") }
    var editingItemName by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(28.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Add new entry row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newValue,
                        onValueChange = { newValue = it },
                        placeholder = { Text("Add new…") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    FilledTonalIconButton(
                        onClick = {
                            val trimmed = newValue.trim()
                            if (trimmed.isNotEmpty()) {
                                onAdd?.invoke(trimmed)
                                onSelect(trimmed)
                                onDismiss()
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add")
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                Spacer(Modifier.height(8.dp))

                val allOptions = options.filter { it.isNotBlank() }.distinct()

                if (allOptions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No existing options yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(allOptions) { option ->
                            val isCategory = title.contains("Category", ignoreCase = true)
                            val isAccount = title.contains("Account", ignoreCase = true)

                            val currentEmoji = when {
                                isCategory -> customCategories.find { it.name == option }?.emoji ?: CategoryEngine.emoji(option, customCategories)
                                isAccount -> accounts.find { it.name == option }?.icon ?: "🏦"
                                else -> null
                            }

                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            onSelect(option)
                                            onDismiss()
                                        }
                                        .padding(vertical = 8.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        if (currentEmoji != null) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                                    .clickable { editingItemName = option },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(currentEmoji, fontSize = 18.sp)
                                            }
                                            Spacer(Modifier.width(12.dp))
                                        }
                                        Text(
                                            text = option,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (option == selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if ((isCategory || isAccount) && onUpdate != null) {
                                            IconButton(onClick = { editingItemName = option }) {
                                                Icon(Icons.Rounded.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                                            }
                                        }
                                        if (option == selected) {
                                            Icon(
                                                Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                                if (option != allOptions.last()) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }

    editingItemName?.let { oldName ->
        val isCategory = title.contains("Category", ignoreCase = true)
        val initialEmoji = if (isCategory) {
            customCategories.find { it.name == oldName }?.emoji ?: CategoryEngine.emoji(oldName, customCategories)
        } else {
            accounts.find { it.name == oldName }?.icon ?: "🏦"
        }
        
        var tempName by remember { mutableStateOf(oldName) }
        var tempEmoji by remember { mutableStateOf(initialEmoji) }

        AlertDialog(
            onDismissRequest = { editingItemName = null },
            title = { Text("Edit ${if (isCategory) "Category" else "Account"}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempEmoji,
                        onValueChange = { tempEmoji = it },
                        label = { Text("Emoji / Icon") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdate?.invoke(oldName, tempName.trim(), tempEmoji.trim())
                        if (oldName == selected) {
                            onSelect(tempName.trim())
                        }
                        editingItemName = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItemName = null }) { Text("Cancel") }
            }
        )
    }
}

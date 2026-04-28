package app.ledgerpop.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.ui.state.AppTheme
import app.ledgerpop.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToPermissions: () -> Unit,
    onNavigateToSmsAudit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember { LedgerPopDatabase.getInstance(context) }
    val factory = remember(db, context) { SettingsViewModel.factory(db, context) }
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()

    // Handle Import Results
    LaunchedEffect(uiState.lastImportResult) {
        uiState.lastImportResult?.let { result ->
            val message = "Import Complete: ${result.imported} Success, ${result.failed} Failed, ${result.skipped} Skipped"
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearImportResult()
        }
    }

    // Refresh permissions on Resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Permission Launchers
    val readLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshPermissions()
        if (granted) viewModel.importSms()
    }

    val rangeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshPermissions()
        if (granted) viewModel.showDateRangePicker()
    }

    var showNameDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        ProfileSection(uiState.userName) { showNameDialog = true }

        PreferencesSection(
            appTheme = uiState.appTheme,
            onThemeClick = { showThemeDialog = true },
            onPermissionsClick = onNavigateToPermissions
        )

        DataImportSection(
            isImporting = uiState.isImporting,
            onFullScan = { checkAndRequestPermission(context, uiState.hasReadSmsPermission, readLauncher) { viewModel.importSms() } },
            onRangeScan = { checkAndRequestPermission(context, uiState.hasReadSmsPermission, rangeLauncher) { viewModel.showDateRangePicker() } }
        )

        ManagementSection(
            onSmsAudit = onNavigateToSmsAudit,
            onBackup = { viewModel.backupData(context) },
            onRestore = { viewModel.restoreData(context, null) },
            onClearAll = { showClearDialog = true },
            onRestart = { restartApp(context) }
        )

        AboutSection()
        
        Spacer(Modifier.height(100.dp))
    }

    // Dialogs
    if (showThemeDialog) {
        ThemeDialog(
            currentTheme = uiState.appTheme,
            onThemeSelected = { viewModel.updateTheme(it) },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showNameDialog) {
        NameEditDialog(
            currentName = uiState.userName,
            onSave = { viewModel.updateUserName(it) },
            onDismiss = { showNameDialog = false }
        )
    }

    if (showClearDialog) {
        ClearDataDialog(
            onConfirm = { viewModel.deleteAll() },
            onDismiss = { showClearDialog = false }
        )
    }

    if (uiState.showDateRangePicker) {
        DateRangeImportDialog(
            onImport = { start, end ->
                viewModel.onDateRangeSelected(start, end)
                viewModel.importSmsWithDateRange()
            },
            onDismiss = { viewModel.hideDateRangePicker() }
        )
    }
}

private fun checkAndRequestPermission(
    context: Context,
    hasPermission: Boolean,
    launcher: ManagedActivityResultLauncher<String, Boolean>,
    onGranted: () -> Unit
) {
    if (hasPermission) {
        onGranted()
    } else {
        Toast.makeText(context, "Please grant 'Read SMS' permission to import transactions", Toast.LENGTH_LONG).show()
        launcher.launch(Manifest.permission.READ_SMS)
    }
}

private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
}

@Composable
private fun ProfileSection(userName: String, onClick: () -> Unit) {
    SectionCard(title = "Profile") {
        MiniCard(onClick = onClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Display Name", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(userName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PreferencesSection(
    appTheme: AppTheme,
    onThemeClick: () -> Unit,
    onPermissionsClick: () -> Unit
) {
    SectionCard(title = "Preferences") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniCard(onClick = onThemeClick) {
                SettingRow(
                    icon = Icons.Rounded.Palette,
                    title = "App Theme",
                    subtitle = appTheme.name.lowercase().replaceFirstChar { it.uppercase() }
                )
            }
            MiniCard(onClick = onPermissionsClick) {
                SettingRow(
                    icon = Icons.Rounded.Security,
                    title = "Permissions",
                    subtitle = "Manage SMS and background access"
                )
            }
        }
    }
}

@Composable
private fun DataImportSection(
    isImporting: Boolean,
    onFullScan: () -> Unit,
    onRangeScan: () -> Unit
) {
    SectionCard(title = "Data Import") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniCard(onClick = onFullScan) {
                SettingRow(
                    icon = Icons.Rounded.Download,
                    title = "Full Inbox Scan",
                    subtitle = "Import all historical transactions",
                    trailing = {
                        if (isImporting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                )
            }
            MiniCard(onClick = onRangeScan) {
                SettingRow(
                    icon = Icons.Rounded.DateRange,
                    title = "Date Range Import",
                    subtitle = "Select specific timeframe"
                )
            }
        }
    }
}

@Composable
private fun ManagementSection(
    onSmsAudit: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onClearAll: () -> Unit,
    onRestart: () -> Unit
) {
    SectionCard(title = "Management") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniCard(onClick = onSmsAudit) {
                SettingRow(
                    icon = Icons.AutoMirrored.Rounded.ManageSearch,
                    title = "SMS Audit",
                    subtitle = "Review detected bank messages"
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    MiniCard(onClick = onBackup) {
                        Text("Backup", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Medium)
                    }
                }
                Box(Modifier.weight(1f)) {
                    MiniCard(onClick = onRestore) {
                        Text("Restore", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Medium)
                    }
                }
            }
            
            MiniCard(
                onClick = onClearAll,
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(12.dp))
                    Text("Clear All Data", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            }

            MiniCard(onClick = onRestart) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Restart App", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun AboutSection() {
    SectionCard(title = "About") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("LedgerPop", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Version 1.0.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(
                "Smart SMS expense tracker.\nSecure & on-device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun ThemeDialog(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App Theme") },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column {
                ThemeOption("Auto (System Default)", currentTheme == AppTheme.AUTO) {
                    onThemeSelected(AppTheme.AUTO)
                    onDismiss()
                }
                ThemeOption("Light Theme", currentTheme == AppTheme.LIGHT) {
                    onThemeSelected(AppTheme.LIGHT)
                    onDismiss()
                }
                ThemeOption("Dark Theme", currentTheme == AppTheme.DARK) {
                    onThemeSelected(AppTheme.DARK)
                    onDismiss()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
private fun NameEditDialog(
    currentName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var tempName by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Edit Profile Name") },
        text = {
            OutlinedTextField(
                value = tempName,
                onValueChange = { tempName = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (tempName.isNotBlank()) {
                        onSave(tempName)
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ClearDataDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Clear All Data") },
        text = { Text("This will permanently erase all transactions and audit logs from your device.") },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Clear Everything") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Discard") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeImportDialog(
    onImport: (start: Long, end: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val dateRangePickerState = rememberDateRangePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        confirmButton = {
            TextButton(
                onClick = {
                    val start = dateRangePickerState.selectedStartDateMillis
                    val end = dateRangePickerState.selectedEndDateMillis
                    if (start != null && end != null) {
                        onImport(start, end)
                    }
                }
            ) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DateRangePicker(
            state = dateRangePickerState,
            modifier = Modifier.height(500.dp)
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
            fontWeight = FontWeight.Bold
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun MiniCard(
    onClick: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ThemeOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

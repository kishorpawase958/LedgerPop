package app.ledgerpop.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import app.ledgerpop.R
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.ui.state.AppTheme
import app.ledgerpop.ui.state.AppLogo
import app.ledgerpop.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToPermissions: () -> Unit,
    onNavigateToSmsAudit: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember { LedgerPopDatabase.getInstance(context) }
    val factory = remember(db, context) { SettingsViewModel.factory(db, context) }
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.lastImportResult, uiState.lastImportMessage) {
        val result = uiState.lastImportResult
        val message = uiState.lastImportMessage
        
        if (result != null) {
            val toastMsg = "Messages scanned: ${result.scanned} --> Transactions imported: ${result.imported}"
            Toast.makeText(context, toastMsg, Toast.LENGTH_LONG).show()
            viewModel.clearImportResult()
        } else if (message.isNotBlank()) {
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

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.restoreData(context, uri)
    }

    val csvImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.importFromCsv(uri)
    }

    var pendingBackupAfterFolderSelection by remember { mutableStateOf(false) }
    var pendingAutoBackupAfterFolderSelection by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val documentFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
            val folderName = documentFile?.name ?: "Selected Folder"
            viewModel.updateBackupFolder(uri, folderName)
            
            if (pendingBackupAfterFolderSelection) {
                viewModel.performManualBackup()
                pendingBackupAfterFolderSelection = false
            }
            if (pendingAutoBackupAfterFolderSelection) {
                viewModel.updateAutoBackupEnabled(true)
                pendingAutoBackupAfterFolderSelection = false
            }
        } else {
            pendingBackupAfterFolderSelection = false
            pendingAutoBackupAfterFolderSelection = false
        }
    }

    var showNameDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLogoDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showFullScanConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (uiState.isImporting) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        ProfileSection(uiState.userName) { showNameDialog = true }

        PreferencesSection(
            appTheme = uiState.appTheme,
            appLogo = uiState.appLogo,
            onThemeClick = { showThemeDialog = true },
            onLogoClick = { showLogoDialog = true },
            onPermissionsClick = onNavigateToPermissions,
            onCategoriesClick = onNavigateToCategories,
            onAccountsClick = onNavigateToAccounts
        )

        DataImportSection(
            isImporting = uiState.isImporting,
            onFullScan = { showFullScanConfirmDialog = true },
            onRangeScan = { checkAndRequestPermission(context, uiState.hasReadSmsPermission, rangeLauncher) { viewModel.showDateRangePicker() } },
            onCsvImport = { csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/octet-stream")) }
        )

        ManagementSection(
            onSmsAudit = onNavigateToSmsAudit,
            onBackup = {
                if (uiState.backupFolderUri == null) {
                    Toast.makeText(context, "Please select a backup folder first", Toast.LENGTH_LONG).show()
                    pendingBackupAfterFolderSelection = true
                    folderPickerLauncher.launch(null)
                } else {
                    viewModel.performManualBackup()
                }
            },
            onRestore = { restoreLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
            onClearAll = { showClearDialog = true },
            onRestart = { restartApp(context) },
            isAutoBackupEnabled = uiState.isAutoBackupEnabled,
            onAutoBackupToggle = { enabled ->
                if (enabled && uiState.backupFolderUri == null) {
                    Toast.makeText(context, "Please select a backup folder to enable auto-backup", Toast.LENGTH_LONG).show()
                    pendingAutoBackupAfterFolderSelection = true
                    folderPickerLauncher.launch(null)
                } else {
                    viewModel.updateAutoBackupEnabled(enabled)
                }
            },
            backupFrequency = uiState.backupFrequency,
            onFrequencyChange = { viewModel.updateBackupFrequency(it) },
            backupFolderName = uiState.backupFolderName ?: "Not Selected",
            onSelectFolder = { folderPickerLauncher.launch(null) }
        )

        AboutSection()
        
        Spacer(Modifier.height(100.dp))
    }

    // Dialogs
    if (showThemeDialog) {
        ThemeDialog(
            currentTheme = uiState.appTheme,
            onThemeSelected = {
                viewModel.updateTheme(it)
                restartApp(context)
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showLogoDialog) {
        LogoSelectionDialog(
            currentLogo = uiState.appLogo,
            onLogoSelected = {
                viewModel.updateAppLogo(it)

                restartApp(context)
            },
            onDismiss = { showLogoDialog = false }
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

    if (showFullScanConfirmDialog) {
        FullScanConfirmDialog(
            onConfirm = { checkAndRequestPermission(context, uiState.hasReadSmsPermission, readLauncher) { viewModel.importSms() } },
            onDismiss = { showFullScanConfirmDialog = false }
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
                    Text(userName, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun PreferencesSection(
    appTheme: AppTheme,
    appLogo: AppLogo,
    onThemeClick: () -> Unit,
    onLogoClick: () -> Unit,
    onPermissionsClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onAccountsClick: () -> Unit
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
            
            MiniCard(onClick = onLogoClick) {
                SettingRow(
                    icon = Icons.Rounded.AppShortcut,
                    title = "App Logo",
                    subtitle = appLogo.name.lowercase().replaceFirstChar { it.uppercase() }
                )
            }

            MiniCard(onClick = onAccountsClick) {
                SettingRow(
                    icon = Icons.Rounded.AccountBalance,
                    title = "Accounts",
                    subtitle = "Manage your banks and wallets"
                )
            }
            MiniCard(onClick = onCategoriesClick) {
                SettingRow(
                    icon = Icons.Rounded.Category,
                    title = "Categories",
                    subtitle = "Manage category names and emojis"
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
    onRangeScan: () -> Unit,
    onCsvImport: () -> Unit
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
            MiniCard(onClick = onCsvImport) {
                SettingRow(
                    icon = Icons.Rounded.FileOpen,
                    title = "Import from CSV",
                    subtitle = "Load data from a CSV file"
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
    onRestart: () -> Unit,
    isAutoBackupEnabled: Boolean,
    onAutoBackupToggle: (Boolean) -> Unit,
    backupFrequency: String,
    onFrequencyChange: (String) -> Unit,
    backupFolderName: String,
    onSelectFolder: () -> Unit
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

            MiniCard(onClick = { onAutoBackupToggle(!isAutoBackupEnabled) }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CloudUpload, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Auto Backup", style = MaterialTheme.typography.bodyLarge)
                        Text("Periodic background backup", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isAutoBackupEnabled,
                        onCheckedChange = onAutoBackupToggle,
                        thumbContent = if (isAutoBackupEnabled) {
                            { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                        } else null
                    )
                }
            }

            if (isAutoBackupEnabled) {
                MiniCard(onClick = onSelectFolder) {
                    SettingRow(
                        icon = Icons.Rounded.Folder,
                        title = "Backup Location",
                        subtitle = backupFolderName
                    )
                }

                var showFrequencyMenu by remember { mutableStateOf(false) }
                MiniCard(onClick = { showFrequencyMenu = true }) {
                    SettingRow(
                        icon = Icons.Rounded.Schedule,
                        title = "Backup Frequency",
                        subtitle = backupFrequency,
                        trailing = {
                            Box {
                                Icon(Icons.Rounded.ArrowDropDown, null)
                                DropdownMenu(
                                    expanded = showFrequencyMenu,
                                    onDismissRequest = { showFrequencyMenu = false }
                                ) {
                                    listOf("Hourly", "Daily", "Weekly", "Monthly").forEach { freq ->
                                        DropdownMenuItem(
                                            text = { Text(freq) },
                                            onClick = {
                                                onFrequencyChange(freq)
                                                showFrequencyMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    MiniCard(onClick = onBackup) {
                        Text("Backup Now", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                Box(Modifier.weight(1f)) {
                    MiniCard(onClick = onRestore) {
                        Text("Restore", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
                    Text("Clear All Data", color = MaterialTheme.colorScheme.error)
                }
            }

            MiniCard(onClick = onRestart) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Restart App")
                }
            }
        }
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val versionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
        } catch (_: Exception) {
            "1.0.5"
        }
    }

    SectionCard(title = "About") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "LedgerPop",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Version $versionName",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "Smart SMS expense tracker.\nYour data stays private and on-device",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

        }
    }
}

@Composable
private fun LogoSelectionDialog(
    currentLogo: AppLogo,
    onLogoSelected: (AppLogo) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose App Logo") },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LogoSelectionItem(
                        name = "Default",
                        logo = AppLogo.DEFAULT,
                        isSelected = currentLogo == AppLogo.DEFAULT,
                        onSelect = { onLogoSelected(AppLogo.DEFAULT) },
                        modifier = Modifier.weight(1f)
                    )
                    LogoSelectionItem(
                        name = "Light",
                        logo = AppLogo.LIGHT,
                        isSelected = currentLogo == AppLogo.LIGHT,
                        onSelect = { onLogoSelected(AppLogo.LIGHT) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LogoSelectionItem(
                        name = "Dark",
                        logo = AppLogo.DARK,
                        isSelected = currentLogo == AppLogo.DARK,
                        onSelect = { onLogoSelected(AppLogo.DARK) },
                        modifier = Modifier.weight(1f)
                    )
                    LogoSelectionItem(
                        name = "Navy",
                        logo = AppLogo.NAVY,
                        isSelected = currentLogo == AppLogo.NAVY,
                        onSelect = { onLogoSelected(AppLogo.NAVY) },
                        modifier = Modifier.weight(1f)
                    )
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
private fun LogoSelectionItem(
    name: String,
    logo: AppLogo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val logoRes = when (logo) {
        AppLogo.LIGHT -> R.mipmap.ic_launcher_light
        AppLogo.DARK -> R.mipmap.ic_launcher_dark
        AppLogo.NAVY -> R.mipmap.ic_launcher_navy
        else -> R.mipmap.ic_launcher
    }

    val painter = rememberLauncherPainter(logoRes)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .clickable { onSelect() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.foundation.Image(
            painter = painter,
            contentDescription = name,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    if (isSelected) 2.dp else 1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                    RoundedCornerShape(14.dp)
                )
        )
        Spacer(Modifier.height(8.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
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
                ThemeOption("Midnight Navy", currentTheme == AppTheme.MIDNIGHT) {
                    onThemeSelected(AppTheme.MIDNIGHT)
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
private fun FullScanConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Full Inbox Scan") },
        text = { Text("This will scan all your SMS messages to find and import historical transactions. This might take a few moments.") },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp)
            ) { Text("Import") }
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
                        // Ensure end date covers the full day (up to 23:59:59)
                        val endOfDay = end + (24 * 60 * 60 * 1000) - 1
                        onImport(start, endOfDay)
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
            modifier = Modifier.height(500.dp),
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

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
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
            Text(title, style = MaterialTheme.typography.bodyLarge)
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

@Composable
private fun rememberLauncherPainter(logoRes: Int): androidx.compose.ui.graphics.painter.Painter {
    val context = LocalContext.current
    return remember(logoRes) {
        val drawable = ContextCompat.getDrawable(context, logoRes)!!
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        BitmapPainter(bitmap.asImageBitmap())
    }
}

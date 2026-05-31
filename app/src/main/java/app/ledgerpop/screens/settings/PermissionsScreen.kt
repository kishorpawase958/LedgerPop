package app.ledgerpop.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.ui.viewmodel.SettingsViewModel

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { LedgerPopDatabase.getInstance(context) }
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(db, context))
    val uiState by viewModel.uiState.collectAsState()

    // Permission Request Launchers
    val readLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshPermissions()
        showToast(context, granted)
    }

    val receiveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshPermissions()
        showToast(context, granted)
    }

    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshPermissions()
        showToast(context, granted)
    }

    // Dynamic state synchronizer when returning from Android System Settings Page
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Bar Header Action Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                "Permissions",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Manage app permissions", color = MaterialTheme.colorScheme.onSurfaceVariant)

            // 1. READ SMS CARD
            PermissionCard(
                icon = Icons.AutoMirrored.Rounded.Message,
                title = "Read SMS",
                desc = "Scan historical bank messages for setup",
                status = if (uiState.hasReadSmsPermission) "✓ Granted" else "Access required",
                granted = uiState.hasReadSmsPermission,
                buttonText = if (uiState.hasReadSmsPermission) "Revoke Access" else "Grant Access",
                onAction = {
                    if (uiState.hasReadSmsPermission) {
                        openAppSettings(context)
                    } else {
                        readLauncher.launch(Manifest.permission.READ_SMS)
                    }
                }
            )

            // 2. RECEIVE SMS CARD
            PermissionCard(
                icon = Icons.Rounded.Sms,
                title = "Receive SMS",
                desc = "Real-time background transaction tracking",
                status = if (uiState.hasReceiveSmsPermission) "✓ Granted" else "Access required",
                granted = uiState.hasReceiveSmsPermission,
                buttonText = if (uiState.hasReceiveSmsPermission) "Revoke Access" else "Grant Access",
                onAction = {
                    if (uiState.hasReceiveSmsPermission) {
                        openAppSettings(context)
                    } else {
                        receiveLauncher.launch(Manifest.permission.RECEIVE_SMS)
                    }
                }
            )

            // 3. PUSH NOTIFICATIONS CARD (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionCard(
                    icon = Icons.Rounded.Notifications,
                    title = "Notifications",
                    desc = "Real-time transaction summary banners",
                    status = if (uiState.hasNotificationsPermission) "✓ Granted" else "Access required",
                    granted = uiState.hasNotificationsPermission,
                    buttonText = if (uiState.hasNotificationsPermission) "Revoke Access" else "Grant Access",
                    onAction = {
                        if (uiState.hasNotificationsPermission) {
                            openAppSettings(context)
                        } else {
                            notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }

            // 4. STORAGE CONFIGURATION PLACEHOLDER
            PermissionCard(
                icon = Icons.Rounded.Folder,
                title = "Storage",
                desc = "CSV import/export uses isolated system picker",
                status = "✓ System handled",
                granted = true,
                buttonText = "Managed by OS",
                enabled = false,
                onAction = {}
            )

            // 5. PRIVACY POLICY INFO CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Privacy Protection", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "LedgerPop operates fully client-side. Your financial SMS texts and database parameters never leave this physical device.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { openAppSettings(context) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Text("Open App System Settings")
                    }
                }
            }
        }
    }
}

/**
 * Reusable layout abstraction mapping specific permission states to custom actionable rows.
 */
@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    desc: String,
    status: String,
    granted: Boolean,
    buttonText: String,
    enabled: Boolean = true,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            color = if (granted) MaterialTheme.colorScheme.primary.copy(0.12f)
                            else MaterialTheme.colorScheme.error.copy(0.12f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(status, color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(4.dp))
                    Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(
                onClick = onAction,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = if (granted) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) else ButtonDefaults.buttonColors()
            ) {
                Text(buttonText)
            }
        }
    }
}

/**
 * Cleanly redirects the user to their native system settings pane to modify permissions safely.
 */
private fun openAppSettings(context: Context) {
    Toast.makeText(context, "Please modify application permissions inside Settings", Toast.LENGTH_LONG).show()
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        // Fallback catch block in case device manufacturer customizes Intent behavior flags
        val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
        context.startActivity(fallbackIntent)
    }
}

private fun showToast(context: Context, granted: Boolean) {
    val message = if (granted) "Permission Access Granted" else "Permission Access Denied"
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
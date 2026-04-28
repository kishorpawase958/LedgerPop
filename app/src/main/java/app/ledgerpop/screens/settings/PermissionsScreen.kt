package app.ledgerpop.screens.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { LedgerPopDatabase.getInstance(context) }
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(db, context))
    val uiState by viewModel.uiState.collectAsState()

    val readLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshPermissions()
        if (granted) Toast.makeText(context, "Access Granted", Toast.LENGTH_SHORT).show()
        else Toast.makeText(context, "Access Denied", Toast.LENGTH_SHORT).show()
    }

    val receiveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshPermissions()
        if (granted) Toast.makeText(context, "Access Granted", Toast.LENGTH_SHORT).show()
        else Toast.makeText(context, "Access Denied", Toast.LENGTH_SHORT).show()
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Manage app permissions", color = MaterialTheme.colorScheme.onSurfaceVariant)

            PermissionCard(
                icon = Icons.AutoMirrored.Rounded.Message,
                title = "Read SMS",
                desc = "Scan bank messages for transactions",
                status = if (uiState.hasReadSmsPermission) "✓ Granted" else "Access required",
                granted = uiState.hasReadSmsPermission,
                buttonText = if (uiState.hasReadSmsPermission) "Revoke Access" else "Grant Access",
                onAction = {
                    if (uiState.hasReadSmsPermission) {
                        revokePermission(context, Manifest.permission.READ_SMS)
                    } else {
                        readLauncher.launch(Manifest.permission.READ_SMS)
                    }
                }
            )

            PermissionCard(
                icon = Icons.Rounded.Sms,
                title = "Receive SMS",
                desc = "Real-time SMS detection",
                status = if (uiState.hasReceiveSmsPermission) "✓ Granted" else "Access required",
                granted = uiState.hasReceiveSmsPermission,
                buttonText = if (uiState.hasReceiveSmsPermission) "Revoke Access" else "Grant Access",
                onAction = {
                    if (uiState.hasReceiveSmsPermission) {
                        revokePermission(context, Manifest.permission.RECEIVE_SMS)
                    } else {
                        if (uiState.hasReadSmsPermission) {
                            receiveLauncher.launch(Manifest.permission.RECEIVE_SMS)
                        } else {
                            Toast.makeText(context, "Please grant 'Read SMS' permission first", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )

            // Storage is usually handled by SAF or simple picker nowadays, keeping as placeholder
            PermissionCard(
                icon = Icons.Rounded.Folder,
                title = "Storage",
                desc = "CSV import/export uses system picker",
                status = "✓ System handled",
                granted = true,
                buttonText = "Revoke Access",
                enabled = false,
                onAction = {}
            )

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("Privacy", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Data stays on your device", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Text("App Settings")
                    }
                }
            }
        }
    }
}

private fun revokePermission(context: android.content.Context, permission: String) {
    Toast.makeText(context, "Access Revoked", Toast.LENGTH_SHORT).show()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.revokeSelfPermissionOnKill(permission)
    } else {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }
}

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
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .background(
                            if (granted) MaterialTheme.colorScheme.primary.copy(0.12f)
                            else MaterialTheme.colorScheme.error.copy(0.12f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        null,
                        tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
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

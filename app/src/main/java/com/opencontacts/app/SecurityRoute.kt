package com.opencontacts.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SecurityRoute(
    onBack: () -> Unit,
    viewModel: AppViewModel = hiltViewModel(),
) {
    val settings by viewModel.appLockSettings.collectAsStateWithLifecycle()
    val pinError by viewModel.pinError.collectAsStateWithLifecycle()
    val storageMessage by viewModel.storageMessage.collectAsStateWithLifecycle()
    val vaults by viewModel.vaults.collectAsStateWithLifecycle()
    val activeVaultId by viewModel.activeVaultId.collectAsStateWithLifecycle()
    val blockedContacts by viewModel.blockedContacts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var retentionDays by remember(settings.trashRetentionDays) { mutableStateOf(settings.trashRetentionDays.toString()) }

    val backupFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) Toast.makeText(context, "Backup folder selection cancelled", Toast.LENGTH_SHORT).show()
        else viewModel.setBackupFolder(uri)
    }
    val exportFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) Toast.makeText(context, "Export folder selection cancelled", Toast.LENGTH_SHORT).show()
        else viewModel.setExportFolder(uri)
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer, shape = CardDefaults.elevatedShape) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Settings & security", style = MaterialTheme.typography.headlineMedium)
                        Text("Manage lock options, theme mode, trash retention, and vault deletion from one place.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecurityStat("PIN", if (settings.hasPin) "Enabled" else "Off")
                            SecurityStat("Biometric", if (settings.biometricEnabled) "Enabled" else "Off")
                            SecurityStat("Theme", settings.themeMode)
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, contentDescription = null)
                            Text("Appearance", style = MaterialTheme.typography.titleLarge)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = settings.themeMode == "LIGHT", onClick = { viewModel.setThemeMode("LIGHT") }, label = { Text("Light") })
                            FilterChip(selected = settings.themeMode == "DARK", onClick = { viewModel.setThemeMode("DARK") }, label = { Text("Dark") })
                            FilterChip(selected = settings.themeMode == "SYSTEM", onClick = { viewModel.setThemeMode("SYSTEM") }, label = { Text("System") })
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Password, contentDescription = null)
                            Text("PIN", style = MaterialTheme.typography.titleLarge)
                        }
                        OutlinedTextField(
                            value = pin,
                            onValueChange = {
                                pin = it
                                viewModel.clearError()
                            },
                            label = { Text(if (settings.hasPin) "Change PIN" else "Set PIN") },
                            singleLine = true,
                            supportingText = {
                                if (pinError != null) Text(pinError ?: "") else Text("Use at least 4 digits")
                            },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.setPin(pin) }) { Text(if (settings.hasPin) "Update PIN" else "Save PIN") }
                            if (settings.hasPin) Button(onClick = viewModel::clearPin) { Text("Clear PIN") }
                        }
                    }
                }
            }

            if (viewModel.canUseBiometric()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null)
                                Column {
                                    Text("Biometric unlock", style = MaterialTheme.typography.titleMedium)
                                    Text("Use fingerprint or face unlock when available")
                                }
                            }
                            Switch(
                                checked = settings.biometricEnabled,
                                onCheckedChange = { enabled ->
                                    if (!enabled) {
                                        viewModel.setBiometricEnabled(false)
                                    } else {
                                        val activity = context.findFragmentActivity() ?: run {
                                            viewModel.showUiError("Biometric unlock is unavailable on this screen.")
                                            return@Switch
                                        }
                                        runCatching {
                                            val prompt = BiometricPrompt(
                                                activity,
                                                ContextCompat.getMainExecutor(activity),
                                                object : BiometricPrompt.AuthenticationCallback() {
                                                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                                        super.onAuthenticationSucceeded(result)
                                                        viewModel.setBiometricEnabled(true)
                                                    }

                                                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                                        super.onAuthenticationError(errorCode, errString)
                                                        viewModel.showUiError(errString.toString())
                                                    }
                                                },
                                            )
                                            prompt.authenticate(viewModel.biometricPromptInfo("OpenContacts"))
                                        }.onFailure {
                                            viewModel.showUiError(it.message ?: "Unable to start biometric prompt")
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, contentDescription = null)
                            Text("Export & backup locations", style = MaterialTheme.typography.titleLarge)
                        }
                        storageMessage?.let { message ->
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(message, modifier = Modifier.weight(1f))
                                    TextButton(onClick = viewModel::clearStorageMessage) { Text("Dismiss") }
                                }
                            }
                        }
                        FolderDestinationSetting(
                            title = "Backup folder",
                            currentValue = settings.backupFolderName ?: "App storage/${settings.backupPath}",
                            selectedViaSystemPicker = settings.backupFolderUri != null,
                            onChoose = { backupFolderPicker.launch(null) },
                            onReset = if (settings.backupFolderUri != null) viewModel::resetBackupFolder else null,
                        )
                        HorizontalDivider()
                        FolderDestinationSetting(
                            title = "Export folder",
                            currentValue = settings.exportFolderName ?: "App storage/${settings.exportPath}",
                            selectedViaSystemPicker = settings.exportFolderUri != null,
                            onChoose = { exportFolderPicker.launch(null) },
                            onReset = if (settings.exportFolderUri != null) viewModel::resetExportFolder else null,
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, contentDescription = null)
                            Text("Trash retention", style = MaterialTheme.typography.titleLarge)
                        }
                        OutlinedTextField(
                            value = retentionDays,
                            onValueChange = { retentionDays = it.filter(Char::isDigit) },
                            label = { Text("Keep deleted contacts for days") },
                            singleLine = true,
                        )
                        Button(onClick = { viewModel.setTrashRetentionDays(retentionDays.toIntOrNull() ?: settings.trashRetentionDays) }) {
                            Text("Save retention")
                        }
                    }
                }
            }


            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Text("Blocked contacts", style = MaterialTheme.typography.titleLarge)
                        }
                        if (blockedContacts.isEmpty()) {
                            Text("No blocked contacts in the active vault.")
                        } else {
                            blockedContacts.forEach { contact ->
                                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(contact.displayName, style = MaterialTheme.typography.titleMedium)
                                            Text(contact.primaryPhone ?: "No phone number", style = MaterialTheme.typography.bodySmall)
                                        }
                                        TextButton(onClick = { viewModel.unblockContact(contact.id) }) { Text("Unblock") }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Text("Immediate actions", style = MaterialTheme.typography.titleLarge)
                        }
                        Button(onClick = viewModel::lockNow) { Text("Lock app now") }
                    }
                }
            }

            item {
                Text("Vault deletion", style = MaterialTheme.typography.titleLarge)
            }
            items(vaults, key = { it.id }) { vault ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(vault.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                when {
                                    vault.id == activeVaultId -> "Current vault"
                                    vault.isLocked -> "Locked"
                                    else -> "Private vault"
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(
                            onClick = { viewModel.deleteVault(vault.id) },
                            enabled = vault.id != activeVaultId && vaults.size > 1,
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = "Delete vault")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderDestinationSetting(
    title: String,
    currentValue: String,
    selectedViaSystemPicker: Boolean,
    onChoose: () -> Unit,
    onReset: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = currentValue,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onChoose) {
                Text(if (selectedViaSystemPicker) "Change folder" else "Choose folder")
            }
            if (onReset != null) {
                TextButton(onClick = onReset) { Text("Reset to default") }
            }
        }
    }
}

@Composable
private fun SecurityStat(label: String, value: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = CardDefaults.shape) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun android.content.Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is android.content.ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

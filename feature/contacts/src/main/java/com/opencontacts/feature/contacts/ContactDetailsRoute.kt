package com.opencontacts.feature.contacts

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.opencontacts.core.model.ContactDetails
import com.opencontacts.core.model.ContactDraft
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.model.FolderSummary
import com.opencontacts.core.model.TagSummary
import com.opencontacts.core.vault.VaultSessionManager
import com.opencontacts.domain.contacts.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContactDetailsRoute(
    onBack: () -> Unit,
    viewModel: ContactDetailsViewModel = hiltViewModel(),
) {
    val details by viewModel.details.collectAsStateWithLifecycle()
    val noteEditor by viewModel.noteEditor.collectAsStateWithLifecycle()
    val contactEditor by viewModel.contactEditor.collectAsStateWithLifecycle()
    val availableTags by viewModel.tags.collectAsStateWithLifecycle()
    val availableFolders by viewModel.folders.collectAsStateWithLifecycle()
    val callHistory by viewModel.callHistory.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var folderEditorOpen by remember { mutableStateOf(false) }
    var tagEditorOpen by remember { mutableStateOf(false) }
    var qrPayload by remember { mutableStateOf<String?>(null) }
    var blockConfirmation by remember { mutableStateOf<Boolean?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.setPhoto(it.toString()) }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text("Contact details", style = MaterialTheme.typography.titleLarge)
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("Share as text") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { details?.let { shareAsText(context, it.contact) }; expanded = false })
                        DropdownMenuItem(text = { Text("Share as file") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { details?.let { shareAsVcfFile(context, it.contact) }; expanded = false })
                        DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { details?.let { viewModel.startEdit(it.contact) }; expanded = false })
                        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { viewModel.deleteContact(); expanded = false; onBack() })
                    }
                }
            }

            if (details == null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Contact not found or vault is locked", style = MaterialTheme.typography.titleLarge)
                    }
                }
            } else {
                ContactDetailsContent(
                    details = details!!,
                    availableTags = availableTags,
                    availableFolders = availableFolders,
                    callHistory = callHistory,
                    onCall = {
                        details!!.contact.primaryPhone?.takeIf { it.isNotBlank() }?.let { phone ->
                            val hasCallPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            val intent = if (hasCallPermission) Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone")) else Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            context.startActivity(intent)
                        }
                    },
                    onOpenWhatsApp = { details!!.contact.primaryPhone?.let { openWhatsApp(context, it) } },
                    onOpenTelegram = { details!!.contact.primaryPhone?.let { openTelegram(context, it) } },
                    onAddNote = viewModel::startAddNote,
                    onAddPhoto = { photoPicker.launch("image/*") },
                    onRemovePhoto = viewModel::removePhoto,
                    onEditFolder = { folderEditorOpen = true },
                    onClearFolder = viewModel::clearFolder,
                    onAddTag = { tagEditorOpen = true },
                    onRemoveTag = viewModel::removeTag,
                    onOpenQr = {
                        qrPayload = details?.contact?.let(::buildVCardPayload)
                    },
                    onToggleBlocked = { blocked -> blockConfirmation = blocked },
                )
            }
        }

        noteEditor?.let { state ->
            SimpleTextDialog(
                title = "Add note",
                value = state,
                label = "Encrypted note",
                onValueChange = viewModel::updateNoteEditor,
                onDismiss = viewModel::dismissNoteEditor,
                onConfirm = viewModel::saveNote,
            )
        }

        contactEditor?.let { state ->
            ContactEditorFullScreen(
                state = state,
                onStateChange = viewModel::updateContactEditor,
                onDismiss = viewModel::dismissContactEditor,
                onConfirm = viewModel::saveContactEditor,
            )
        }

        if (folderEditorOpen) {
            FolderPickerDialog(
                currentValue = details?.contact?.folderName.orEmpty(),
                folders = availableFolders,
                onDismiss = { folderEditorOpen = false },
                onConfirm = {
                    viewModel.updateFolder(it)
                    folderEditorOpen = false
                },
            )
        }

        if (tagEditorOpen) {
            TagPickerDialog(
                tags = availableTags,
                onDismiss = { tagEditorOpen = false },
                onConfirm = {
                    viewModel.addTag(it)
                    tagEditorOpen = false
                },
            )
        }

        qrPayload?.let { payload ->
            ContactQrDialog(payload = payload, onDismiss = { qrPayload = null })
        }

        blockConfirmation?.let { targetBlocked ->
            AlertDialog(
                onDismissRequest = { blockConfirmation = null },
                title = { Text(if (targetBlocked) "Block contact?" else "Unblock contact?") },
                text = { Text(if (targetBlocked) "This contact will appear in the blocked contacts list until you unblock it." else "This will remove the contact from the blocked contacts list.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.setBlocked(targetBlocked)
                        blockConfirmation = null
                    }) { Text(if (targetBlocked) "Block" else "Unblock") }
                },
                dismissButton = { TextButton(onClick = { blockConfirmation = null }) { Text("Cancel") } },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactDetailsContent(
    details: ContactDetails,
    availableTags: List<TagSummary>,
    availableFolders: List<FolderSummary>,
    callHistory: List<CallLogItem>,
    onCall: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    onOpenTelegram: () -> Unit,
    onAddNote: () -> Unit,
    onAddPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onEditFolder: () -> Unit,
    onClearFolder: () -> Unit,
    onAddTag: () -> Unit,
    onRemoveTag: (String) -> Unit,
    onOpenQr: () -> Unit,
    onToggleBlocked: (Boolean) -> Unit,
) {
    val contact = details.contact
    val localContext = LocalContext.current
    var showAllCalls by rememberSaveable(contact.id) { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            val bannerBitmap = remember(contact.photoUri) { loadContactBitmap(localContext, contact.photoUri) }
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer, shape = CardDefaults.elevatedShape) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                        if (bannerBitmap != null) {
                            Image(bitmap = bannerBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer))
                        }
                    }
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(72.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (bannerBitmap != null) {
                                            Image(bitmap = bannerBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        } else {
                                            Text(contact.displayName.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium)
                                        }
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(contact.displayName, style = MaterialTheme.typography.headlineSmall)
                                        if (contact.isFavorite) Icon(Icons.Default.Star, contentDescription = null)
                                    }
                                    Text(contact.primaryPhone ?: "No primary phone", style = MaterialTheme.typography.bodyLarge)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        contact.folderName?.let { AssistChip(onClick = {}, label = { Text(it) }, leadingIcon = { Icon(Icons.Default.Folder, null) }) }
                                        if (contact.isBlocked) AssistChip(onClick = {}, label = { Text("Blocked") }, leadingIcon = { Icon(Icons.Default.Block, null) })
                                        contact.tags.take(4).forEach { tag -> AssistChip(onClick = {}, label = { Text(tag) }) }
                                    }
                                }
                            }
                            FilledTonalButton(onClick = onCall, enabled = !contact.primaryPhone.isNullOrBlank()) {
                                Icon(Icons.Default.Call, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Call")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            SocialPill("WA", MaterialTheme.colorScheme.secondaryContainer, onOpenWhatsApp)
                            SocialPill("TG", MaterialTheme.colorScheme.tertiaryContainer, onOpenTelegram)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onOpenQr) {
                                Icon(Icons.Default.QrCode2, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("QR Code")
                            }
                            OutlinedButton(onClick = { onToggleBlocked(!contact.isBlocked) }) {
                                Icon(Icons.Default.Block, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (contact.isBlocked) "Unblock" else "Block")
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Contact photo", style = MaterialTheme.typography.titleLarge)
                    Text(if (contact.photoUri.isNullOrBlank()) "No custom photo selected" else "Custom photo attached")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onAddPhoto) { Text(if (contact.photoUri.isNullOrBlank()) "Add photo" else "Change photo") }
                        if (!contact.photoUri.isNullOrBlank()) {
                            TextButton(onClick = onRemovePhoto) { Text("Remove") }
                        }
                    }
                }
            }
        }
        item {
            ClassificationSectionCard(
                title = "Folder",
                subtitle = if (availableFolders.isEmpty()) "Create or assign a folder directly from here." else "Tap edit to assign a different folder.",
                actionLabel = "Edit",
                onAction = onEditFolder,
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (contact.folderName != null) {
                        AssistChip(onClick = {}, label = { Text(contact.folderName) }, leadingIcon = { Icon(Icons.Default.Folder, null) })
                        TextButton(onClick = onClearFolder) { Text("Remove folder") }
                    } else {
                        Text("No folder assigned")
                    }
                }
            }
        }
        item {
            ClassificationSectionCard(
                title = "Tags",
                subtitle = if (availableTags.isEmpty()) "Add tags directly from the contact page." else "Tap a tag chip to remove it, or add a new one.",
                actionLabel = "Add tag",
                onAction = onAddTag,
            ) {
                if (contact.tags.isEmpty()) {
                    Text("No tags assigned")
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        contact.tags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { onRemoveTag(tag) },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
            }
        }
        item {
            SectionHeader(title = "Call history", actionLabel = if (showAllCalls) "Show less" else "Show more", onAction = { showAllCalls = !showAllCalls }, icon = { Icon(Icons.Default.Phone, null) })
        }
        if (callHistory.isEmpty()) {
            item { EmptyCard("No call history found", "This contact will show all matching device calls after number normalization.") }
        } else {
            items(if (showAllCalls) callHistory else callHistory.take(5), key = { it.id }) { item ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                            Text(item.type, style = MaterialTheme.typography.titleMedium)
                            Text(item.number.ifBlank { "Unknown number" }, style = MaterialTheme.typography.bodyMedium)
                            Text("${formatTime(item.timestamp)}${if (item.durationSeconds > 0) " • ${item.durationSeconds}s" else ""}", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.Phone, contentDescription = null)
                    }
                }
            }
        }
        item {
            SectionHeader(title = "Notes", actionLabel = "Add note", onAction = onAddNote, icon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, null) })
        }
        if (details.notes.isEmpty()) {
            item { EmptyCard("No notes yet", "Capture protected follow-up notes and context for this contact.") }
        } else {
            items(details.notes, key = { it.id }) { note ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(note.body, style = MaterialTheme.typography.bodyLarge)
                        Text(formatTime(note.createdAt), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Text("Timeline", style = MaterialTheme.typography.titleLarge) }
        if (details.timeline.isEmpty()) {
            item { EmptyCard("No activity yet", "The timeline will show notes, edits, block changes, and future bridge events.") }
        } else {
            items(details.timeline, key = { it.id }) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        item.subtitle?.let { Text(it) }
                        Text("${item.type} • ${formatTime(item.createdAt)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialPill(label: String, containerColor: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = containerColor, onClick = onClick) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(24.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.bodySmall) }
            }
            Text(if (label == "WA") "WhatsApp" else "Telegram")
        }
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String, onAction: () -> Unit, icon: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun ClassificationSectionCard(title: String, subtitle: String, actionLabel: String, onAction: () -> Unit, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
            content()
        }
    }
}

@Composable
private fun EmptyCard(title: String, subtitle: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle)
        }
    }
}

@Composable
private fun SimpleTextDialog(title: String, value: String, label: String, onValueChange: (String) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), minLines = 4) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ContactEditorFullScreen(state: ContactEditorState, onStateChange: (ContactEditorState) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        onStateChange(state.copy(photoUri = uri?.toString().orEmpty()))
    }
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.id == null) "Add contact" else "Edit contact", style = MaterialTheme.typography.headlineMedium)
                Row {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = onConfirm) { Text("Save") }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = state.displayName, onValueChange = { onStateChange(state.copy(displayName = it)) }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = state.phone, onValueChange = { onStateChange(state.copy(phone = it)) }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = state.folderName, onValueChange = { onStateChange(state.copy(folderName = it)) }, label = { Text("Folder") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = state.tags, onValueChange = { onStateChange(state.copy(tags = it)) }, label = { Text("Tags (comma separated)") }, modifier = Modifier.fillMaxWidth())
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Contact photo", style = MaterialTheme.typography.titleMedium)
                    Text(if (state.photoUri.isBlank()) "No photo selected" else "Photo attached")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { photoPicker.launch("image/*") }) { Text(if (state.photoUri.isBlank()) "Add photo" else "Change photo") }
                        if (state.photoUri.isNotBlank()) TextButton(onClick = { onStateChange(state.copy(photoUri = "")) }) { Text("Remove") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderPickerDialog(
    currentValue: String,
    folders: List<FolderSummary>,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var value by remember(currentValue) { mutableStateOf(currentValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Folder name") }, singleLine = true)
                if (folders.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        folders.forEach { folder -> AssistChip(onClick = { value = folder.name }, label = { Text(folder.name) }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.trim().ifBlank { null }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TagPickerDialog(
    tags: List<TagSummary>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Tag name") }, singleLine = true)
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { tag -> AssistChip(onClick = { value = tag.name }, label = { Text(tag.name) }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.trim().removePrefix("#")) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ContactQrDialog(payload: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val bitmap = remember(payload, expanded) { generateContactQrBitmap(payload, if (expanded) 1200 else 720) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contact QR Code") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "QR code",
                        modifier = Modifier.size(if (expanded) 300.dp else 220.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp)).padding(12.dp),
                    )
                }
                Text("Share, save, or expand this QR for quick import.")
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    val ok = bitmap?.let { shareContactQrBitmap(context, it) } ?: false
                    if (!ok) Toast.makeText(context, "Unable to share QR", Toast.LENGTH_SHORT).show()
                }) { Text("Share") }
                TextButton(onClick = {
                    val ok = bitmap?.let { saveContactQrBitmap(context, it) } ?: false
                    Toast.makeText(context, if (ok) "QR saved to gallery" else "Unable to save QR", Toast.LENGTH_SHORT).show()
                }) { Text("Save") }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Collapse" else "Expand") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun buildVCardPayload(contact: ContactSummary): String = buildString {
    append("BEGIN:VCARD\n")
    append("VERSION:3.0\n")
    append("FN:${contact.displayName}\n")
    contact.primaryPhone?.takeIf(String::isNotBlank)?.let { append("TEL:$it\n") }
    if (contact.tags.isNotEmpty()) append("NOTE:Tags=${contact.tags.joinToString()}\n")
    contact.folderName?.let { append("ORG:$it\n") }
    append("END:VCARD")
}

private fun shareAsText(context: android.content.Context, contact: ContactSummary) {
    val payload = buildString {
        append(contact.displayName)
        contact.primaryPhone?.takeIf(String::isNotBlank)?.let { append("\n$it") }
    }
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, payload)
    }, "Share contact"))
}

private fun shareAsVcfFile(context: android.content.Context, contact: ContactSummary) {
    val file = File(context.cacheDir, "contact_${System.currentTimeMillis()}.vcf")
    file.writeText(buildVCardPayload(contact))
    val uri = FileProvider.getUriForFile(context, "com.opencontacts.app.fileprovider", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/x-vcard"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Share contact file"))
}

private fun openWhatsApp(context: android.content.Context, phone: String) {
    val normalized = normalizePhoneForDeepLink(phone)
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$normalized")))
}

private fun openTelegram(context: android.content.Context, phone: String) {
    val normalized = normalizePhoneForDeepLink(phone)
    val tgIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?phone=$normalized"))
    val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+${normalized}"))
    runCatching { context.startActivity(tgIntent) }.getOrElse { context.startActivity(fallback) }
}

private fun normalizePhoneForDeepLink(phone: String): String {
    var normalized = phone.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
    normalized = normalized.filter { it.isDigit() || it == '+' }
    if (normalized.startsWith("00")) normalized = "+${normalized.drop(2)}"
    if (normalized.startsWith("0")) normalized = "+20${normalized.drop(1)}"
    if (!normalized.startsWith("+")) normalized = "+$normalized"
    return normalized.filter { it.isDigit() }
}

private fun generateContactQrBitmap(payload: String, size: Int): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until size) for (y in 0 until size) setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
}.getOrNull()

private fun saveContactQrBitmap(context: android.content.Context, bitmap: Bitmap): Boolean {
    return runCatching {
        val resolver = context.contentResolver
        val name = "opencontacts_contact_qr_${System.currentTimeMillis()}.png"
        val values = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenContacts")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) } ?: return false
        true
    }.getOrDefault(false)
}

private fun shareContactQrBitmap(context: android.content.Context, bitmap: Bitmap): Boolean {
    return runCatching {
        val file = File(context.cacheDir, "contact_qr_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        val uri = FileProvider.getUriForFile(context, "com.opencontacts.app.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share QR"))
        true
    }.getOrDefault(false)
}

private fun formatTime(value: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(value))

private fun loadContactBitmap(context: android.content.Context, uri: String?): android.graphics.Bitmap? {
    if (uri.isNullOrBlank()) return null
    return runCatching {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: android.content.Context,
    private val vaultSessionManager: VaultSessionManager,
    private val contactRepository: ContactRepository,
) : ViewModel() {
    private val contactId: String = checkNotNull(savedStateHandle.get<String>("contactId"))

    val details: StateFlow<ContactDetails?> = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, isLocked -> vaultId to isLocked }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) flowOf(null) else contactRepository.observeContactDetails(vaultId, contactId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tags: StateFlow<List<TagSummary>> = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, isLocked -> vaultId to isLocked }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) flowOf(emptyList()) else contactRepository.observeTags(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders: StateFlow<List<FolderSummary>> = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, isLocked -> vaultId to isLocked }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) flowOf(emptyList()) else contactRepository.observeFolders(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val callHistory: StateFlow<List<CallLogItem>> = details
        .flatMapLatest { current ->
            val contact = current?.contact ?: return@flatMapLatest flowOf(emptyList())
            flow {
                val logs = queryDeviceCallLogs(context, listOf(contact))
                    .filter {
                        it.matchedContactId == contact.id || (
                            it.normalizedNumber.isNotBlank() && it.normalizedNumber == normalizePhoneForMatching(contact.primaryPhone)
                        )
                    }
                emit(logs)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _noteEditor = MutableStateFlow<String?>(null)
    val noteEditor: StateFlow<String?> = _noteEditor

    private val _contactEditor = MutableStateFlow<ContactEditorState?>(null)
    val contactEditor: StateFlow<ContactEditorState?> = _contactEditor

    fun startAddNote() { _noteEditor.value = "" }
    fun updateNoteEditor(value: String) { _noteEditor.value = value }
    fun dismissNoteEditor() { _noteEditor.value = null }

    fun saveNote() {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val note = _noteEditor.value ?: return
        viewModelScope.launch {
            contactRepository.addNote(vaultId, contactId, note)
            _noteEditor.value = null
        }
    }

    fun startEdit(contact: ContactSummary) {
        _contactEditor.value = ContactEditorState(
            id = contact.id,
            displayName = contact.displayName,
            phone = contact.primaryPhone.orEmpty(),
            tags = contact.tags.joinToString(", "),
            isFavorite = contact.isFavorite,
            folderName = contact.folderName.orEmpty(),
            photoUri = contact.photoUri.orEmpty(),
            isBlocked = contact.isBlocked,
        )
    }

    fun updateContactEditor(state: ContactEditorState) { _contactEditor.value = state }
    fun dismissContactEditor() { _contactEditor.value = null }

    fun saveContactEditor() {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val editor = _contactEditor.value ?: return
        viewModelScope.launch {
            contactRepository.saveContactDraft(
                vaultId,
                ContactDraft(
                    id = editor.id,
                    displayName = editor.displayName.ifBlank { "Unnamed contact" },
                    primaryPhone = editor.phone.ifBlank { null },
                    tags = editor.tags.split(',').mapNotNull { it.trim().takeIf(String::isNotBlank) },
                    isFavorite = editor.isFavorite,
                    folderName = editor.folderName.ifBlank { null },
                    photoUri = editor.photoUri.ifBlank { null },
                    isBlocked = editor.isBlocked,
                ),
            )
            _contactEditor.value = null
        }
    }

    fun updateFolder(folderName: String?) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val current = details.value?.contact ?: return
        viewModelScope.launch {
            folderName?.takeIf { it.isNotBlank() }?.let {
                contactRepository.upsertFolder(vaultId, FolderSummary(name = it))
            }
            contactRepository.saveContactDraft(
                vaultId,
                ContactDraft(
                    id = current.id,
                    displayName = current.displayName,
                    primaryPhone = current.primaryPhone,
                    tags = current.tags,
                    isFavorite = current.isFavorite,
                    folderName = folderName,
                    photoUri = current.photoUri,
                    isBlocked = current.isBlocked,
                ),
            )
        }
    }

    fun clearFolder() = updateFolder(null)

    fun addTag(tagName: String) {
        val clean = tagName.trim().removePrefix("#").ifBlank { return }
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val current = details.value?.contact ?: return
        viewModelScope.launch {
            contactRepository.upsertTag(vaultId, TagSummary(name = clean))
            contactRepository.saveContactDraft(
                vaultId,
                ContactDraft(
                    id = current.id,
                    displayName = current.displayName,
                    primaryPhone = current.primaryPhone,
                    tags = (current.tags + clean).distinct(),
                    isFavorite = current.isFavorite,
                    folderName = current.folderName,
                    photoUri = current.photoUri,
                    isBlocked = current.isBlocked,
                ),
            )
        }
    }

    fun removeTag(tagName: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val current = details.value?.contact ?: return
        viewModelScope.launch {
            contactRepository.saveContactDraft(
                vaultId,
                ContactDraft(
                    id = current.id,
                    displayName = current.displayName,
                    primaryPhone = current.primaryPhone,
                    tags = current.tags.filterNot { it == tagName },
                    isFavorite = current.isFavorite,
                    folderName = current.folderName,
                    photoUri = current.photoUri,
                    isBlocked = current.isBlocked,
                ),
            )
        }
    }

    fun setPhoto(uri: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val current = details.value?.contact ?: return
        viewModelScope.launch {
            contactRepository.saveContactDraft(
                vaultId,
                ContactDraft(
                    id = current.id,
                    displayName = current.displayName,
                    primaryPhone = current.primaryPhone,
                    tags = current.tags,
                    isFavorite = current.isFavorite,
                    folderName = current.folderName,
                    photoUri = uri,
                    isBlocked = current.isBlocked,
                ),
            )
        }
    }

    fun removePhoto() {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val current = details.value?.contact ?: return
        viewModelScope.launch {
            contactRepository.saveContactDraft(
                vaultId,
                ContactDraft(
                    id = current.id,
                    displayName = current.displayName,
                    primaryPhone = current.primaryPhone,
                    tags = current.tags,
                    isFavorite = current.isFavorite,
                    folderName = current.folderName,
                    photoUri = null,
                    isBlocked = current.isBlocked,
                ),
            )
        }
    }

    fun setBlocked(blocked: Boolean) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            contactRepository.setContactBlocked(vaultId, contactId, blocked)
        }
    }

    fun deleteContact() {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch { contactRepository.deleteContact(vaultId, contactId) }
    }
}

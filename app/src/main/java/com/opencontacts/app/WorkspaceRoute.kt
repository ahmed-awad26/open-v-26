package com.opencontacts.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.opencontacts.core.model.ContactDraft
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.model.FolderSummary
import com.opencontacts.core.model.TagSummary
import com.opencontacts.core.vault.VaultSessionManager
import com.opencontacts.domain.contacts.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun WorkspaceRoute(
    onBack: () -> Unit,
    onOpenDetails: (String) -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel(),
) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    var newTag by remember { mutableStateOf<String?>(null) }
    var newFolder by remember { mutableStateOf<String?>(null) }
    var editTag by remember { mutableStateOf<String?>(null) }
    var editFolder by remember { mutableStateOf<String?>(null) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedContactIds by remember { mutableStateOf(setOf<String>()) }
    var addContactsDialog by remember { mutableStateOf(false) }

    val filteredContacts = remember(contacts, selectedTag, selectedFolder) {
        when {
            selectedTag != null -> contacts.filter { selectedTag in it.tags }
            selectedFolder != null -> contacts.filter { it.folderName == selectedFolder }
            else -> emptyList()
        }
    }

    val activeContainerTitle = selectedFolder ?: selectedTag
    val selectionMode = selectedContactIds.isNotEmpty()

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CardDefaults.elevatedShape,
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (activeContainerTitle == null) "Groups & folders" else activeContainerTitle, style = MaterialTheme.typography.headlineMedium)
                    Text(if (activeContainerTitle == null) "Open a folder or tag to browse and manage the contacts inside it." else "${filteredContacts.size} contact(s) classified here")
                }
            }

            if (activeContainerTitle == null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, null)
                                Text("Folders", style = MaterialTheme.typography.titleLarge)
                            }
                            Row {
                                IconButton(onClick = { newFolder = "" }) { Icon(Icons.Default.Add, contentDescription = "Add folder") }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            folders.forEach { folder ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        selectedFolder = folder.name
                                        selectedTag = null
                                        selectedContactIds = emptySet()
                                    },
                                    label = { Text(folder.name) },
                                    leadingIcon = { Icon(Icons.Default.Folder, null) },
                                    trailingIcon = { Icon(Icons.Default.ArrowForward, null) },
                                )
                            }
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Label, null)
                                Text("Tags", style = MaterialTheme.typography.titleLarge)
                            }
                            Row {
                                IconButton(onClick = { newTag = "" }) { Icon(Icons.Default.Add, contentDescription = "Add tag") }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            tags.forEach { tag ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        selectedTag = tag.name
                                        selectedFolder = null
                                        selectedContactIds = emptySet()
                                    },
                                    label = { Text(tag.name) },
                                    leadingIcon = { Icon(Icons.Default.Label, null) },
                                    trailingIcon = { Icon(Icons.Default.ArrowForward, null) },
                                )
                            }
                        }
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (selectedFolder != null) "Folder contents" else "Tag contents", style = MaterialTheme.typography.titleLarge)
                            Row {
                                IconButton(onClick = { addContactsDialog = true }) { Icon(Icons.Default.Add, contentDescription = "Add contacts") }
                                if (selectedFolder != null) IconButton(onClick = { editFolder = selectedFolder }) { Icon(Icons.Default.Edit, contentDescription = "Edit folder") }
                                if (selectedTag != null) IconButton(onClick = { editTag = selectedTag }) { Icon(Icons.Default.Edit, contentDescription = "Edit tag") }
                                if (selectedFolder != null) IconButton(onClick = { viewModel.deleteFolder(selectedFolder!!); selectedFolder = null; selectedContactIds = emptySet() }) { Icon(Icons.Default.Delete, contentDescription = "Delete folder") }
                                if (selectedTag != null) IconButton(onClick = { viewModel.deleteTag(selectedTag!!); selectedTag = null; selectedContactIds = emptySet() }) { Icon(Icons.Default.Delete, contentDescription = "Delete tag") }
                                TextButton(onClick = { selectedFolder = null; selectedTag = null; selectedContactIds = emptySet() }) { Text("Close") }
                            }
                        }

                        if (selectionMode) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${selectedContactIds.size} selected", style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = {
                                    if (selectedFolder != null) viewModel.removeFolderFromContacts(selectedContactIds)
                                    if (selectedTag != null) viewModel.removeTagFromContacts(selectedContactIds, selectedTag!!)
                                    selectedContactIds = emptySet()
                                }) {
                                    Text(if (selectedFolder != null) "Remove from folder" else "Remove tag")
                                }
                                TextButton(onClick = { selectedContactIds = emptySet() }) { Text("Clear") }
                            }
                        }

                        if (filteredContacts.isEmpty()) {
                            Text("No contacts are classified under this item yet.")
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                items(filteredContacts, key = { it.id }) { contact ->
                                    ContactMiniCard(
                                        contact = contact,
                                        selected = contact.id in selectedContactIds,
                                        selectionMode = selectionMode,
                                        onOpen = {
                                            if (selectionMode) {
                                                selectedContactIds = if (contact.id in selectedContactIds) selectedContactIds - contact.id else selectedContactIds + contact.id
                                            } else {
                                                onOpenDetails(contact.id)
                                            }
                                        },
                                        onLongPress = {
                                            selectedContactIds = if (contact.id in selectedContactIds) selectedContactIds - contact.id else selectedContactIds + contact.id
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    newTag?.let { value ->
        NameDialog("New tag", value, "Tag name", { newTag = it }, { newTag = null }) {
            val clean = value.trim()
            if (clean.isNotBlank()) viewModel.saveTag(clean)
            newTag = null
        }
    }
    newFolder?.let { value ->
        NameDialog("New folder", value, "Folder name", { newFolder = it }, { newFolder = null }) {
            val clean = value.trim()
            if (clean.isNotBlank()) viewModel.saveFolder(clean)
            newFolder = null
        }
    }
    editTag?.let { value ->
        NameDialog("Edit tag", value, "Tag name", { editTag = it }, { editTag = null }) {
            val old = selectedTag
            val clean = value.trim()
            if (old != null && clean.isNotBlank()) viewModel.renameTag(old, clean)
            selectedTag = clean.ifBlank { old }
            editTag = null
        }
    }
    editFolder?.let { value ->
        NameDialog("Edit folder", value, "Folder name", { editFolder = it }, { editFolder = null }) {
            val old = selectedFolder
            val clean = value.trim()
            if (old != null && clean.isNotBlank()) viewModel.renameFolder(old, clean)
            selectedFolder = clean.ifBlank { old }
            editFolder = null
        }
    }
    if (addContactsDialog && activeContainerTitle != null) {
        AddContactsDialog(
            title = if (selectedFolder != null) "Add contacts to folder" else "Add contacts to tag",
            contacts = contacts,
            alreadyIncludedIds = filteredContacts.map { it.id }.toSet(),
            onDismiss = { addContactsDialog = false },
            onConfirm = { ids ->
                if (selectedFolder != null) viewModel.assignFolderToContacts(ids, selectedFolder!!)
                if (selectedTag != null) viewModel.assignTagToContacts(ids, selectedTag!!)
                addContactsDialog = false
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactMiniCard(contact: ContactSummary, selected: Boolean, selectionMode: Boolean, onOpen: () -> Unit, onLongPress: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = {}) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = onLongPress,
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) Checkbox(checked = selected, onCheckedChange = { onOpen() })
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(contact.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(contact.primaryPhone ?: "No phone", style = MaterialTheme.typography.bodyMedium)
                if (contact.tags.isNotEmpty()) Text(contact.tags.joinToString(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AddContactsDialog(
    title: String,
    contacts: List<ContactSummary>,
    alreadyIncludedIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(contacts.filterNot { it.id in alreadyIncludedIds }, key = { it.id }) { contact ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = contact.id in selected,
                            onCheckedChange = {
                                selected = if (contact.id in selected) selected - contact.id else selected + contact.id
                            },
                        )
                        Column {
                            Text(contact.displayName)
                            Text(contact.primaryPhone ?: "No phone", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NameDialog(title: String, value: String, label: String, onValueChange: (String) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val vaultSessionManager: VaultSessionManager,
    private val contactRepository: ContactRepository,
) : ViewModel() {
    val tags = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, isLocked -> vaultId to isLocked }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) flowOf(emptyList()) else contactRepository.observeTags(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, isLocked -> vaultId to isLocked }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) flowOf(emptyList()) else contactRepository.observeFolders(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val contacts = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, isLocked -> vaultId to isLocked }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) flowOf(emptyList()) else contactRepository.observeContacts(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveTag(name: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch { contactRepository.upsertTag(vaultId, TagSummary(name = name)) }
    }

    fun saveFolder(name: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch { contactRepository.upsertFolder(vaultId, FolderSummary(name = name)) }
    }

    fun deleteTag(name: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch { contactRepository.deleteTag(vaultId, name) }
    }

    fun deleteFolder(name: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch { contactRepository.deleteFolder(vaultId, name) }
    }

    fun renameTag(oldName: String, newName: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            if (oldName != newName) {
                contactRepository.upsertTag(vaultId, TagSummary(name = newName))
                contacts.value.filter { oldName in it.tags }.forEach { current ->
                    contactRepository.saveContactDraft(vaultId, ContactDraft(id = current.id, displayName = current.displayName, primaryPhone = current.primaryPhone, tags = current.tags.map { if (it == oldName) newName else it }, isFavorite = current.isFavorite, folderName = current.folderName, photoUri = current.photoUri, isBlocked = current.isBlocked))
                }
                contactRepository.deleteTag(vaultId, oldName)
            }
        }
    }

    fun renameFolder(oldName: String, newName: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            if (oldName != newName) {
                contactRepository.upsertFolder(vaultId, FolderSummary(name = newName))
                contacts.value.filter { it.folderName == oldName }.forEach { current ->
                    contactRepository.saveContactDraft(vaultId, ContactDraft(id = current.id, displayName = current.displayName, primaryPhone = current.primaryPhone, tags = current.tags, isFavorite = current.isFavorite, folderName = newName, photoUri = current.photoUri, isBlocked = current.isBlocked))
                }
                contactRepository.deleteFolder(vaultId, oldName)
            }
        }
    }

    fun removeTagFromContacts(contactIds: Set<String>, tag: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val currentMap = contacts.value.associateBy { it.id }
        viewModelScope.launch {
            contactIds.forEach { id ->
                val current = currentMap[id] ?: return@forEach
                contactRepository.saveContactDraft(vaultId, ContactDraft(id = current.id, displayName = current.displayName, primaryPhone = current.primaryPhone, tags = current.tags.filterNot { it == tag }, isFavorite = current.isFavorite, folderName = current.folderName, photoUri = current.photoUri, isBlocked = current.isBlocked))
            }
        }
    }

    fun removeFolderFromContacts(contactIds: Set<String>) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val currentMap = contacts.value.associateBy { it.id }
        viewModelScope.launch {
            contactIds.forEach { id ->
                val current = currentMap[id] ?: return@forEach
                contactRepository.saveContactDraft(vaultId, ContactDraft(id = current.id, displayName = current.displayName, primaryPhone = current.primaryPhone, tags = current.tags, isFavorite = current.isFavorite, folderName = null, photoUri = current.photoUri, isBlocked = current.isBlocked))
            }
        }
    }

    fun assignFolderToContacts(contactIds: Set<String>, folder: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val currentMap = contacts.value.associateBy { it.id }
        viewModelScope.launch {
            contactIds.forEach { id ->
                val current = currentMap[id] ?: return@forEach
                contactRepository.saveContactDraft(vaultId, ContactDraft(id = current.id, displayName = current.displayName, primaryPhone = current.primaryPhone, tags = current.tags, isFavorite = current.isFavorite, folderName = folder, photoUri = current.photoUri, isBlocked = current.isBlocked))
            }
        }
    }

    fun assignTagToContacts(contactIds: Set<String>, tag: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val currentMap = contacts.value.associateBy { it.id }
        viewModelScope.launch {
            contactIds.forEach { id ->
                val current = currentMap[id] ?: return@forEach
                contactRepository.saveContactDraft(vaultId, ContactDraft(id = current.id, displayName = current.displayName, primaryPhone = current.primaryPhone, tags = (current.tags + tag).distinct(), isFavorite = current.isFavorite, folderName = current.folderName, photoUri = current.photoUri, isBlocked = current.isBlocked))
            }
        }
    }
}

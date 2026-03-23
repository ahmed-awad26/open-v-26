package com.opencontacts.feature.contacts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencontacts.core.model.ContactDraft
import com.opencontacts.core.model.ContactSummary
import com.opencontacts.core.model.FolderSummary
import com.opencontacts.core.model.TagSummary
import com.opencontacts.core.vault.VaultSessionManager
import com.opencontacts.domain.contacts.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ContactsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultSessionManager: VaultSessionManager,
    private val contactRepository: ContactRepository,
) : ViewModel() {
    val contacts: StateFlow<List<ContactSummary>> = vaultSessionManager.activeVaultId
        .combine(vaultSessionManager.isLocked) { vaultId, isLocked -> vaultId to isLocked }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) flowOf(emptyList()) else contactRepository.observeContacts(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    private val _editingContact = MutableStateFlow<ContactEditorState?>(null)
    val editingContact: StateFlow<ContactEditorState?> = _editingContact

    private val _callLogs = MutableStateFlow<List<CallLogItem>>(emptyList())
    val callLogs: StateFlow<List<CallLogItem>> = _callLogs

    val callLogGroups: StateFlow<List<CallLogGroup>> = combine(_callLogs, contacts) { logs, contactList ->
        groupCallLogs(
            logs.map { log ->
                val matched = contactList.firstOrNull { it.id == log.matchedContactId }
                    ?: contactList.firstOrNull {
                        normalizePhoneForMatching(it.primaryPhone).isNotBlank() && normalizePhoneForMatching(it.primaryPhone) == log.normalizedNumber
                    }
                if (matched != null) {
                    log.copy(matchedContactId = matched.id, matchedDisplayName = matched.displayName)
                } else {
                    log
                }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refreshCallLogs() {
        viewModelScope.launch {
            _callLogs.value = queryDeviceCallLogs(context, contacts.value)
        }
    }

    fun startCreate() {
        _editingContact.value = ContactEditorState()
    }

    fun startEdit(contact: ContactSummary) {
        _editingContact.value = ContactEditorState(
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

    fun updateEditor(state: ContactEditorState) {
        _editingContact.value = state
    }

    fun dismissEditor() {
        _editingContact.value = null
    }

    fun saveEditor() {
        val editor = _editingContact.value ?: return
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            contactRepository.saveContactDraft(
                vaultId = vaultId,
                draft = ContactDraft(
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
            _editingContact.value = null
        }
    }

    fun delete(contactId: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch { contactRepository.deleteContact(vaultId, contactId) }
    }

    fun deleteMany(contactIds: Collection<String>) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        viewModelScope.launch {
            contactIds.forEach { contactRepository.deleteContact(vaultId, it) }
        }
    }

    fun assignFolderToMany(contactIds: Collection<String>, folderName: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val cleanFolder = folderName.trim().ifBlank { return }
        val currentContacts = contacts.value.associateBy { it.id }
        viewModelScope.launch {
            contactRepository.upsertFolder(vaultId, FolderSummary(name = cleanFolder))
            contactIds.forEach { id ->
                val current = currentContacts[id] ?: return@forEach
                contactRepository.saveContactDraft(
                    vaultId,
                    ContactDraft(
                        id = current.id,
                        displayName = current.displayName,
                        primaryPhone = current.primaryPhone,
                        tags = current.tags,
                        isFavorite = current.isFavorite,
                        folderName = cleanFolder,
                        photoUri = current.photoUri,
                        isBlocked = current.isBlocked,
                    ),
                )
            }
        }
    }

    fun assignTagToMany(contactIds: Collection<String>, tagName: String) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val cleanTag = tagName.trim().removePrefix("#").ifBlank { return }
        val currentContacts = contacts.value.associateBy { it.id }
        viewModelScope.launch {
            contactRepository.upsertTag(vaultId, TagSummary(name = cleanTag))
            contactIds.forEach { id ->
                val current = currentContacts[id] ?: return@forEach
                contactRepository.saveContactDraft(
                    vaultId,
                    ContactDraft(
                        id = current.id,
                        displayName = current.displayName,
                        primaryPhone = current.primaryPhone,
                        tags = (current.tags + cleanTag).distinct(),
                        isFavorite = current.isFavorite,
                        folderName = current.folderName,
                        photoUri = current.photoUri,
                        isBlocked = current.isBlocked,
                    ),
                )
            }
        }
    }

    fun removeFolderFromMany(contactIds: Collection<String>) {
        val vaultId = vaultSessionManager.activeVaultId.value ?: return
        val currentContacts = contacts.value.associateBy { it.id }
        viewModelScope.launch {
            contactIds.forEach { id ->
                val current = currentContacts[id] ?: return@forEach
                contactRepository.saveContactDraft(
                    vaultId,
                    ContactDraft(
                        id = current.id,
                        displayName = current.displayName,
                        primaryPhone = current.primaryPhone,
                        tags = current.tags,
                        isFavorite = current.isFavorite,
                        folderName = null,
                        photoUri = current.photoUri,
                        isBlocked = current.isBlocked,
                    ),
                )
            }
        }
    }
}

data class ContactEditorState(
    val id: String? = null,
    val displayName: String = "",
    val phone: String = "",
    val tags: String = "",
    val isFavorite: Boolean = false,
    val folderName: String = "",
    val photoUri: String = "",
    val isBlocked: Boolean = false,
)

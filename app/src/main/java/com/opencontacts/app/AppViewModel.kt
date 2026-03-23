package com.opencontacts.app

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencontacts.core.crypto.AppLockRepository
import com.opencontacts.core.crypto.AppLockSettings
import com.opencontacts.core.crypto.BiometricAuthManager
import com.opencontacts.core.vault.VaultSessionManager
import com.opencontacts.domain.contacts.ContactRepository
import com.opencontacts.data.repository.TransferDestinationKind
import com.opencontacts.data.repository.TransferDestinationManager
import com.opencontacts.domain.vaults.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val contactRepository: ContactRepository,
    private val appLockRepository: AppLockRepository,
    private val transferDestinationManager: TransferDestinationManager,
    private val biometricAuthManager: BiometricAuthManager,
    private val sessionManager: VaultSessionManager,
) : ViewModel() {
    val appLockSettings: StateFlow<AppLockSettings> = appLockRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLockSettings(false, false, 30, "SYSTEM", "vault_exports", "vault_backups", null, null, null, null))

    val activeVaultId: StateFlow<String?> = sessionManager.activeVaultId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isLocked: StateFlow<Boolean> = sessionManager.isLocked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val shouldShowUnlock: StateFlow<Boolean> = combine(
        activeVaultId,
        isLocked,
        appLockSettings,
    ) { activeVaultId, isLocked, settings ->
        activeVaultId != null && isLocked && (settings.hasPin || settings.biometricEnabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)


    val vaults = vaultRepository.observeVaults()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeVaultName: StateFlow<String> = combine(activeVaultId, vaults) { activeVaultId, vaults ->
        vaults.firstOrNull { it.id == activeVaultId }?.displayName ?: "No active vault"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "No active vault")

    val activeContactCount: StateFlow<Int> = activeVaultId
        .combine(isLocked) { vaultId, isLocked -> vaultId to isLocked }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) kotlinx.coroutines.flow.flowOf(emptyList()) else contactRepository.observeContacts(vaultId)
        }
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val blockedContacts = activeVaultId
        .combine(isLocked) { vaultId, isLocked -> vaultId to isLocked }
        .flatMapLatest { (vaultId, isLocked) ->
            if (vaultId == null || isLocked) kotlinx.coroutines.flow.flowOf(emptyList()) else contactRepository.observeBlockedContacts(vaultId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError

    private val _storageMessage = MutableStateFlow<String?>(null)
    val storageMessage: StateFlow<String?> = _storageMessage

    init {
        viewModelScope.launch {
            val defaultVault = vaultRepository.ensureDefaultVault()
            val settings = appLockRepository.settings.first()
            val shouldLock = settings.hasPin || settings.biometricEnabled
            vaultRepository.setLocked(defaultVault.id, shouldLock)
            sessionManager.setVault(defaultVault.id, locked = shouldLock)
            val retentionMillis = settings.trashRetentionDays * 24L * 60L * 60L * 1000L
            if (retentionMillis > 0) {
                contactRepository.purgeDeletedOlderThan(defaultVault.id, System.currentTimeMillis() - retentionMillis)
            }
        }
    }

    fun setPin(pin: String) {
        if (pin.length < 4) {
            _pinError.value = "PIN must be at least 4 digits"
            return
        }
        viewModelScope.launch {
            appLockRepository.setPin(pin.toCharArray())
            _pinError.value = null
        }
    }

    fun clearPin() {
        viewModelScope.launch {
            appLockRepository.clearPin()
            _pinError.value = null
            activeVaultId.value?.let { vaultRepository.setLocked(it, false) }
            sessionManager.unlock()
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appLockRepository.setBiometricEnabled(enabled)
        }
    }

    fun showUiError(message: String) {
        _pinError.value = message
    }

    fun unlockWithPin(pin: String) {
        viewModelScope.launch {
            val valid = appLockRepository.verifyPin(pin.toCharArray())
            if (valid) {
                _pinError.value = null
                activeVaultId.value?.let { vaultRepository.setLocked(it, false) }
                sessionManager.unlock()
            } else {
                _pinError.value = "Incorrect PIN"
            }
        }
    }

    fun unlockWithBiometricSuccess() {
        viewModelScope.launch {
            _pinError.value = null
            activeVaultId.value?.let { vaultRepository.setLocked(it, false) }
            sessionManager.unlock()
        }
    }

    fun lockNow() {
        viewModelScope.launch {
            activeVaultId.value?.let { vaultRepository.setLocked(it, true) }
            sessionManager.lock()
        }
    }

    fun clearError() {
        _pinError.value = null
    }


    fun switchVault(vaultId: String) {
        viewModelScope.launch {
            val target = vaults.value.firstOrNull { it.id == vaultId } ?: return@launch
            sessionManager.setVault(vaultId, locked = target.isLocked)
            val retentionMillis = appLockSettings.value.trashRetentionDays * 24L * 60L * 60L * 1000L
            if (retentionMillis > 0) {
                contactRepository.purgeDeletedOlderThan(vaultId, System.currentTimeMillis() - retentionMillis)
            }
        }
    }

    fun setTrashRetentionDays(days: Int) {
        viewModelScope.launch {
            appLockRepository.setTrashRetentionDays(days)
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            appLockRepository.setThemeMode(mode)
        }
    }

    fun setExportPath(path: String) {
        viewModelScope.launch { appLockRepository.setExportPath(path) }
    }

    fun setBackupPath(path: String) {
        viewModelScope.launch { appLockRepository.setBackupPath(path) }
    }


    fun setBackupFolder(uri: Uri) {
        viewModelScope.launch {
            try {
                val result = transferDestinationManager.setFolder(TransferDestinationKind.BACKUP, uri)
                _storageMessage.value = "Backup folder set to ${result.displayName}"
            } catch (error: Exception) {
                _storageMessage.value = error.message ?: "Unable to use the selected backup folder."
            }
        }
    }

    fun setExportFolder(uri: Uri) {
        viewModelScope.launch {
            try {
                val result = transferDestinationManager.setFolder(TransferDestinationKind.EXPORT, uri)
                _storageMessage.value = "Export folder set to ${result.displayName}"
            } catch (error: Exception) {
                _storageMessage.value = error.message ?: "Unable to use the selected export folder."
            }
        }
    }

    fun resetBackupFolder() {
        viewModelScope.launch {
            transferDestinationManager.resetFolder(TransferDestinationKind.BACKUP)
            _storageMessage.value = "Backup folder reset to the default app storage location."
        }
    }

    fun resetExportFolder() {
        viewModelScope.launch {
            transferDestinationManager.resetFolder(TransferDestinationKind.EXPORT)
            _storageMessage.value = "Export folder reset to the default app storage location."
        }
    }

    fun showStorageMessage(message: String) {
        _storageMessage.value = message
    }

    fun clearStorageMessage() {
        _storageMessage.value = null
    }

    fun createVault(displayName: String) {
        viewModelScope.launch {
            val created = vaultRepository.createVault(displayName)
            sessionManager.setVault(created.id, locked = created.isLocked)
        }
    }

    fun unblockContact(contactId: String) {
        val vaultId = activeVaultId.value ?: return
        viewModelScope.launch {
            contactRepository.setContactBlocked(vaultId, contactId, false)
        }
    }

    fun deleteVault(vaultId: String) {
        viewModelScope.launch {
            val current = activeVaultId.value
            val items = vaults.value
            if (items.size <= 1) return@launch
            vaultRepository.deleteVault(vaultId)
            if (current == vaultId) {
                val fallback = vaultRepository.ensureDefaultVault()
                sessionManager.setVault(fallback.id, locked = fallback.isLocked)
            }
        }
    }

    fun canUseBiometric(): Boolean = biometricAuthManager.canAuthenticate()

    fun biometricPromptInfo(vaultName: String) = biometricAuthManager.createPromptInfo(vaultName)
}

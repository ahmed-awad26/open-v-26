package com.opencontacts.core.crypto

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appLockDataStore by preferencesDataStore(name = "app_lock_settings")

@Singleton
class AppLockRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val settings: Flow<AppLockSettings> = context.appLockDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            AppLockSettings(
                hasPin = prefs[PIN_HASH] != null,
                biometricEnabled = prefs[BIOMETRIC_ENABLED] ?: false,
                trashRetentionDays = prefs[TRASH_RETENTION_DAYS] ?: 30,
                themeMode = prefs[THEME_MODE] ?: "SYSTEM",
                exportPath = prefs[EXPORT_PATH] ?: "vault_exports",
                backupPath = prefs[BACKUP_PATH] ?: "vault_backups",
                exportFolderUri = prefs[EXPORT_FOLDER_URI],
                exportFolderName = prefs[EXPORT_FOLDER_NAME],
                backupFolderUri = prefs[BACKUP_FOLDER_URI],
                backupFolderName = prefs[BACKUP_FOLDER_NAME],
            )
        }

    suspend fun setPin(pin: CharArray) {
        val salt = ByteArray(SALT_LENGTH).also(SecureRandom()::nextBytes)
        val hash = hashPin(pin, salt)
        context.appLockDataStore.edit { prefs ->
            prefs[PIN_HASH] = encode(hash)
            prefs[PIN_SALT] = encode(salt)
        }
        pin.fill('\u0000')
    }

    suspend fun clearPin() {
        context.appLockDataStore.edit { prefs ->
            prefs.remove(PIN_HASH)
            prefs.remove(PIN_SALT)
        }
    }

    suspend fun verifyPin(pin: CharArray): Boolean {
        val snapshot = context.appLockDataStore.data.first()
        val storedHash = snapshot[PIN_HASH] ?: return false
        val storedSalt = snapshot[PIN_SALT] ?: return false
        val computed = hashPin(pin, decode(storedSalt))
        pin.fill('\u0000')
        return encode(computed) == storedHash
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.appLockDataStore.edit { prefs ->
            prefs[BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun setTrashRetentionDays(days: Int) {
        context.appLockDataStore.edit { prefs ->
            prefs[TRASH_RETENTION_DAYS] = days.coerceIn(1, 365)
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.appLockDataStore.edit { prefs ->
            prefs[THEME_MODE] = mode.uppercase()
        }
    }

    suspend fun setExportPath(path: String) {
        context.appLockDataStore.edit { prefs ->
            prefs[EXPORT_PATH] = path.ifBlank { "vault_exports" }
        }
    }

    suspend fun setBackupPath(path: String) {
        context.appLockDataStore.edit { prefs ->
            prefs[BACKUP_PATH] = path.ifBlank { "vault_backups" }
        }
    }


    suspend fun setExportFolder(uri: String?, displayName: String?) {
        context.appLockDataStore.edit { prefs ->
            if (uri.isNullOrBlank()) prefs.remove(EXPORT_FOLDER_URI) else prefs[EXPORT_FOLDER_URI] = uri
            if (displayName.isNullOrBlank()) prefs.remove(EXPORT_FOLDER_NAME) else prefs[EXPORT_FOLDER_NAME] = displayName
        }
    }

    suspend fun setBackupFolder(uri: String?, displayName: String?) {
        context.appLockDataStore.edit { prefs ->
            if (uri.isNullOrBlank()) prefs.remove(BACKUP_FOLDER_URI) else prefs[BACKUP_FOLDER_URI] = uri
            if (displayName.isNullOrBlank()) prefs.remove(BACKUP_FOLDER_NAME) else prefs[BACKUP_FOLDER_NAME] = displayName
        }
    }

    private fun hashPin(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, HASH_ITERATIONS, DERIVED_KEY_LENGTH_BITS)
        return SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val TRASH_RETENTION_DAYS = androidx.datastore.preferences.core.intPreferencesKey("trash_retention_days")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val EXPORT_PATH = stringPreferencesKey("export_path")
        val BACKUP_PATH = stringPreferencesKey("backup_path")
        val EXPORT_FOLDER_URI = stringPreferencesKey("export_folder_uri")
        val EXPORT_FOLDER_NAME = stringPreferencesKey("export_folder_name")
        val BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
        val BACKUP_FOLDER_NAME = stringPreferencesKey("backup_folder_name")

        const val SALT_LENGTH = 16
        const val HASH_ITERATIONS = 120_000
        const val DERIVED_KEY_LENGTH_BITS = 256
        const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    }
}

data class AppLockSettings(
    val hasPin: Boolean,
    val biometricEnabled: Boolean,
    val trashRetentionDays: Int,
    val themeMode: String,
    val exportPath: String,
    val backupPath: String,
    val exportFolderUri: String? = null,
    val exportFolderName: String? = null,
    val backupFolderUri: String? = null,
    val backupFolderName: String? = null,
)

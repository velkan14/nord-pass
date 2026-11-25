package dev.damado.nordpass.data.secure

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.secretsDataStore: DataStore<Preferences> by preferencesDataStore(name = "carnet_secrets")

/**
 * Stores only an encrypted ciphertext blob in DataStore. Plaintext secrets
 * never touch disk.
 */
class SecretsStore(
    context: Context,
    private val encryptor: KeystoreEncryptor,
) {
    private val dataStore = context.applicationContext.secretsDataStore

    val secretsFlow: Flow<StoredSecrets> = dataStore.data.map { preferences ->
        decode(preferences[CIPHERTEXT_KEY])
    }

    suspend fun read(): StoredSecrets = secretsFlow.first()

    suspend fun update(transform: (StoredSecrets) -> StoredSecrets) {
        dataStore.edit { preferences ->
            val current = decode(preferences[CIPHERTEXT_KEY])
            preferences[CIPHERTEXT_KEY] = encode(transform(current))
        }
    }

    suspend fun setCookie(name: String, value: String, expiresAt: Long?) {
        update { it.copy(cookieName = name, cookieValue = value, cookieExpiresAt = expiresAt) }
    }

    suspend fun clearCookie() {
        update { it.copy(cookieName = null, cookieValue = null, cookieExpiresAt = null) }
    }

    suspend fun clearPassword() {
        update { it.copy(password = null) }
    }

    suspend fun clearAll() {
        dataStore.edit { it.remove(CIPHERTEXT_KEY) }
    }

    private fun decode(encoded: String?): StoredSecrets {
        if (encoded.isNullOrBlank()) return StoredSecrets()
        val plain = encryptor.decryptString(encoded) ?: return StoredSecrets()
        return runCatching { Json.decodeFromString<StoredSecrets>(plain) }.getOrDefault(StoredSecrets())
    }

    private fun encode(secrets: StoredSecrets): String =
        encryptor.encryptString(Json.encodeToString(secrets))

    private companion object {
        val CIPHERTEXT_KEY = stringPreferencesKey("secrets_blob")
    }
}

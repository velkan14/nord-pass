package dev.damado.nordpass.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "carnet_settings")

enum class QrMode {
    /** Request the site's verified `/qrcode` PNG. Default. */
    SERVER,

    /** Draw the QR locally from the scraped token with ZXing. Experimental. */
    LOCAL,
}

class SettingsStore(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val qrMode: Flow<QrMode> = dataStore.data.map { preferences ->
        runCatching { QrMode.valueOf(preferences[QR_MODE_KEY] ?: QrMode.SERVER.name) }
            .getOrDefault(QrMode.SERVER)
    }

    suspend fun setQrMode(mode: QrMode) {
        dataStore.edit { it[QR_MODE_KEY] = mode.name }
    }

    private companion object {
        val QR_MODE_KEY = stringPreferencesKey("qr_mode")
    }
}

package dev.damado.nordpass.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.damado.nordpass.core.BASE_URL
import dev.damado.nordpass.data.network.EncryptedCookieJar
import dev.damado.nordpass.data.repository.AuthRepository
import dev.damado.nordpass.data.secure.SecretsStore
import dev.damado.nordpass.data.settings.QrMode
import dev.damado.nordpass.data.settings.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SettingsUiState(
    val qrMode: QrMode = QrMode.SERVER,
    val baseUrl: String = BASE_URL,
    val hasSession: Boolean = false,
    val isLoggingOut: Boolean = false,
)

class SettingsViewModel(
    private val settings: SettingsStore,
    private val secrets: SecretsStore,
    private val auth: AuthRepository,
    private val cookieJar: EncryptedCookieJar,
    private val onLoggedOut: () -> Unit,
) : ViewModel() {

    var uiState by mutableStateOf(SettingsUiState())
        private set

    init {
        viewModelScope.launch {
            uiState = uiState.copy(
                qrMode = settings.qrMode.first(),
                hasSession = secrets.read().hasSessionCookie || cookieJar.current() != null,
            )
        }
    }

    fun onQrModeChange(mode: QrMode) {
        viewModelScope.launch {
            settings.setQrMode(mode)
            uiState = uiState.copy(qrMode = mode)
        }
    }

    fun logout() {
        if (uiState.isLoggingOut) return
        viewModelScope.launch {
            uiState = uiState.copy(isLoggingOut = true)
            auth.logout()
            uiState = uiState.copy(isLoggingOut = false, hasSession = false)
            onLoggedOut()
        }
    }
}

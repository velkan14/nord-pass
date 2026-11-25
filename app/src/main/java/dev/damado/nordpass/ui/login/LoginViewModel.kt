package dev.damado.nordpass.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.damado.nordpass.data.network.LoginResult
import dev.damado.nordpass.data.repository.AuthRepository
import dev.damado.nordpass.data.secure.SecretsStore
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val savePassword: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
)

class LoginViewModel(
    private val auth: AuthRepository,
    private val secrets: SecretsStore,
) : ViewModel() {

    var uiState by mutableStateOf(LoginUiState())
        private set

    init {
        viewModelScope.launch {
            uiState = uiState.copy(username = secrets.read().username)
        }
    }

    fun onUsernameChange(value: String) {
        uiState = uiState.copy(username = value, error = null)
    }

    fun onPasswordChange(value: String) {
        uiState = uiState.copy(password = value, error = null)
    }

    fun onSavePasswordChange(value: Boolean) {
        uiState = uiState.copy(savePassword = value)
    }

    fun submit() {
        if (uiState.isLoading) return
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            val result = auth.login(uiState.username, uiState.password, uiState.savePassword)
            uiState = when (result) {
                is LoginResult.Success -> uiState.copy(isLoading = false, loggedIn = true, password = "")
                LoginResult.BadCredentials -> uiState.copy(
                    isLoading = false,
                    error = "Incorrect username or password.",
                )
                is LoginResult.Failure -> uiState.copy(isLoading = false, error = result.message)
            }
        }
    }

    fun onNavigated() {
        uiState = uiState.copy(loggedIn = false)
    }
}

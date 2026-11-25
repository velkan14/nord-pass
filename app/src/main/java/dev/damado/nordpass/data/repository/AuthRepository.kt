package dev.damado.nordpass.data.repository

import dev.damado.nordpass.data.network.EncryptedCookieJar
import dev.damado.nordpass.data.network.LoginResult
import dev.damado.nordpass.data.network.MiclubApi
import dev.damado.nordpass.data.secure.SecretsStore

class AuthRepository(
    private val api: MiclubApi,
    private val secrets: SecretsStore,
    private val cookieJar: EncryptedCookieJar,
) {
    /**
     * @param savePassword when false, any previously stored password is removed.
     */
    suspend fun login(username: String, password: String, savePassword: Boolean): LoginResult {
        // Username is always persisted; password only when the user opts in.
        secrets.update { it.copy(username = username, password = if (savePassword) password else null) }

        // Start from an anonymous session so form_build_id is fresh.
        cookieJar.clear()

        return when (val result = api.login(username, password)) {
            is LoginResult.Success -> result
            LoginResult.BadCredentials -> {
                secrets.clearPassword()
                result
            }
            is LoginResult.Failure -> {
                // Any failed login clears the saved password so there is no
                // silent retry loop on the next launch.
                secrets.clearPassword()
                result
            }
        }
    }

    /**
     * Best-effort server logout, then forget both the session cookie and the
     * saved password in one atomic update. The username is kept so Login can
     * prefill it. No error is propagated: local logout always wins.
     */
    suspend fun logout() {
        // Must happen while the cookie is still attached to the request.
        api.logout()
        secrets.update {
            it.copy(
                cookieName = null,
                cookieValue = null,
                cookieExpiresAt = null,
                password = null,
            )
        }
        cookieJar.clear()
    }
}

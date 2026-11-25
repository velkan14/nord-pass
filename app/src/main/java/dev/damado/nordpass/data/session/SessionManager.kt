package dev.damado.nordpass.data.session

import dev.damado.nordpass.data.network.CarnetMember
import dev.damado.nordpass.data.network.CarnetResult
import dev.damado.nordpass.data.network.EncryptedCookieJar
import dev.damado.nordpass.data.network.LoginResult
import dev.damado.nordpass.data.network.MiclubApi
import dev.damado.nordpass.data.secure.SecretsStore

sealed interface TokenOutcome {
    data class Token(
        val url: String,
        val validitySeconds: Long,
        val member: CarnetMember,
    ) : TokenOutcome
    data class NeedLogin(val reason: String) : TokenOutcome
    data class Error(val message: String) : TokenOutcome
}

/**
 * Implements the agreed state machine: use the persisted cookie first; only
 * re-login silently when a password has been explicitly saved.
 */
class SessionManager(
    private val api: MiclubApi,
    private val secrets: SecretsStore,
    private val cookieJar: EncryptedCookieJar,
) {
    suspend fun freshToken(): TokenOutcome {
        return when (val carnet = api.fetchCarnet()) {
            is CarnetResult.Success -> TokenOutcome.Token(carnet.tokenUrl, carnet.validitySeconds, carnet.member)
            is CarnetResult.Failure -> TokenOutcome.Error(carnet.message)
            CarnetResult.SessionExpired -> recoverExpiredSession()
        }
    }

    private suspend fun recoverExpiredSession(): TokenOutcome {
        val stored = secrets.read()
        if (!stored.hasSavedPassword) {
            // A first launch has no cookie at all, so "expired" would be
            // misleading. Only use that word when we actually had a session.
            val hadSession = stored.hasSessionCookie || cookieJar.current() != null
            val reason = if (hadSession) {
                "Session expired — log in again"
            } else {
                "Log in to load your carnet"
            }
            return TokenOutcome.NeedLogin(reason)
        }

        val password = requireNotNull(stored.password)
        cookieJar.clear() // force a fresh anonymous form_build_id for the re-login
        return when (val login = api.login(stored.username, password)) {
            is LoginResult.Success -> when (val carnet = api.fetchCarnet()) {
                is CarnetResult.Success -> TokenOutcome.Token(carnet.tokenUrl, carnet.validitySeconds, carnet.member)
                is CarnetResult.Failure -> TokenOutcome.Error(carnet.message)
                CarnetResult.SessionExpired -> TokenOutcome.NeedLogin("Could not load carnet after login")
            }
            LoginResult.BadCredentials -> {
                // Clear the stale password so we never retry in a loop.
                cookieJar.clear()
                secrets.clearPassword()
                TokenOutcome.NeedLogin("Saved password no longer works — log in again")
            }
            is LoginResult.Failure -> TokenOutcome.Error(login.message)
        }
    }
}

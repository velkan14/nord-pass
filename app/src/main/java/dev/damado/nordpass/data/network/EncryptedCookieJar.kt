package dev.damado.nordpass.data.network

import dev.damado.nordpass.core.COOKIE_DOMAIN
import dev.damado.nordpass.data.secure.SecretsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

data class PersistedCookie(
    val name: String,
    val value: String,
    val expiresAt: Long?,
)

/**
 * Keeps the current `SSESS…` cookie in memory for OkHttp and mirrors it into
 * the encrypted secrets blob for the next app launch.
 */
class EncryptedCookieJar(
    private val secrets: SecretsStore,
) : CookieJar {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cached: PersistedCookie? = null

    suspend fun load() {
        val stored = secrets.read()
        cached = if (stored.hasSessionCookie) {
            PersistedCookie(
                name = requireNotNull(stored.cookieName),
                value = requireNotNull(stored.cookieValue),
                expiresAt = stored.cookieExpiresAt,
            )
        } else {
            null
        }
    }

    fun current(): PersistedCookie? = cached

    /** Drop the cookie before an anonymous login page fetch. */
    fun clear() {
        cached = null
        scope.launch { secrets.clearCookie() }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.firstOrNull { it.name.startsWith("SSESS") }?.let { cookie ->
            val persisted = PersistedCookie(
                name = cookie.name,
                value = cookie.value,
                expiresAt = cookie.expiresAt.takeIf { it != Long.MAX_VALUE },
            )
            cached = persisted
            scope.launch {
                secrets.setCookie(persisted.name, persisted.value, persisted.expiresAt)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookie = cached ?: return emptyList()
        return listOf(
            Cookie.Builder()
                .name(cookie.name)
                .value(cookie.value)
                .domain(COOKIE_DOMAIN)
                .path("/")
                .secure()
                .expiresAt(cookie.expiresAt ?: Long.MAX_VALUE)
                .build(),
        )
    }
}

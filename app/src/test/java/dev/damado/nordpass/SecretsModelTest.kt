package dev.damado.nordpass

import dev.damado.nordpass.core.BASE_URL
import dev.damado.nordpass.core.COOKIE_DOMAIN
import dev.damado.nordpass.data.secure.StoredSecrets
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Placeholder for the fixture-based tests described in the design doc. */
class SecretsModelTest {
    @Test
    fun `empty secrets contain no session or password`() {
        val secrets = StoredSecrets()
        assertFalse(secrets.hasSessionCookie)
        assertFalse(secrets.hasSavedPassword)
    }

    @Test
    fun `rebuilt session cookie is accepted by OkHttp and matches the site host`() {
        // Regression: OkHttp throws "unexpected domain" for a leading dot.
        val cookie = Cookie.Builder()
            .name("SSESS_test")
            .value("test")
            .domain(COOKIE_DOMAIN)
            .path("/")
            .secure()
            .build()

        assertTrue(cookie.matches(BASE_URL.toHttpUrl()))
    }
}

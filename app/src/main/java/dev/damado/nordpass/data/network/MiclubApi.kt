package dev.damado.nordpass.data.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import dev.damado.nordpass.core.BAD_CREDENTIALS_MARKER
import dev.damado.nordpass.core.BASE_URL
import dev.damado.nordpass.core.CARNET_MARKER
import dev.damado.nordpass.core.CARNET_PATH
import dev.damado.nordpass.core.LOGIN_FORM_MARKER
import dev.damado.nordpass.core.LOGIN_PATH
import dev.damado.nordpass.core.LOGOUT_PATH
import dev.damado.nordpass.core.QRCODE_PATH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.net.URLEncoder

/**
 * Thin HTTP layer. It never calls `/user-active/...`: that URL is only ever
 * treated as text to be encoded into a QR code.
 */
class MiclubApi(
    private val client: OkHttpClient,
    private val cookieJar: EncryptedCookieJar,
) {

    suspend fun login(username: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            val loginUrl = BASE_URL.toHttpUrl().resolve(LOGIN_PATH)
                ?: return@withContext LoginResult.Failure("Invalid login URL")

            val formHtml = execute(Request.Builder().url(loginUrl).get().build()).use { response ->
                if (!response.isSuccessful) {
                    return@withContext LoginResult.Failure("Login page HTTP ${response.code}")
                }
                response.body?.string().orEmpty()
            }

            val form = LoginFormParser.parse(formHtml, loginUrl)
                ?: return@withContext LoginResult.Failure("Login form not found in page")

            val bodyBuilder = FormBody.Builder()
                .add("name", username)
                .add("pass", password)
            // form_build_id is a Drupal cache key, not an auth token, so a
            // missing one doesn't block submission.
            if (form.buildId != null) {
                bodyBuilder.add("form_build_id", form.buildId)
            }
            val body = bodyBuilder
                .add("form_id", form.formId)
                .add("op", "Entra")
                .build()

            val request = Request.Builder()
                .url(form.action)
                .header("Referer", loginUrl.toString())
                .post(body)
                .build()

            execute(request).use { response ->
                val html = response.body?.string().orEmpty()
                when {
                    html.contains(BAD_CREDENTIALS_MARKER, ignoreCase = false) -> LoginResult.BadCredentials
                    html.contains(LOGIN_FORM_MARKER) || response.request.url.encodedPath.contains("user/login") ->
                        LoginResult.BadCredentials
                    response.request.url.encodedPath == "/user/info" && response.isSuccessful -> {
                        val cookie = cookieJar.current()
                        if (cookie != null) {
                            LoginResult.Success(cookie.name, cookie.value)
                        } else {
                            LoginResult.Failure("Login succeeded but no session cookie was found")
                        }
                    }
                    else -> LoginResult.Failure("Unexpected login response (HTTP ${response.code})")
                }
            }
        } catch (e: IOException) {
            LoginResult.Failure("Network error: ${e.message ?: "unknown"}")
        }
    }

    /**
     * Drupal 7 logs out on a plain GET with no CSRF token. This is best-effort:
     * the caller always clears local state regardless of the result, and we do
     * not care about the 302/redirect target.
     */
    suspend fun logout() = withContext(Dispatchers.IO) {
        val url = BASE_URL.toHttpUrl().resolve(LOGOUT_PATH) ?: return@withContext
        runCatching { execute(Request.Builder().url(url).get().build()).close() }
    }

    suspend fun fetchCarnet(): CarnetResult = withContext(Dispatchers.IO) {
        try {
            val url = BASE_URL.toHttpUrl().resolve(CARNET_PATH)
                ?: return@withContext CarnetResult.Failure("Invalid carnet URL")

            execute(Request.Builder().url(url).get().build()).use { response ->
                val html = response.body?.string().orEmpty()
                when {
                    response.request.url.encodedPath.contains("user/login") ||
                        html.contains(LOGIN_FORM_MARKER) ||
                        !html.contains(CARNET_MARKER) -> CarnetResult.SessionExpired
                    else -> {
                        val page = CarnetPageParser.parse(html)
                        if (page == null) {
                            CarnetResult.Failure("Carnet token not found in page")
                        } else {
                            CarnetResult.Success(
                                tokenUrl = page.tokenUrl,
                                validitySeconds = page.validitySeconds,
                                member = CarnetMember(
                                    name = page.memberName,
                                    photo = decodePhoto(page.photoBase64),
                                ),
                            )
                        }
                    }
                }
            }
        } catch (e: IOException) {
            CarnetResult.Failure("Network error: ${e.message ?: "unknown"}")
        }
    }

    suspend fun fetchQrPng(tokenUrl: String): ByteArray = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(tokenUrl, "UTF-8").replace("+", "%20")
        val url = "$BASE_URL$QRCODE_PATH?text=$encoded&size=2"
        execute(Request.Builder().url(url).get().build()).use { response ->
            if (!response.isSuccessful) throw IOException("QR request failed: HTTP ${response.code}")
            response.body?.bytes() ?: throw IOException("Empty QR response")
        }
    }

    private fun execute(request: Request) = client.newCall(request).execute()

    private fun decodePhoto(value: String?): Bitmap? {
        if (value.isNullOrBlank()) return null
        // Tolerate a data-URI prefix if the site ever adds one.
        // Base64 in HTML/JSON may contain line breaks; strip all whitespace.
        val base64 = value.substringAfter("base64,", value).filter { !it.isWhitespace() }
        if (base64.isEmpty()) return null
        val bytes = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}

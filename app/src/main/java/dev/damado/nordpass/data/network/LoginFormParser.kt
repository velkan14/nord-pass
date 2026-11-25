package dev.damado.nordpass.data.network

import dev.damado.nordpass.core.LOGIN_FORM_MARKER
import dev.damado.nordpass.core.LOGIN_PATH
import okhttp3.HttpUrl
import org.jsoup.Jsoup

internal data class LoginForm(
    val action: HttpUrl,
    val buildId: String?,
    val formId: String,
)

/** Scrapes the hidden fields from the Drupal login form using a real HTML DOM. */
internal object LoginFormParser {

    fun parse(html: String, loginUrl: HttpUrl): LoginForm? {
        val form = Jsoup.parse(html).selectFirst("form#$LOGIN_FORM_MARKER") ?: return null

        val action = form.attr("action").ifBlank { LOGIN_PATH }
        val resolvedAction = loginUrl.resolve(action) ?: return null

        val buildId = form.selectFirst("input[name=form_build_id]")
            ?.attr("value")
            ?.ifBlank { null }
        val formId = form.selectFirst("input[name=form_id]")
            ?.attr("value")
            ?.ifBlank { null }
            ?: "user_login_block"
        return LoginForm(resolvedAction, buildId, formId)
    }
}

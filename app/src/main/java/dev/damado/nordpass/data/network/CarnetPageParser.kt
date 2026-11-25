package dev.damado.nordpass.data.network

import dev.damado.nordpass.core.BASE_URL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup

internal data class CarnetPage(
    val tokenUrl: String,
    val validitySeconds: Long,
    val memberName: String?,
    val photoBase64: String?,
)

/**
 * Extracts the token URL, its lifetime and the member-card fields from the
 * carnet page's inline Drupal.settings JavaScript.
 */
internal object CarnetPageParser {
    private const val DEFAULT_VALIDITY_SECONDS = 10L
    private const val SETTINGS_MARKER = "jQuery.extend(Drupal.settings,"

    fun parse(html: String): CarnetPage? {
        // Jsoup finds the script blocks; only the small JS literal still needs
        // a pattern, since it is not HTML.
        val scripts = Jsoup.parse(html)
            .select("script")
            .joinToString("\n") { it.data() }

        val tokenUrl = REGEX_TOKEN_URL.find(scripts)
            ?.groupValues?.get(1)
            ?.replace("\\/", "/")
            ?.trim()
            ?.let(::normalizeUrl)
            ?: return null

        // The site itself does setTimeout(..., caducidad * 1000). Fall back to
        // the observed value if the key is ever removed.
        val validitySeconds = REGEX_CADUCIDAD.find(scripts)
            ?.groupValues?.get(1)
            ?.toLongOrNull()
            ?.coerceAtLeast(1L)
            ?: DEFAULT_VALIDITY_SECONDS

        val settings = parseSettings(scripts)
        val memberName = settings?.takeIf { it.visible("nombre") }?.string("nombre")
        val photoBase64 = settings?.takeIf { it.visible("foto") }?.string("foto")

        return CarnetPage(tokenUrl, validitySeconds, memberName, photoBase64)
    }

    /** Finds and JSON-decodes the `jQuery.extend(Drupal.settings, {...})` block. */
    private fun parseSettings(scripts: String): JsonObject? {
        val markerPos = scripts.indexOf(SETTINGS_MARKER)
        if (markerPos < 0) return null
        val start = scripts.indexOf('{', markerPos + SETTINGS_MARKER.length)
        if (start < 0) return null
        val end = findClosingBrace(scripts, start) ?: return null

        return runCatching {
            Json.parseToJsonElement(scripts.substring(start, end + 1)).jsonObject
        }.getOrNull()
    }

    /** String-aware brace matching so nested objects and braces in strings work. */
    private fun findClosingBrace(text: String, start: Int): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.visible(key: String): Boolean {
        val value = this["${key}_visible"] ?: return true
        return when (value) {
            is JsonPrimitive -> when {
                value.isString -> value.content.equals("true", true) || value.content == "1"
                else -> value.booleanOrNull ?: true
            }
            else -> true
        }
    }

    private fun normalizeUrl(value: String) = when {
        value.startsWith("http://") || value.startsWith("https://") -> value
        value.startsWith("/") -> BASE_URL + value
        else -> "$BASE_URL/$value"
    }

    private val REGEX_TOKEN_URL = Regex(
        """\b["']?url["']?\s*:\s*["']([^"']*user-active[^"']*)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val REGEX_CADUCIDAD = Regex(
        """\bcaducidad["']?\s*:\s*["']?(\d+)["']?""",
        RegexOption.IGNORE_CASE,
    )
}

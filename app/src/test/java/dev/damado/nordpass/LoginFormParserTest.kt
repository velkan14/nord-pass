package dev.damado.nordpass

import dev.damado.nordpass.core.BASE_URL
import dev.damado.nordpass.data.network.LoginFormParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoginFormParserTest {

    // Mirrors the real page: hidden inputs are inside the form body, and other
    // forms appear before it. A regex over just the opening tag misses this.
    private val page = """
        <html><body>
          <form action="/user/login" method="post" id="gpa-piw-register-block-form">
            <input type="hidden" name="form_build_id" value="form-WRONG" />
          </form>
          <form action="/user/login?destination=user/info" method="post" id="user-login-form" accept-charset="UTF-8">
            <input type="text" id="edit-name" name="name" value="" size="15" maxlength="60" />
            <input type="password" id="edit-pass" name="pass" size="15" maxlength="128" />
            <input type="hidden" name="form_build_id" value="form-abc123" />
            <input type="hidden" name="form_id" value="user_login_block" />
            <input type="submit" id="edit-submit" value="Entra" />
          </form>
        </body></html>
    """.trimIndent()

    @Test
    fun `parses hidden fields from the login form body`() {
        val form = LoginFormParser.parse(page, BASE_URL.toHttpUrl())!!

        assertEquals("/user/login", form.action.encodedPath)
        assertEquals("user/info", form.action.queryParameter("destination"))
        assertEquals("form-abc123", form.buildId)
        assertEquals("user_login_block", form.formId)
    }

    @Test
    fun `returns null when the login form is absent`() {
        assertNull(LoginFormParser.parse("<html><body>no form</body></html>", BASE_URL.toHttpUrl()))
    }
}

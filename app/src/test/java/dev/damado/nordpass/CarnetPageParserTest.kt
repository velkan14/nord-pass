package dev.damado.nordpass

import dev.damado.nordpass.core.BASE_URL
import dev.damado.nordpass.data.network.CarnetPageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarnetPageParserTest {

    private val tokenUrl = "$BASE_URL/user-active/12345/abcdef0123456789"
    private val escapedTokenUrl = tokenUrl.replace("/", "\\/")

    @Test
    fun `reads token URL and caducidad from inline settings`() {
        val html = """
            <html><body>
              <script>var unrelated = 1;</script>
              <script>
                jQuery.extend(Drupal.settings, {
                  "url": "$escapedTokenUrl",
                  "caducidad": 10
                });
              </script>
            </body></html>
        """.trimIndent()

        val page = CarnetPageParser.parse(html)!!

        assertEquals(tokenUrl, page.tokenUrl)
        assertEquals(10L, page.validitySeconds)
    }

    @Test
    fun `falls back to 10 seconds when caducidad is absent`() {
        val html = """
            <html><body><script>
              Drupal.settings = { url: "$tokenUrl" };
            </script></body></html>
        """.trimIndent()

        assertEquals(10L, CarnetPageParser.parse(html)!!.validitySeconds)
    }

    @Test
    fun `reads member name and photo from settings JSON`() {
        // Nested objects and a '}' inside a string must not end the JSON early.
        val html = """
            <html><body><script>
              jQuery.extend(Drupal.settings, {
                "url": "$escapedTokenUrl",
                "caducidad": 10,
                "nombre": "Alex Example",
                "foto": "/9j/2Q==",
                "nombre_visible": true,
                "foto_visible": true,
                "extra": { "note": "a } brace", "nested": { "x": 1 } }
              });
            </script></body></html>
        """.trimIndent()

        val page = CarnetPageParser.parse(html)!!

        assertEquals("Alex Example", page.memberName)
        assertEquals("/9j/2Q==", page.photoBase64)
    }

    @Test
    fun `hides fields when their visibility flags are off`() {
        val html = """
            <html><body><script>
              jQuery.extend(Drupal.settings, {
                "url": "$escapedTokenUrl",
                "nombre": "Hidden Name",
                "foto": "hidden-photo",
                "nombre_visible": false,
                "foto_visible": "0"
              });
            </script></body></html>
        """.trimIndent()

        val page = CarnetPageParser.parse(html)!!

        assertNull(page.memberName)
        assertNull(page.photoBase64)
    }

    @Test
    fun `returns null when no token URL is present`() {
        assertNull(CarnetPageParser.parse("<html><body><p>No carnet</p></body></html>"))
    }
}

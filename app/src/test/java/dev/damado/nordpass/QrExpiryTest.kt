package dev.damado.nordpass

import dev.damado.nordpass.ui.carnet.QrExpiry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrExpiryTest {

    @Test
    fun `full duration reserves the one second margin`() {
        assertEquals(9_000L, QrExpiry.fullDurationMs(10_000L))
    }

    @Test
    fun `token is not expired just before the margin boundary`() {
        val remaining = QrExpiry.timeUntilRefresh(10_000L, 8_999L)

        assertEquals(1L, remaining)
        assertFalse(QrExpiry.isExpired(10_000L, 8_999L))
    }

    @Test
    fun `token is expired exactly at the margin boundary`() {
        assertEquals(0L, QrExpiry.timeUntilRefresh(10_000L, 9_000L))
        assertTrue(QrExpiry.isExpired(10_000L, 9_000L))
    }

    @Test
    fun `token is expired throughout the nominal last second`() {
        assertTrue(QrExpiry.isExpired(10_000L, 9_500L))
        assertTrue(QrExpiry.isExpired(10_000L, 10_000L))
        assertTrue(QrExpiry.isExpired(10_000L, 15_000L))
    }

    @Test
    fun `very short validity still leaves a one millisecond window`() {
        assertEquals(1L, QrExpiry.fullDurationMs(500L))
        assertFalse(QrExpiry.isExpired(500L, 0L))
        assertTrue(QrExpiry.isExpired(500L, 1L))
    }
}

package dev.damado.nordpass.ui.carnet

/**
 * Pure timing rules for a carnet QR token.
 *
 * The site reports the token lifetime as `caducidad` seconds. We refresh one
 * second early so an invalid QR is never shown, including during the network
 * call that replaces it.
 */
internal object QrExpiry {
    const val REFRESH_MARGIN_MS = 1_000L

    /** The window we actually treat as usable, after the safety margin. */
    fun fullDurationMs(validityMs: Long): Long =
        (validityMs - REFRESH_MARGIN_MS).coerceAtLeast(1L)

    /** 0 means an aged token should be replaced immediately. */
    fun timeUntilRefresh(validityMs: Long, ageMs: Long): Long =
        (fullDurationMs(validityMs) - ageMs).coerceAtLeast(0L)

    fun isExpired(validityMs: Long, ageMs: Long): Boolean =
        timeUntilRefresh(validityMs, ageMs) == 0L
}

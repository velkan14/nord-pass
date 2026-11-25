package dev.damado.nordpass.data.secure

import kotlinx.serialization.Serializable

/**
 * Everything sensitive the app keeps. Serialized to JSON, encrypted with a
 * Keystore-backed AES-GCM key, and stored as a single ciphertext blob.
 *
 * Password is nullable by design: it is only written when the user opts in.
 */
@Serializable
data class StoredSecrets(
    val username: String = "",
    val cookieName: String? = null,
    val cookieValue: String? = null,
    val cookieExpiresAt: Long? = null,
    val password: String? = null,
) {
    val hasSessionCookie: Boolean
        get() = !cookieName.isNullOrBlank() && !cookieValue.isNullOrBlank()

    val hasSavedPassword: Boolean
        get() = !password.isNullOrEmpty()
}

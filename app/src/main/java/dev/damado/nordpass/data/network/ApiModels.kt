package dev.damado.nordpass.data.network

import android.graphics.Bitmap

/** Member-card details shown alongside the QR. Never persisted. */
data class CarnetMember(
    val name: String?,
    val photo: Bitmap?,
)

sealed interface LoginResult {
    data class Success(val cookieName: String, val cookieValue: String) : LoginResult
    data object BadCredentials : LoginResult
    data class Failure(val message: String) : LoginResult
}

sealed interface CarnetResult {
    /**
     * The short-lived URL that gets encoded into the QR, plus the lifetime in
     * seconds reported by the page (`caducidad`). Never execute the URL.
     */
    data class Success(
        val tokenUrl: String,
        val validitySeconds: Long,
        val member: CarnetMember,
    ) : CarnetResult
    data object SessionExpired : CarnetResult
    data class Failure(val message: String) : CarnetResult
}

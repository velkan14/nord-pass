package dev.damado.nordpass.core

/**
 * The single site this personal app talks to. Kept as a constant: the design
 * deliberately does not support multiple accounts/sites.
 */
const val BASE_URL = "https://eurofitness-estaciodelnord.miclubonline.net"
const val LOGIN_PATH = "/user/login?destination=user/info"
const val LOGOUT_PATH = "/user/logout"
const val CARNET_PATH = "/user/carnet"
const val QRCODE_PATH = "/qrcode"

/**
 * Domain used when rebuilding the persisted `SSESS` cookie for OkHttp.
 * No leading dot: OkHttp's Cookie.Builder rejects ".miclubonline.net", and a
 * bare domain still matches all subdomains (including our site host).
 */
const val COOKIE_DOMAIN = "miclubonline.net"

/**
 * Markers used to detect auth state from the site's HTML.
 * These match the server's Catalan response and are never shown in the UI;
 * the app displays its own English error strings.
 */
const val CARNET_MARKER = "Carnet Virtual"
const val LOGIN_FORM_MARKER = "user-login-form"
const val BAD_CREDENTIALS_MARKER = "Nom d'usuari o contrasenya incorrectes"

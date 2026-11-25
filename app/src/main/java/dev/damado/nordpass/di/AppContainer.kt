package dev.damado.nordpass.di

import android.content.Context
import okhttp3.OkHttpClient
import dev.damado.nordpass.data.network.EncryptedCookieJar
import dev.damado.nordpass.data.network.MiclubApi
import dev.damado.nordpass.data.repository.AuthRepository
import dev.damado.nordpass.data.repository.CarnetRepository
import dev.damado.nordpass.data.secure.KeystoreEncryptor
import dev.damado.nordpass.data.secure.SecretsStore
import dev.damado.nordpass.data.session.SessionManager
import dev.damado.nordpass.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Manual dependency container for the skeleton. Replace with Hilt/Koin if the
 * app grows; there is intentionally no reflection or annotation processing here.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val encryptor = KeystoreEncryptor(appContext)

    val secrets: SecretsStore = SecretsStore(appContext, encryptor)
    val settings: SettingsStore = SettingsStore(appContext)
    val cookieJar: EncryptedCookieJar = EncryptedCookieJar(secrets)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .build()

    val api: MiclubApi = MiclubApi(httpClient, cookieJar)
    val auth: AuthRepository = AuthRepository(api, secrets, cookieJar)
    val carnet: CarnetRepository = CarnetRepository(api, settings)
    val session: SessionManager = SessionManager(api, secrets, cookieJar)

    init {
        // Load the encrypted cookie once at startup so the first QR request can
        // use it. This is a small DataStore read on the IO dispatcher.
        runBlocking(Dispatchers.IO) {
            cookieJar.load()
        }
    }
}

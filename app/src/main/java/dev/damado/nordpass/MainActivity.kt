package dev.damado.nordpass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.damado.nordpass.ui.CarnetApp
import dev.damado.nordpass.ui.theme.CarnetTheme
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {

    // The system splash stays up until CarnetViewModel has resolved the first
    // session check, so the user never sees a blank/stepping-stone screen.
    private val startupCheckComplete = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !startupCheckComplete.get() }

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as CarnetApplication).container
        setContent {
            CarnetTheme {
                CarnetApp(
                    container = container,
                    onInitialCheckComplete = { startupCheckComplete.set(true) },
                )
            }
        }
    }
}

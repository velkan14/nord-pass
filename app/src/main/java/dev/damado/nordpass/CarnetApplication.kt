package dev.damado.nordpass

import android.app.Application
import dev.damado.nordpass.di.AppContainer

class CarnetApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

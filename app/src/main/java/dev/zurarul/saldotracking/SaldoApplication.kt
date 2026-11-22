package dev.zurarul.saldotracking

import android.app.Application

class SaldoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

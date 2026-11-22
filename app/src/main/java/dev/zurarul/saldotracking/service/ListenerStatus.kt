package dev.zurarul.saldotracking.service

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ListenerStatus {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun set(value: Boolean) {
        _connected.value = value
    }

    fun isAccessGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    fun requestRebind(context: Context) {
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(context, SaldoNotificationListener::class.java)
            )
        }
    }
}

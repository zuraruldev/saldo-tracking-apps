package dev.zurarul.saldotracking.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.zurarul.saldotracking.SaldoApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        ListenerStatus.requestRebind(context)
        val app = context.applicationContext as? SaldoApplication ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = app.container.settingsRepository.settings.first()
                if (s.keepAliveEnabled) {
                    KeepAliveService.start(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

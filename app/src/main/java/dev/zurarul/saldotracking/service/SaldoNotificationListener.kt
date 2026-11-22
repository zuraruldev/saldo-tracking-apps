package dev.zurarul.saldotracking.service

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.zurarul.saldotracking.SaldoApplication
import dev.zurarul.saldotracking.parser.RawNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SaldoNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        ListenerStatus.set(true)
        val app = application as? SaldoApplication ?: return
        scope.launch {
            val s = app.container.settingsRepository.settings.first()
            if (s.keepAliveEnabled) {
                KeepAliveService.start(this@SaldoNotificationListener)
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        ListenerStatus.set(false)
        runCatching {
            requestRebind(ComponentName(this, SaldoNotificationListener::class.java))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        val pkg = posted.packageName ?: return
        if (pkg == packageName) return

        val notification = posted.notification ?: return
        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val normalText = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val text = (bigText ?: normalText).orEmpty()

        val flags = notification.flags
        val isOngoing = (flags and Notification.FLAG_ONGOING_EVENT) != 0
        val isGroupSummary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0

        val raw = RawNotification(
            packageName = pkg,
            title = title,
            text = text,
            postedAtMillis = posted.postTime,
            isOngoing = isOngoing,
            isGroupSummary = isGroupSummary
        )

        val app = application as? SaldoApplication ?: return
        scope.launch {
            val enabled = app.container.settingsRepository.settings.first().enabledWallets
            val outcome = app.container.processor.process(raw, enabled) ?: return@launch
            app.container.repository.record(outcome)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

package dev.zurarul.saldotracking.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.zurarul.saldotracking.AppContainer
import dev.zurarul.saldotracking.SaldoApplication
import dev.zurarul.saldotracking.data.AppSettings
import dev.zurarul.saldotracking.data.BackupData
import dev.zurarul.saldotracking.data.ThemeMode
import dev.zurarul.saldotracking.domain.NotificationLog
import dev.zurarul.saldotracking.domain.Wallet
import dev.zurarul.saldotracking.service.KeepAliveService
import dev.zurarul.saldotracking.service.ListenerStatus
import dev.zurarul.saldotracking.ui.home.TransactionDraft
import dev.zurarul.saldotracking.ui.home.toTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val container: AppContainer
) : ViewModel() {
    val settings: StateFlow<AppSettings> = container.settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings()
        )

    val logs: StateFlow<List<NotificationLog>> = container.repository.recentLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _accessGranted = MutableStateFlow(false)
    val accessGranted: StateFlow<Boolean> = _accessGranted.asStateFlow()

    private val _batteryExempt = MutableStateFlow(false)
    val batteryExempt: StateFlow<Boolean> = _batteryExempt.asStateFlow()

    val listenerConnected: StateFlow<Boolean> = ListenerStatus.connected

    fun refreshStatus(context: Context) {
        _accessGranted.value = ListenerStatus.isAccessGranted(context)
        _batteryExempt.value = SetupActions.isIgnoringBatteryOptimizations(context)
    }

    fun setKeepAlive(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setKeepAlive(enabled)
            if (enabled) {
                KeepAliveService.start(context)
            } else {
                KeepAliveService.stop(context)
            }
        }
    }

    fun setWalletEnabled(wallet: Wallet, enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setWalletEnabled(wallet, enabled)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            container.settingsRepository.setThemeMode(mode)
        }
    }

    fun promoteLog(log: NotificationLog, draft: TransactionDraft) {
        viewModelScope.launch {
            val baseTx = draft.toTransaction(null) ?: return@launch
            container.repository.add(
                baseTx.copy(
                    sourcePackage = log.packageName,
                    rawNotificationText = "${log.title}\n${log.text}",
                    isManual = true
                )
            )
        }
    }

    suspend fun exportJson(): String = container.repository.exportJson()

    fun prepareImport(json: String): BackupData = container.repository.prepareImport(json)

    fun applyImport(data: BackupData, onDone: () -> Unit) {
        viewModelScope.launch {
            container.repository.applyImport(data)
            onDone()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SaldoApplication
                SettingsViewModel(app.container)
            }
        }
    }
}

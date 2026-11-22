package dev.zurarul.saldotracking.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.zurarul.saldotracking.AppContainer
import dev.zurarul.saldotracking.SaldoApplication
import dev.zurarul.saldotracking.data.AppSettings
import dev.zurarul.saldotracking.domain.Transaction
import dev.zurarul.saldotracking.service.ListenerStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class HomeViewModel(
    private val container: AppContainer
) : ViewModel() {
    private val accessGranted = MutableStateFlow(false)
    private val nowFlow = MutableStateFlow(Instant.now())
    private var latestTransactions: List<Transaction> = emptyList()

    val uiState: StateFlow<HomeUiState> = combine(
        container.settingsRepository.settings,
        container.repository.transactions,
        ListenerStatus.connected,
        accessGranted,
        nowFlow
    ) { settings, txs, connected, granted, now ->
        latestTransactions = txs
        buildHomeUiState(
            settings = settings,
            txs = txs,
            autoTrackOn = isAutoTrackActive(connected = connected, accessGranted = granted),
            now = now,
            zone = ZoneId.systemDefault()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildHomeUiState(
            settings = AppSettings(),
            txs = emptyList(),
            autoTrackOn = false,
            now = Instant.now(),
            zone = ZoneId.systemDefault()
        )
    )

    fun refreshAccess(context: Context) {
        nowFlow.value = Instant.now()
        val granted = ListenerStatus.isAccessGranted(context)
        accessGranted.value = granted
        if (granted && !ListenerStatus.connected.value) {
            ListenerStatus.requestRebind(context)
        }
    }

    fun findDraft(id: Long): TransactionDraft? =
        latestTransactions.firstOrNull { it.id == id }?.toDraft()

    fun save(draft: TransactionDraft) {
        viewModelScope.launch {
            val original = if (draft.id != 0L) {
                latestTransactions.firstOrNull { it.id == draft.id }
            } else {
                null
            }
            val tx = draft.toTransaction(original) ?: return@launch
            if (original == null) {
                container.repository.add(tx)
            } else {
                container.repository.update(tx)
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            val existing = latestTransactions.firstOrNull { it.id == id } ?: return@launch
            container.repository.delete(existing)
        }
    }

    fun reconcile(actualSaldoIdr: Long) {
        viewModelScope.launch {
            container.repository.reconcileTo(actualSaldoIdr)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SaldoApplication
                HomeViewModel(app.container)
            }
        }
    }
}

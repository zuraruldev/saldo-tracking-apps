package dev.zurarul.saldotracking.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.zurarul.saldotracking.AppContainer
import dev.zurarul.saldotracking.SaldoApplication
import dev.zurarul.saldotracking.stats.Period
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class StatsViewModel(
    container: AppContainer
) : ViewModel() {
    private val periodFlow = MutableStateFlow(Period.DAILY)
    private val todayFlow = MutableStateFlow(LocalDate.now(ZoneId.systemDefault()))
    private val anchorFlow = MutableStateFlow(YearMonth.from(todayFlow.value))

    val uiState: StateFlow<StatsUiState> = combine(
        container.repository.transactions,
        periodFlow,
        anchorFlow,
        todayFlow
    ) { txs, period, anchor, today ->
        val zone = ZoneId.systemDefault()
        buildStatsUiState(
            txs = txs,
            period = period,
            anchor = anchor,
            zone = zone,
            today = today
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildStatsUiState(
            txs = emptyList(),
            period = Period.DAILY,
            anchor = YearMonth.now(),
            zone = ZoneId.systemDefault(),
            today = LocalDate.now()
        )
    )

    fun refreshToday() {
        val zone = ZoneId.systemDefault()
        val newToday = LocalDate.now(zone)
        val prevToday = todayFlow.value
        if (newToday != prevToday) {
            anchorFlow.value = rollAnchorForNewToday(anchorFlow.value, prevToday, newToday)
            todayFlow.value = newToday
        }
    }

    fun setPeriod(period: Period) {
        periodFlow.value = period
    }

    fun shift(step: Int) {
        val current = uiState.value
        if (step < 0 && !current.canGoPrev) return
        if (step > 0 && !current.canGoNext) return
        anchorFlow.value = shiftAnchor(current.period, current.anchor, step)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SaldoApplication
                StatsViewModel(app.container)
            }
        }
    }
}

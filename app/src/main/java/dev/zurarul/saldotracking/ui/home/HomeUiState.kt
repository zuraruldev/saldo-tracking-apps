package dev.zurarul.saldotracking.ui.home

import dev.zurarul.saldotracking.data.AppSettings
import dev.zurarul.saldotracking.domain.AmountSign
import dev.zurarul.saldotracking.domain.BalanceCalculator
import dev.zurarul.saldotracking.domain.Transaction
import dev.zurarul.saldotracking.domain.TxType
import dev.zurarul.saldotracking.domain.formatRupiah
import dev.zurarul.saldotracking.domain.monthShortId
import dev.zurarul.saldotracking.stats.StatsCalculator
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

data class TransactionRow(
    val id: Long,
    val title: String,
    val sourceApp: String,
    val timeLabel: String,
    val amountText: String,
    val isIncome: Boolean
)

data class HomeUiState(
    val saldoText: String,
    val monthIncomeText: String,
    val monthExpenseText: String,
    val rows: List<TransactionRow>,
    val autoTrackOn: Boolean
)

fun buildHomeUiState(
    settings: AppSettings,
    txs: List<Transaction>,
    autoTrackOn: Boolean,
    now: Instant,
    zone: ZoneId
): HomeUiState {
    val totalSaldo = BalanceCalculator.currentSaldo(settings.baseBalanceIdr, txs)
    val currentMonth = YearMonth.from(now.atZone(zone))
    val monthFlow = StatsCalculator.monthTotals(txs, currentMonth, zone)
    val sorted = txs.sortedWith(
        compareByDescending<Transaction> { it.timestampMillis }.thenByDescending { it.id }
    )
    val rows = sorted.map { tx ->
        val isIncome = tx.type == TxType.INCOME
        val sign = if (isIncome) AmountSign.PLUS else AmountSign.MINUS
        TransactionRow(
            id = tx.id,
            title = tx.title,
            sourceApp = tx.sourceApp,
            timeLabel = timeLabel(tx.timestampMillis, now, zone),
            amountText = formatRupiah(tx.amountIdr, sign),
            isIncome = isIncome
        )
    }
    return HomeUiState(
        saldoText = formatRupiah(totalSaldo),
        monthIncomeText = formatRupiah(monthFlow.incomeIdr, AmountSign.PLUS),
        monthExpenseText = formatRupiah(monthFlow.expenseIdr, AmountSign.MINUS),
        rows = rows,
        autoTrackOn = autoTrackOn
    )
}

fun timeLabel(ts: Long, now: Instant, zone: ZoneId): String {
    val zdt = Instant.ofEpochMilli(ts).atZone(zone)
    val nowZdt = now.atZone(zone)
    val txDate = zdt.toLocalDate()
    val today = nowZdt.toLocalDate()
    val hhmm = String.format(Locale.US, "%02d:%02d", zdt.hour, zdt.minute)
    val datePart = when {
        txDate == today -> "Hari ini"
        txDate == today.minusDays(1) -> "Kemarin"
        txDate.year == today.year -> "${txDate.dayOfMonth} ${monthShortId(txDate.monthValue)}"
        else -> "${txDate.dayOfMonth} ${monthShortId(txDate.monthValue)} ${txDate.year}"
    }
    return "$datePart · $hhmm"
}

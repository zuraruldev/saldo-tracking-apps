package dev.zurarul.saldotracking.ui.stats

import dev.zurarul.saldotracking.domain.AmountSign
import dev.zurarul.saldotracking.domain.Transaction
import dev.zurarul.saldotracking.domain.formatRupiah
import dev.zurarul.saldotracking.domain.formatRupiahCompact
import dev.zurarul.saldotracking.domain.monthShortId
import dev.zurarul.saldotracking.stats.Bucket
import dev.zurarul.saldotracking.stats.Period
import dev.zurarul.saldotracking.stats.StatsCalculator
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

data class AppSummaryRow(
    val sourceApp: String,
    val countText: String,
    val incomeText: String,
    val expenseText: String
)

data class StatsUiState(
    val period: Period,
    val anchor: YearMonth,
    val title: String,
    val canGoPrev: Boolean,
    val canGoNext: Boolean,
    val incomeText: String,
    val expenseText: String,
    val netText: String,
    val netPositive: Boolean,
    val incomeShare: Float?,
    val ratioLabel: String,
    val buckets: List<Bucket>,
    val perApp: List<AppSummaryRow>
)

fun buildStatsUiState(
    txs: List<Transaction>,
    period: Period,
    anchor: YearMonth,
    zone: ZoneId,
    today: LocalDate
): StatsUiState {
    val snap = StatsCalculator.snapshot(
        txs = txs,
        period = period,
        anchor = anchor,
        zone = zone,
        currentYear = today.year
    )
    val todayMonth = YearMonth.from(today)
    val canGoPrev = period != Period.YEARLY
    val canGoNext = when (period) {
        Period.DAILY -> anchor.plusMonths(1) <= todayMonth
        Period.MONTHLY -> anchor.year + 1 <= today.year
        Period.YEARLY -> false
    }

    val net = snap.totals.netIdr
    val netText = when {
        net > 0L -> "Net +${formatRupiahCompact(net)}"
        net < 0L -> "Net -${formatRupiahCompact(abs(net))}"
        else -> "Net Rp 0"
    }

    val share = snap.totals.incomeShare
    val ratioLabel = if (share == null) {
        "Belum ada data"
    } else {
        val inPct = (share * 100f).roundToInt().coerceIn(0, 100)
        val outPct = 100 - inPct
        "$inPct% masuk · $outPct% keluar"
    }

    val perAppRows = snap.perApp.map { summary ->
        AppSummaryRow(
            sourceApp = summary.sourceApp,
            countText = "${summary.count} tx",
            incomeText = formatRupiah(summary.incomeIdr, AmountSign.PLUS),
            expenseText = formatRupiah(summary.expenseIdr, AmountSign.MINUS)
        )
    }

    return StatsUiState(
        period = period,
        anchor = anchor,
        title = snap.title,
        canGoPrev = canGoPrev,
        canGoNext = canGoNext,
        incomeText = formatRupiah(snap.totals.incomeIdr, AmountSign.PLUS),
        expenseText = formatRupiah(snap.totals.expenseIdr, AmountSign.MINUS),
        netText = netText,
        netPositive = net >= 0L,
        incomeShare = share,
        ratioLabel = ratioLabel,
        buckets = snap.buckets,
        perApp = perAppRows
    )
}

fun shiftAnchor(period: Period, anchor: YearMonth, step: Int): YearMonth = when (period) {
    Period.DAILY -> anchor.plusMonths(step.toLong())
    Period.MONTHLY -> anchor.plusYears(step.toLong())
    Period.YEARLY -> anchor
}

fun bucketDetail(period: Period, anchor: YearMonth, bucket: Bucket): String {
    val prefix = when (period) {
        Period.DAILY -> "${bucket.label} ${monthShortId(anchor.monthValue)}"
        Period.MONTHLY -> "${bucket.label} ${anchor.year}"
        Period.YEARLY -> bucket.label
    }
    val inc = formatRupiah(bucket.incomeIdr, AmountSign.PLUS)
    val exp = formatRupiah(bucket.expenseIdr, AmountSign.MINUS)
    return "$prefix: $inc / $exp"
}

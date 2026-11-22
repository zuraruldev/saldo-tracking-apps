package dev.zurarul.saldotracking.stats

import dev.zurarul.saldotracking.domain.Transaction
import dev.zurarul.saldotracking.domain.TxType
import dev.zurarul.saldotracking.domain.monthLongId
import dev.zurarul.saldotracking.domain.monthShortId
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

enum class Period {
    DAILY,
    MONTHLY,
    YEARLY
}

data class FlowTotals(
    val incomeIdr: Long,
    val expenseIdr: Long
) {
    val netIdr: Long
        get() = incomeIdr - expenseIdr

    val incomeShare: Float?
        get() {
            val total = incomeIdr + expenseIdr
            return if (total <= 0L) null else (incomeIdr.toDouble() / total.toDouble()).toFloat()
        }
}

data class Bucket(
    val label: String,
    val incomeIdr: Long,
    val expenseIdr: Long
)

data class AppSummary(
    val sourceApp: String,
    val count: Int,
    val incomeIdr: Long,
    val expenseIdr: Long
)

data class StatsSnapshot(
    val period: Period,
    val title: String,
    val totals: FlowTotals,
    val buckets: List<Bucket>,
    val perApp: List<AppSummary>
)

object StatsCalculator {
    fun monthTotals(
        txs: List<Transaction>,
        month: YearMonth,
        zone: ZoneId
    ): FlowTotals {
        val startMillis = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return computeTotals(txs.filter { it.timestampMillis in startMillis until endMillis })
    }

    fun snapshot(
        txs: List<Transaction>,
        period: Period,
        anchor: YearMonth,
        zone: ZoneId,
        currentYear: Int
    ): StatsSnapshot = when (period) {
        Period.DAILY -> buildDaily(txs, anchor, zone)
        Period.MONTHLY -> buildMonthly(txs, anchor, zone)
        Period.YEARLY -> buildYearly(txs, zone, currentYear)
    }

    private fun buildDaily(
        txs: List<Transaction>,
        anchor: YearMonth,
        zone: ZoneId
    ): StatsSnapshot {
        val startMillis = anchor.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = anchor.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val inRange = txs.filter { it.timestampMillis in startMillis until endMillis }
        val daysInMonth = anchor.lengthOfMonth()
        val incomeByDay = LongArray(daysInMonth + 1)
        val expenseByDay = LongArray(daysInMonth + 1)

        for (tx in inRange) {
            val day = Instant.ofEpochMilli(tx.timestampMillis).atZone(zone).dayOfMonth
            when (tx.type) {
                TxType.INCOME -> incomeByDay[day] += tx.amountIdr
                TxType.EXPENSE -> expenseByDay[day] += tx.amountIdr
            }
        }

        val buckets = (1..daysInMonth).map { d ->
            Bucket(
                label = d.toString(),
                incomeIdr = incomeByDay[d],
                expenseIdr = expenseByDay[d]
            )
        }
        val title = "${monthLongId(anchor.monthValue)} ${anchor.year}"
        return StatsSnapshot(
            period = Period.DAILY,
            title = title,
            totals = computeTotals(inRange),
            buckets = buckets,
            perApp = computePerApp(inRange)
        )
    }

    private fun buildMonthly(
        txs: List<Transaction>,
        anchor: YearMonth,
        zone: ZoneId
    ): StatsSnapshot {
        val year = anchor.year
        val startMillis = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val inRange = txs.filter { it.timestampMillis in startMillis until endMillis }

        val incomeByMonth = LongArray(13)
        val expenseByMonth = LongArray(13)
        for (tx in inRange) {
            val m = Instant.ofEpochMilli(tx.timestampMillis).atZone(zone).monthValue
            when (tx.type) {
                TxType.INCOME -> incomeByMonth[m] += tx.amountIdr
                TxType.EXPENSE -> expenseByMonth[m] += tx.amountIdr
            }
        }

        val buckets = (1..12).map { m ->
            Bucket(
                label = monthShortId(m),
                incomeIdr = incomeByMonth[m],
                expenseIdr = expenseByMonth[m]
            )
        }
        return StatsSnapshot(
            period = Period.MONTHLY,
            title = year.toString(),
            totals = computeTotals(inRange),
            buckets = buckets,
            perApp = computePerApp(inRange)
        )
    }

    private fun buildYearly(
        txs: List<Transaction>,
        zone: ZoneId,
        currentYear: Int
    ): StatsSnapshot {
        val years = txs.map { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).year }
        val minYear = years.minOrNull()?.let { min(it, currentYear) } ?: currentYear
        val maxYear = years.maxOrNull()?.let { max(it, currentYear) } ?: currentYear

        val incomeByYear = mutableMapOf<Int, Long>()
        val expenseByYear = mutableMapOf<Int, Long>()
        for (tx in txs) {
            val y = Instant.ofEpochMilli(tx.timestampMillis).atZone(zone).year
            when (tx.type) {
                TxType.INCOME -> incomeByYear[y] = (incomeByYear[y] ?: 0L) + tx.amountIdr
                TxType.EXPENSE -> expenseByYear[y] = (expenseByYear[y] ?: 0L) + tx.amountIdr
            }
        }

        val buckets = (minYear..maxYear).map { y ->
            Bucket(
                label = y.toString(),
                incomeIdr = incomeByYear[y] ?: 0L,
                expenseIdr = expenseByYear[y] ?: 0L
            )
        }
        return StatsSnapshot(
            period = Period.YEARLY,
            title = "Semua Tahun",
            totals = computeTotals(txs),
            buckets = buckets,
            perApp = computePerApp(txs)
        )
    }

    private fun computeTotals(txs: List<Transaction>): FlowTotals {
        var income = 0L
        var expense = 0L
        for (tx in txs) {
            when (tx.type) {
                TxType.INCOME -> income += tx.amountIdr
                TxType.EXPENSE -> expense += tx.amountIdr
            }
        }
        return FlowTotals(income, expense)
    }

    private fun computePerApp(txs: List<Transaction>): List<AppSummary> =
        txs.groupBy { it.sourceApp }
            .map { (app, list) ->
                var inc = 0L
                var exp = 0L
                for (tx in list) {
                    when (tx.type) {
                        TxType.INCOME -> inc += tx.amountIdr
                        TxType.EXPENSE -> exp += tx.amountIdr
                    }
                }
                AppSummary(
                    sourceApp = app,
                    count = list.size,
                    incomeIdr = inc,
                    expenseIdr = exp
                )
            }
            .sortedWith(compareByDescending<AppSummary> { it.count }.thenBy { it.sourceApp })
}

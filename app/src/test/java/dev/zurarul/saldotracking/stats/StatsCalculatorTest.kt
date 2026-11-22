package dev.zurarul.saldotracking.stats

import dev.zurarul.saldotracking.domain.Transaction
import dev.zurarul.saldotracking.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class StatsCalculatorTest {
    private val zone: ZoneId = ZoneId.of("Asia/Jakarta")

    private fun at(isoLocal: String): Long =
        LocalDateTime.parse(isoLocal).atZone(zone).toInstant().toEpochMilli()

    private fun tx(
        type: TxType,
        amountIdr: Long,
        isoLocal: String,
        sourceApp: String = "DANA"
    ) = Transaction(
        amountIdr = amountIdr,
        type = type,
        sourceApp = sourceApp,
        sourcePackage = null,
        title = "Tx",
        rawNotificationText = null,
        timestampMillis = at(isoLocal),
        isManual = false
    )

    @Test
    fun nearMidnightWib_staysOnLocalDay() {
        val snap = StatsCalculator.snapshot(
            txs = listOf(tx(TxType.INCOME, 500_000, "2026-11-23T23:30:00")),
            period = Period.DAILY,
            anchor = YearMonth.of(2026, 11),
            zone = zone,
            currentYear = 2026
        )
        val nonZero = snap.buckets.single { it.incomeIdr > 0 }
        assertEquals("23", nonZero.label)
        assertEquals(500_000L, nonZero.incomeIdr)
    }

    @Test
    fun monthTotals_respectsLocalMonthBoundary() {
        val txs = listOf(
            tx(TxType.EXPENSE, 10_000, "2026-11-30T23:59:00"),
            tx(TxType.EXPENSE, 20_000, "2026-12-01T00:01:00")
        )
        assertEquals(
            FlowTotals(0L, 10_000L),
            StatsCalculator.monthTotals(txs, YearMonth.of(2026, 11), zone)
        )
    }

    @Test
    fun emptyPeriod_hasNullIncomeShareAndCorrectBucketCount() {
        val nov = StatsCalculator.snapshot(
            txs = emptyList(),
            period = Period.DAILY,
            anchor = YearMonth.of(2026, 11),
            zone = zone,
            currentYear = 2026
        )
        assertNull(nov.totals.incomeShare)
        assertEquals(30, nov.buckets.size)
        assertEquals("November 2026", nov.title)

        val leapFeb = StatsCalculator.snapshot(
            txs = emptyList(),
            period = Period.DAILY,
            anchor = YearMonth.of(2024, 2),
            zone = zone,
            currentYear = 2026
        )
        assertEquals(29, leapFeb.buckets.size)
    }

    @Test
    fun flowTotals_computesRatioAndNetCorrectly() {
        val totals = FlowTotals(3_200_000L, 1_480_000L)
        val share = totals.incomeShare!!
        assertTrue(share in 0.6837f..0.6838f)
        assertEquals(1_720_000L, totals.netIdr)
    }

    @Test
    fun monthlySnapshot_createsTwelveIndonesianMonthBuckets() {
        val txs = listOf(
            tx(TxType.INCOME, 1_000_000, "2026-01-10T10:00:00"),
            tx(TxType.EXPENSE, 250_000, "2026-11-20T12:00:00")
        )
        val snap = StatsCalculator.snapshot(
            txs = txs,
            period = Period.MONTHLY,
            anchor = YearMonth.of(2026, 11),
            zone = zone,
            currentYear = 2026
        )
        assertEquals("2026", snap.title)
        assertEquals(
            listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des"),
            snap.buckets.map { it.label }
        )
        assertEquals(1_000_000L, snap.buckets[0].incomeIdr)
        assertEquals(250_000L, snap.buckets[10].expenseIdr)
    }

    @Test
    fun yearlySnapshot_fillsIntermediateYears() {
        val txs = listOf(
            tx(TxType.INCOME, 100_000, "2024-05-01T09:00:00"),
            tx(TxType.EXPENSE, 50_000, "2026-01-01T09:00:00")
        )
        val snap = StatsCalculator.snapshot(
            txs = txs,
            period = Period.YEARLY,
            anchor = YearMonth.of(2026, 11),
            zone = zone,
            currentYear = 2026
        )
        assertEquals("Semua Tahun", snap.title)
        assertEquals(listOf("2024", "2025", "2026"), snap.buckets.map { it.label })
    }

    @Test
    fun perApp_sortsByCountDescendingThenName() {
        val txs = listOf(
            tx(TxType.EXPENSE, 10_000, "2026-11-01T10:00:00", "DANA"),
            tx(TxType.EXPENSE, 20_000, "2026-11-02T10:00:00", "DANA"),
            tx(TxType.INCOME, 50_000, "2026-11-03T10:00:00", "DANA"),
            tx(TxType.EXPENSE, 15_000, "2026-11-04T10:00:00", "GoPay"),
            tx(TxType.INCOME, 500_000, "2026-11-05T10:00:00", "Livin' Mandiri"),
            tx(TxType.EXPENSE, 101_000, "2026-11-06T10:00:00", "Livin' Mandiri")
        )
        val snap = StatsCalculator.snapshot(
            txs = txs,
            period = Period.DAILY,
            anchor = YearMonth.of(2026, 11),
            zone = zone,
            currentYear = 2026
        )
        assertEquals(
            listOf(
                AppSummary("DANA", 3, 50_000L, 30_000L),
                AppSummary("Livin' Mandiri", 2, 500_000L, 101_000L),
                AppSummary("GoPay", 1, 0L, 15_000L)
            ),
            snap.perApp
        )
    }
}

package dev.zurarul.saldotracking.ui.stats

import dev.zurarul.saldotracking.domain.Transaction
import dev.zurarul.saldotracking.domain.TxType
import dev.zurarul.saldotracking.stats.Bucket
import dev.zurarul.saldotracking.stats.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class StatsUiStateTest {
    private val zone: ZoneId = ZoneId.of("Asia/Jakarta")
    private val today: LocalDate = LocalDate.of(2026, 11, 23)

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
    fun buildStatsUiState_formatsRatioNetNavigationAndPerApp() {
        val txs = listOf(
            tx(TxType.INCOME, 3_200_000, "2026-11-10T10:00:00", "DANA"),
            tx(TxType.EXPENSE, 1_000_000, "2026-11-12T10:00:00", "DANA"),
            tx(TxType.EXPENSE, 480_000, "2026-11-15T10:00:00", "DANA")
        )
        val state = buildStatsUiState(
            txs = txs,
            period = Period.DAILY,
            anchor = YearMonth.of(2026, 11),
            zone = zone,
            today = today
        )
        assertEquals("68% masuk · 32% keluar", state.ratioLabel)
        assertEquals("Net +Rp 1,72jt", state.netText)
        assertTrue(state.netPositive)
        assertFalse(state.canGoNext)
        assertTrue(state.canGoPrev)
        assertEquals("November 2026", state.title)
        assertEquals(
            listOf(AppSummaryRow("DANA", "3 tx", "+Rp 3.200.000", "-Rp 1.480.000")),
            state.perApp
        )
    }

    @Test
    fun buildStatsUiState_handlesEmptyAndNegativeNetAndNavigation() {
        val empty = buildStatsUiState(
            txs = emptyList(),
            period = Period.DAILY,
            anchor = YearMonth.of(2026, 11),
            zone = zone,
            today = today
        )
        assertEquals("Belum ada data", empty.ratioLabel)
        assertNull(empty.incomeShare)
        assertEquals("Net Rp 0", empty.netText)

        val negTxs = listOf(
            tx(TxType.INCOME, 200_000, "2026-11-01T10:00:00"),
            tx(TxType.EXPENSE, 500_000, "2026-11-02T10:00:00")
        )
        val monthlyNeg = buildStatsUiState(
            txs = negTxs,
            period = Period.MONTHLY,
            anchor = YearMonth.of(2026, 11),
            zone = zone,
            today = today
        )
        assertFalse(monthlyNeg.netPositive)
        assertEquals("Net -Rp 300rb", monthlyNeg.netText)

        val pastMonth = buildStatsUiState(
            txs = negTxs,
            period = Period.DAILY,
            anchor = YearMonth.of(2026, 10),
            zone = zone,
            today = today
        )
        assertTrue(pastMonth.canGoNext)

        val yearly = buildStatsUiState(
            txs = negTxs,
            period = Period.YEARLY,
            anchor = YearMonth.of(2026, 11),
            zone = zone,
            today = today
        )
        assertFalse(yearly.canGoPrev)
        assertFalse(yearly.canGoNext)
    }

    @Test
    fun shiftAnchor_and_bucketDetail_formatDailyMonthlyYearly() {
        assertEquals(YearMonth.of(2025, 12), shiftAnchor(Period.DAILY, YearMonth.of(2026, 1), -1))
        assertEquals(YearMonth.of(2027, 11), shiftAnchor(Period.MONTHLY, YearMonth.of(2026, 11), 1))
        assertEquals(YearMonth.of(2026, 11), shiftAnchor(Period.YEARLY, YearMonth.of(2026, 11), 1))

        assertEquals(
            "23 Nov: +Rp 500.000 / -Rp 28.000",
            bucketDetail(Period.DAILY, YearMonth.of(2026, 11), Bucket("23", 500_000L, 28_000L))
        )
        assertEquals(
            "Nov 2026: +Rp 500.000 / -Rp 28.000",
            bucketDetail(Period.MONTHLY, YearMonth.of(2026, 11), Bucket("Nov", 500_000L, 28_000L))
        )
        assertEquals(
            "2026: +Rp 500.000 / -Rp 28.000",
            bucketDetail(Period.YEARLY, YearMonth.of(2026, 11), Bucket("2026", 500_000L, 28_000L))
        )
    }
}

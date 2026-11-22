package dev.zurarul.saldotracking.ui.home

import dev.zurarul.saldotracking.data.AppSettings
import dev.zurarul.saldotracking.domain.Transaction
import dev.zurarul.saldotracking.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class HomeUiStateTest {
    private val zone: ZoneId = ZoneId.of("Asia/Jakarta")
    private val now: Instant = LocalDateTime.parse("2026-11-23T10:00:00").atZone(zone).toInstant()

    private fun at(isoLocal: String): Long =
        LocalDateTime.parse(isoLocal).atZone(zone).toInstant().toEpochMilli()

    private fun tx(
        id: Long,
        type: TxType,
        amountIdr: Long,
        isoLocal: String,
        sourceApp: String = "DANA",
        title: String = "Tx $id"
    ) = Transaction(
        id = id,
        amountIdr = amountIdr,
        type = type,
        sourceApp = sourceApp,
        sourcePackage = null,
        title = title,
        rawNotificationText = null,
        timestampMillis = at(isoLocal),
        isManual = false
    )

    @Test
    fun buildHomeUiState_computesTotalSaldoCurrentMonthInOutAndSortedRows() {
        val txs = listOf(
            tx(1, TxType.INCOME, 500_000, "2026-11-23T08:02:00", "Livin' Mandiri", "Transfer Masuk"),
            tx(2, TxType.EXPENSE, 28_000, "2026-11-23T09:14:00", "DANA", "QRIS Kopi Kenangan"),
            tx(3, TxType.EXPENSE, 200_000, "2026-10-15T12:00:00", "GoPay", "GoFood")
        )
        val state = buildHomeUiState(
            settings = AppSettings(baseBalanceIdr = 1_000_000L),
            txs = txs,
            autoTrackOn = true,
            now = now,
            zone = zone
        )
        assertEquals("Rp 1.272.000", state.saldoText)
        assertEquals("+Rp 500.000", state.monthIncomeText)
        assertEquals("-Rp 28.000", state.monthExpenseText)
        assertTrue(state.autoTrackOn)

        assertEquals(3, state.rows.size)
        assertEquals(2L, state.rows[0].id)
        assertEquals("-Rp 28.000", state.rows[0].amountText)
        assertFalse(state.rows[0].isIncome)
        assertEquals(1L, state.rows[1].id)
        assertEquals("+Rp 500.000", state.rows[1].amountText)
        assertTrue(state.rows[1].isIncome)
    }

    @Test
    fun timeLabel_formatsTodayYesterdaySameYearAndOtherYearInIndonesian() {
        assertEquals("Hari ini · 09:14", timeLabel(at("2026-11-23T09:14:00"), now, zone))
        assertEquals("Kemarin · 19:20", timeLabel(at("2026-11-22T19:20:00"), now, zone))
        assertEquals("21 Nov · 08:00", timeLabel(at("2026-11-21T08:00:00"), now, zone))
        assertEquals("21 Nov 2025 · 08:00", timeLabel(at("2025-11-21T08:00:00"), now, zone))
    }

    @Test
    fun isAutoTrackActive_requiresBothConnectedAndAccessGranted() {
        assertTrue(isAutoTrackActive(connected = true, accessGranted = true))
        assertFalse(isAutoTrackActive(connected = false, accessGranted = true))
        assertFalse(isAutoTrackActive(connected = true, accessGranted = false))
        assertFalse(isAutoTrackActive(connected = false, accessGranted = false))
    }
}

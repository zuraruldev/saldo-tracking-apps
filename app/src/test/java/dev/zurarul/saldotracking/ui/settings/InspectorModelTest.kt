package dev.zurarul.saldotracking.ui.settings

import dev.zurarul.saldotracking.domain.LogStatus
import dev.zurarul.saldotracking.domain.NotificationLog
import dev.zurarul.saldotracking.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectorModelTest {
    private fun log(
        status: LogStatus,
        parsedAmountIdr: Long? = null,
        parsedType: TxType? = null,
        title: String = "Notif Title",
        text: String = "Pembayaran Rp 10.000 di Warung Makan Sederhana"
    ) = NotificationLog(
        id = 1L,
        packageName = "id.dana",
        sourceApp = "DANA",
        title = title,
        text = text,
        postedAtMillis = 1_700_000_000_000L,
        status = status,
        parsedAmountIdr = parsedAmountIdr,
        parsedType = parsedType
    )

    @Test
    fun badgeLabel_mapsAllLogStatuses() {
        assertEquals("Captured", badgeLabel(LogStatus.CAPTURED))
        assertEquals("Promo", badgeLabel(LogStatus.IGNORED_PROMO))
        assertEquals("Gagal/Pending", badgeLabel(LogStatus.IGNORED_FAILED))
        assertEquals("Non-Saldo", badgeLabel(LogStatus.IGNORED_NON_SALDO))
        assertEquals("Duplicate", badgeLabel(LogStatus.IGNORED_DUPLICATE))
        assertEquals("Unparsed", badgeLabel(LogStatus.IGNORED_UNPARSED))
    }

    @Test
    fun canPromote_isFalseOnlyForCaptured() {
        assertFalse(log(LogStatus.CAPTURED).canPromote())
        assertTrue(log(LogStatus.IGNORED_UNPARSED).canPromote())
        assertTrue(log(LogStatus.IGNORED_NON_SALDO).canPromote())
        assertTrue(log(LogStatus.IGNORED_PROMO).canPromote())
    }

    @Test
    fun canPromote_returnsFalseOnceLogIdIsInPromotedSet() {
        val item = log(LogStatus.IGNORED_UNPARSED)
        assertTrue(item.canPromote(emptySet()))
        assertFalse(item.canPromote(setOf(item.id)))
    }

    @Test
    fun toDraft_prefillsAmountTypeTitleAndTimestampFromLog() {
        val unparsedWithAmount = log(
            status = LogStatus.IGNORED_UNPARSED,
            parsedAmountIdr = 10_000L,
            parsedType = null,
            title = ""
        )
        val draft = unparsedWithAmount.toDraft()
        assertEquals("10000", draft.amountInput)
        assertEquals(TxType.EXPENSE, draft.type)
        assertEquals(unparsedWithAmount.text.take(60), draft.title)
        assertEquals(unparsedWithAmount.postedAtMillis, draft.timestampMillis)
        assertEquals("DANA", draft.sourceApp)

        val unparsedWithoutAmount = log(
            status = LogStatus.IGNORED_UNPARSED,
            parsedAmountIdr = null
        )
        assertEquals("", unparsedWithoutAmount.toDraft().amountInput)
    }
}

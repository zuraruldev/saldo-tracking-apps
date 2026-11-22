package dev.zurarul.saldotracking.ui.home

import dev.zurarul.saldotracking.domain.Transaction
import dev.zurarul.saldotracking.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionDraftTest {
    private val t = 1_700_000_000_000L

    @Test
    fun parseAmountInput_handlesFormattedDigitsAndRejectsInvalidInputs() {
        assertEquals(25_000L, parseAmountInput("25.000"))
        assertEquals(1_250_000L, parseAmountInput("1,250,000"))
        assertNull(parseAmountInput(""))
        assertNull(parseAmountInput("0"))
        assertNull(parseAmountInput("12a"))
        assertNull(parseAmountInput("1234567890123"))
    }

    @Test
    fun toTransaction_validatesAndPreservesOriginalMetadataOnEdit() {
        assertNull(
            TransactionDraft(amountInput = "50000", title = "  ", timestampMillis = t)
                .toTransaction(null)
        )

        val created = TransactionDraft(
            amountInput = "50.000",
            type = TxType.INCOME,
            title = "Gaji",
            timestampMillis = t
        ).toTransaction(null)!!
        assertEquals(50_000L, created.amountIdr)
        assertTrue(created.isManual)
        assertEquals("Manual", created.sourceApp)

        val captured = Transaction(
            id = 42L,
            amountIdr = 25_000L,
            type = TxType.EXPENSE,
            sourceApp = "DANA",
            sourcePackage = "id.dana",
            title = "Pembayaran",
            rawNotificationText = "Pembayaran\nRp25.000",
            timestampMillis = t,
            isManual = false
        )
        val edited = captured.toDraft().copy(amountInput = "30000").toTransaction(captured)!!
        assertEquals(42L, edited.id)
        assertEquals(30_000L, edited.amountIdr)
        assertFalse(edited.isManual)
        assertEquals("id.dana", edited.sourcePackage)
        assertEquals("Pembayaran\nRp25.000", edited.rawNotificationText)
    }
}

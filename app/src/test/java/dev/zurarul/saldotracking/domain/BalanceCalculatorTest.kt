package dev.zurarul.saldotracking.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceCalculatorTest {
    private fun tx(
        type: TxType,
        amountIdr: Long,
        sourceApp: String = "DANA"
    ) = Transaction(
        amountIdr = amountIdr,
        type = type,
        sourceApp = sourceApp,
        sourcePackage = null,
        title = "Test",
        rawNotificationText = null,
        timestampMillis = 1_000L,
        isManual = false
    )

    @Test
    fun currentSaldo_addsIncomeAndSubtractsExpenseFromBase() {
        val txs = listOf(
            tx(TxType.INCOME, 500_000),
            tx(TxType.EXPENSE, 28_000),
            tx(TxType.EXPENSE, 101_000)
        )
        assertEquals(1_371_000L, BalanceCalculator.currentSaldo(1_000_000L, txs))
        assertEquals(750_000L, BalanceCalculator.currentSaldo(750_000L, emptyList()))
    }

    @Test
    fun selfTransfer_netsOutToAdminFeeOnly() {
        val txs = listOf(
            tx(TxType.EXPENSE, 101_000, "Livin' Mandiri"),
            tx(TxType.INCOME, 100_000, "DANA")
        )
        assertEquals(-1_000L, BalanceCalculator.netFlow(txs))
    }

    @Test
    fun rebaseFor_computesBaseOffsetSoCurrentSaldoEqualsTarget() {
        val txs = listOf(
            tx(TxType.EXPENSE, 101_000, "Livin' Mandiri"),
            tx(TxType.INCOME, 100_000, "DANA")
        )
        val base = BalanceCalculator.rebaseFor(2_000_000L, txs)
        assertEquals(2_001_000L, base)
        assertEquals(2_000_000L, BalanceCalculator.currentSaldo(base, txs))
    }
}

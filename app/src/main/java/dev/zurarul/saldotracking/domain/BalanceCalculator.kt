package dev.zurarul.saldotracking.domain

object BalanceCalculator {
    fun netFlow(txs: List<Transaction>): Long =
        txs.sumOf { tx ->
            when (tx.type) {
                TxType.INCOME -> tx.amountIdr
                TxType.EXPENSE -> -tx.amountIdr
            }
        }

    fun currentSaldo(baseBalanceIdr: Long, txs: List<Transaction>): Long =
        baseBalanceIdr + netFlow(txs)

    fun rebaseFor(actualSaldoIdr: Long, txs: List<Transaction>): Long =
        actualSaldoIdr - netFlow(txs)
}

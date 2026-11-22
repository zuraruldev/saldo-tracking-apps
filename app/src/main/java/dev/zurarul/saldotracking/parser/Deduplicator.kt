package dev.zurarul.saldotracking.parser

import dev.zurarul.saldotracking.domain.TxType
import dev.zurarul.saldotracking.domain.Wallet

class Deduplicator(private val windowMillis: Long = 15_000L) {
    private data class Key(
        val wallet: Wallet,
        val amountIdr: Long,
        val type: TxType
    )

    private val seenAt = mutableMapOf<Key, Long>()

    @Synchronized
    fun isDuplicate(
        wallet: Wallet,
        amountIdr: Long,
        type: TxType,
        atMillis: Long
    ): Boolean {
        val key = Key(wallet, amountIdr, type)
        val last = seenAt[key]
        if (last != null && atMillis - last in 0..windowMillis) {
            return true
        }
        seenAt[key] = atMillis
        if (seenAt.size > 256) {
            val cutoff = atMillis - windowMillis
            val iter = seenAt.entries.iterator()
            while (iter.hasNext()) {
                if (iter.next().value < cutoff) iter.remove()
            }
        }
        return false
    }
}

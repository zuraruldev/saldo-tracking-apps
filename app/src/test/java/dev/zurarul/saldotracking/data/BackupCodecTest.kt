package dev.zurarul.saldotracking.data

import dev.zurarul.saldotracking.domain.Transaction
import dev.zurarul.saldotracking.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    private fun tx(
        id: Long,
        type: TxType,
        amountIdr: Long,
        sourceApp: String,
        raw: String? = null
    ) = Transaction(
        id = id,
        amountIdr = amountIdr,
        type = type,
        sourceApp = sourceApp,
        sourcePackage = "id.dana",
        title = "Tx $id",
        rawNotificationText = raw,
        timestampMillis = 1_700_000_000_000L + id,
        isManual = false
    )

    @Test
    fun roundTrip_preservesBaseBalanceAndTransactionsWithZeroedIds() {
        val txs = listOf(
            tx(id = 7, type = TxType.INCOME, amountIdr = 500_000, sourceApp = "Livin' Mandiri"),
            tx(id = 9, type = TxType.EXPENSE, amountIdr = 28_000, sourceApp = "DANA", raw = "QRIS\nRp28.000")
        )
        val encoded = BackupCodec.encode(BackupCodec.fromTransactions(1_000_000L, txs))
        val decoded = BackupCodec.decode(encoded)
        assertEquals(1_000_000L, decoded.baseBalanceIdr)
        assertEquals(txs.map { it.copy(id = 0) }, BackupCodec.toTransactions(decoded))
    }

    @Test
    fun decode_rejectsMalformedUnsupportedVersionZeroAmountOrInvalidType() {
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode("not json")
        }
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode("{}")
        }
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode("""{"version":2,"baseBalanceIdr":0,"transactions":[]}""")
        }
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode(
                """{"version":1,"baseBalanceIdr":0,"transactions":[{"amountIdr":0,"type":"EXPENSE","sourceApp":"DANA","title":"x","timestampMillis":1,"isManual":true}]}"""
            )
        }
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode(
                """{"version":1,"baseBalanceIdr":0,"transactions":[{"amountIdr":1000,"type":"FOO","sourceApp":"DANA","title":"x","timestampMillis":1,"isManual":true}]}"""
            )
        }
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode(
                """{"version":1,"baseBalanceIdr":0,"transactions":[{"amountIdr":1000,"type":"EXPENSE","sourceApp":"DANA","title":"x","timestampMillis":999999999999999,"isManual":true}]}"""
            )
        }
    }

    @Test
    fun decode_ignoresUnknownKeysForForwardCompatibility() {
        val decoded = BackupCodec.decode(
            """{"version":1,"baseBalanceIdr":250000,"extra":1,"transactions":[{"amountIdr":15000,"type":"EXPENSE","sourceApp":"GoPay","title":"Parkir","timestampMillis":1700000000000,"isManual":true,"futureField":"ok"}]}"""
        )
        assertEquals(250_000L, decoded.baseBalanceIdr)
        assertEquals(1, decoded.transactions.size)
        assertEquals(15_000L, BackupCodec.toTransactions(decoded).single().amountIdr)
    }
}

package dev.zurarul.saldotracking.parser

import dev.zurarul.saldotracking.domain.LogStatus
import dev.zurarul.saldotracking.domain.TxType
import dev.zurarul.saldotracking.domain.Wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationProcessorTest {
    private val enabled = Wallet.entries.toSet()
    private val t0 = 1_700_000_000_000L

    private fun raw(
        pkg: String,
        title: String,
        text: String,
        at: Long = t0,
        isOngoing: Boolean = false,
        isGroupSummary: Boolean = false
    ) = RawNotification(
        packageName = pkg,
        title = title,
        text = text,
        postedAtMillis = at,
        isOngoing = isOngoing,
        isGroupSummary = isGroupSummary
    )

    @Test
    fun process_ignoresUntrackedDisabledOngoingOrBlankNotifications() {
        val processor = NotificationProcessor()
        assertNull(processor.process(raw("com.whatsapp", "Chat", "Rp 50.000"), enabled))
        assertNull(processor.process(raw("id.dana", "Pembayaran", "Bayar Rp 25.000"), enabled - Wallet.DANA))
        assertNull(processor.process(raw("id.dana", "Pembayaran", "Bayar Rp 25.000", isOngoing = true), enabled))
        assertNull(processor.process(raw("id.dana", "Pembayaran", "Bayar Rp 25.000", isGroupSummary = true), enabled))
        assertNull(processor.process(raw("id.dana", "  ", " "), enabled))
    }

    @Test
    fun process_capturesValidDanaExpense() {
        val processor = NotificationProcessor()
        val outcome = processor.process(
            raw("id.dana", "Pembayaran Berhasil", "Pembayaran Rp25.000 ke Kopi Kenangan berhasil"),
            enabled
        )!!
        assertEquals(LogStatus.CAPTURED, outcome.log.status)
        val tx = outcome.transaction!!
        assertEquals(TxType.EXPENSE, tx.type)
        assertEquals(25_000L, tx.amountIdr)
        assertEquals("DANA", tx.sourceApp)
    }

    @Test
    fun process_logsPromoFailedNonSaldoAndUnparsedReasons() {
        val processor = NotificationProcessor()

        val promo = processor.process(
            raw("id.dana", "Promo DANA", "Cashback hingga Rp50.000 buat kamu"),
            enabled
        )!!
        assertEquals(LogStatus.IGNORED_PROMO, promo.log.status)
        assertNull(promo.transaction)

        val failed = processor.process(
            raw("id.dana", "Transaksi gagal", "Pembayaran Rp 50.000 tidak dapat diproses"),
            enabled
        )!!
        assertEquals(LogStatus.IGNORED_FAILED, failed.log.status)
        assertNull(failed.transaction)

        val gojekOrderNoWallet = processor.process(
            raw("com.gojek.app", "GoFood", "Pembayaran Rp 42.500 untuk pesananmu berhasil"),
            enabled
        )!!
        assertEquals(LogStatus.IGNORED_NON_SALDO, gojekOrderNoWallet.log.status)
        assertNull(gojekOrderNoWallet.transaction)

        val gojekWithGopay = processor.process(
            raw("com.gojek.app", "GoPay", "Pembayaran GoPay Rp 42.500 berhasil"),
            enabled
        )!!
        assertEquals(LogStatus.CAPTURED, gojekWithGopay.log.status)
        assertEquals("GoPay", gojekWithGopay.transaction!!.sourceApp)

        val shopeeOrderNoWallet = processor.process(
            raw("com.shopee.id", "Pesanan Dibayar", "Pembayaran Rp 89.000 untuk pesanan 2311ABC berhasil"),
            enabled
        )!!
        assertEquals(LogStatus.IGNORED_NON_SALDO, shopeeOrderNoWallet.log.status)

        val noAmount = processor.process(
            raw("id.dana", "Info", "Info akun kamu diperbarui"),
            enabled
        )!!
        assertEquals(LogStatus.IGNORED_UNPARSED, noAmount.log.status)
        assertNull(noAmount.log.parsedAmountIdr)

        val noDirection = processor.process(
            raw("id.dana", "Notifikasi", "Notifikasi Rp 10.000"),
            enabled
        )!!
        assertEquals(LogStatus.IGNORED_UNPARSED, noDirection.log.status)
        assertEquals(10_000L, noDirection.log.parsedAmountIdr)
    }

    @Test
    fun process_deduplicatesWithin15sAndAcrossPairedSuperAppPackages() {
        val processor = NotificationProcessor()

        val first = processor.process(
            raw("id.dana", "Pembayaran", "Pembayaran Rp25.000 berhasil", at = t0),
            enabled
        )!!
        assertEquals(LogStatus.CAPTURED, first.log.status)

        val dup = processor.process(
            raw("id.dana", "Pembayaran", "Pembayaran Rp25.000 berhasil", at = t0 + 5_000),
            enabled
        )!!
        assertEquals(LogStatus.IGNORED_DUPLICATE, dup.log.status)
        assertNull(dup.transaction)

        val afterWindow = processor.process(
            raw("id.dana", "Pembayaran", "Pembayaran Rp25.000 berhasil", at = t0 + 20_000),
            enabled
        )!!
        assertEquals(LogStatus.CAPTURED, afterWindow.log.status)

        // Paired Gojek + GoPay standalone within 3 seconds
        val gojek = processor.process(
            raw("com.gojek.app", "GoPay", "Pembayaran GoPay Rp 20.000 berhasil", at = t0),
            enabled
        )!!
        assertEquals(LogStatus.CAPTURED, gojek.log.status)

        val gopayStandalone = processor.process(
            raw("com.gojek.gopay", "GoPay", "Kamu udah bayar Rp20.000", at = t0 + 3_000),
            enabled
        )!!
        assertEquals(LogStatus.IGNORED_DUPLICATE, gopayStandalone.log.status)
    }

    @Test
    fun process_capturesBothSidesOfLivinToDanaSelfTransfer() {
        val processor = NotificationProcessor()
        val livinOut = processor.process(
            raw("id.bmri.livin", "Transaksi Berhasil", "Top Up DANA Rp 101.000 berhasil", at = t0),
            enabled
        )!!
        val danaIn = processor.process(
            raw("id.dana", "Isi Saldo Berhasil", "Saldo DANA kamu telah bertambah Rp100.000", at = t0),
            enabled
        )!!
        assertEquals(LogStatus.CAPTURED, livinOut.log.status)
        assertEquals(TxType.EXPENSE, livinOut.transaction!!.type)
        assertEquals(101_000L, livinOut.transaction!!.amountIdr)

        assertEquals(LogStatus.CAPTURED, danaIn.log.status)
        assertEquals(TxType.INCOME, danaIn.transaction!!.type)
        assertEquals(100_000L, danaIn.transaction!!.amountIdr)
        assertNotNull(danaIn.transaction)
    }
}

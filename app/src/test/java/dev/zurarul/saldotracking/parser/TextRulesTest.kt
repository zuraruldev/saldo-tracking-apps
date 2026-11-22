package dev.zurarul.saldotracking.parser

import dev.zurarul.saldotracking.domain.TxType
import dev.zurarul.saldotracking.domain.Wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextRulesTest {
    private fun n(title: String, text: String): String = TextRules.normalize(title, text)

    @Test
    fun isPromo_flagsMarketingAndOtpWhileAllowingCreditedCashback() {
        assertTrue(TextRules.isPromo(n("DANA", "Cashback hingga Rp50.000! Bayar pakai DANA sekarang")))
        assertTrue(TextRules.isPromo(n("", "Kode OTP kamu 123456. Jangan berikan ke siapa pun")))
        assertFalse(TextRules.isPromo(n("", "Cashback Rp2.000 telah masuk ke saldo DANA kamu")))
    }

    @Test
    fun isFailedOrPending_flagsIncompleteTransactions() {
        assertTrue(TextRules.isFailedOrPending(n("Transaksi gagal", "Pembayaran Rp 50.000 tidak dapat diproses")))
        assertTrue(TextRules.isFailedOrPending(n("", "Menunggu pembayaran Rp 75.000")))
        assertFalse(TextRules.isFailedOrPending(n("Pembayaran Berhasil", "Pembayaran Rp 50.000 berhasil")))
    }

    @Test
    fun isNonSaldo_flagsPayLaterCreditCardsCoinsAndPocketsWithoutSubstringFalsePositive() {
        assertTrue(TextRules.isNonSaldo(n("GoPayLater", "Pembayaran Rp 120.000 berhasil")))
        assertTrue(TextRules.isNonSaldo(n("", "Tagihan Kartu Kredit Mandiri Rp 1.500.000")))
        assertTrue(TextRules.isNonSaldo(n("", "Rp 50.000 berhasil dipindahkan ke DANA Goals")))
        assertFalse(TextRules.isNonSaldo(n("", "Pemasukan Rp 50.000")))
    }

    @Test
    fun classify_distinguishesIncomeExpenseTopUpsAndTerimaKasih() {
        assertEquals(
            TxType.EXPENSE,
            TextRules.classify(n("Pembayaran Berhasil", "Pembayaran Rp25.000 ke Kopi Kenangan berhasil"), Wallet.DANA)
        )
        assertEquals(
            TxType.INCOME,
            TextRules.classify(n("Dana Masuk", "Kamu menerima Rp50.000 dari JOHN"), Wallet.DANA)
        )
        assertEquals(
            TxType.INCOME,
            TextRules.classify(n("Isi Saldo Berhasil", "Saldo DANA kamu telah bertambah Rp100.000"), Wallet.DANA)
        )
        assertEquals(
            TxType.EXPENSE,
            TextRules.classify(n("Top Up GoPay berhasil", "Rp 50.000"), Wallet.DANA)
        )
        assertEquals(
            TxType.EXPENSE,
            TextRules.classify(n("Transaksi Berhasil", "Top Up DANA Rp 101.000 berhasil"), Wallet.LIVIN)
        )
        assertEquals(
            TxType.INCOME,
            TextRules.classify(n("Transfer masuk", "Rp 500.000 dari BUDI"), Wallet.LIVIN)
        )
        assertEquals(
            TxType.EXPENSE,
            TextRules.classify(n("GoPay", "Terima kasih! Kamu udah bayar Rp20.000 ke Warung Bu Sri"), Wallet.GOPAY)
        )
        assertEquals(
            TxType.INCOME,
            TextRules.classify(n("GoPay", "Top up GoPay Rp100.000 berhasil"), Wallet.GOPAY)
        )
        assertNull(TextRules.classify(n("Info", "Rp 10.000"), Wallet.DANA))
    }

    @Test
    fun classify_handlesTopUpConnectorsPurchaseTopUpsAndPaymentDiterimaAsExpense() {
        assertEquals(
            TxType.EXPENSE,
            TextRules.classify(n("DANA", "Top Up ke GoPay Rp 50.000 berhasil"), Wallet.DANA)
        )
        assertEquals(
            TxType.EXPENSE,
            TextRules.classify(n("GoPay", "Isi ulang saldo kartu Flazz Rp 100.000 berhasil"), Wallet.GOPAY)
        )
        assertEquals(
            TxType.EXPENSE,
            TextRules.classify(n("DANA", "Pembayaran Top Up Pulsa Rp 50.000 berhasil"), Wallet.DANA)
        )
        assertEquals(
            TxType.EXPENSE,
            TextRules.classify(n("DANA", "Top up paket data Rp 50.000 berhasil"), Wallet.DANA)
        )
        assertEquals(
            TxType.EXPENSE,
            TextRules.classify(n("GoPay", "Pembayaran Rp 25.000 ke Merchant telah diterima"), Wallet.GOPAY)
        )
        assertEquals(
            TxType.EXPENSE,
            TextRules.classify(n("Livin", "Transfer ke Budi Rp 100.000 diterima"), Wallet.LIVIN)
        )
    }
}

package dev.zurarul.saldotracking.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountExtractorTest {
    @Test
    fun extract_parsesIndonesianAndIdrFormats() {
        assertEquals(25_000L, AmountExtractor.extract("Pembayaran Rp25.000 ke Kopi Kenangan berhasil"))
        assertEquals(1_250_000L, AmountExtractor.extract("Transfer Rp 1.250.000,00 berhasil"))
        assertEquals(50_000L, AmountExtractor.extract("Payment IDR 50,000.00 success"))
        assertEquals(50_000L, AmountExtractor.extract("IDR 50.000 diterima"))
        assertEquals(500L, AmountExtractor.extract("Bayar parkir Rp 500"))
        assertEquals(50_000L, AmountExtractor.extract("Rp 50.000,- telah dibayar"))
        assertEquals(100_000L, AmountExtractor.extract("Saldo DANA kamu bertambah Rp100.000"))
    }

    @Test
    fun extract_prefersTotalAndIgnoresBalanceAmounts() {
        assertEquals(
            102_500L,
            AmountExtractor.extract("Transfer Rp 100.000 berhasil. Biaya admin Rp 2.500. Total Rp 102.500")
        )
        assertEquals(
            25_000L,
            AmountExtractor.extract("Sisa saldo Rp 1.250.000. Pembayaran Rp 25.000 berhasil")
        )
        assertEquals(
            25_000L,
            AmountExtractor.extract("Pembayaran Rp 25.000 berhasil. Saldo kamu sekarang Rp 1.225.000")
        )
    }

    @Test
    fun extract_returnsNullWhenMissingOrZero() {
        assertNull(AmountExtractor.extract("Pembayaran berhasil"))
        assertNull(AmountExtractor.extract("Rp 0"))
        assertNull(AmountExtractor.extract("Rp 0,00"))
    }
}

package dev.zurarul.saldotracking.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class IdFormatTest {
    @Test
    fun formatRupiah_formatsStandardAndSignedValues() {
        assertEquals("Rp 2.450.000", formatRupiah(2_450_000))
        assertEquals("+Rp 500.000", formatRupiah(500_000, AmountSign.PLUS))
        assertEquals("-Rp 28.000", formatRupiah(28_000, AmountSign.MINUS))
        assertEquals("-Rp 150.000", formatRupiah(-150_000))
        assertEquals("Rp 0", formatRupiah(0))
    }

    @Test
    fun formatRupiahCompact_formatsThresholdsWithIndonesianSuffixes() {
        assertEquals("Rp 999", formatRupiahCompact(999))
        assertEquals("Rp 1,5rb", formatRupiahCompact(1_500))
        assertEquals("Rp 850rb", formatRupiahCompact(850_000))
        assertEquals("Rp 1jt", formatRupiahCompact(1_000_000))
        assertEquals("Rp 1,72jt", formatRupiahCompact(1_720_000))
        assertEquals("Rp 14,9jt", formatRupiahCompact(14_900_000))
        assertEquals("Rp 2,5M", formatRupiahCompact(2_500_000_000))
    }

    @Test
    fun monthNames_returnIndonesianShortAndLongNames() {
        assertEquals("Agu", monthShortId(8))
        assertEquals("Desember", monthLongId(12))
    }
}

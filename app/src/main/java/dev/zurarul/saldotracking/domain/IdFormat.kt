package dev.zurarul.saldotracking.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

enum class AmountSign {
    NONE,
    PLUS,
    MINUS
}

fun formatRupiah(amountIdr: Long, sign: AmountSign = AmountSign.NONE): String {
    val absVal = abs(amountIdr)
    val grouped = formatDotGrouped(absVal)
    val prefix = when (sign) {
        AmountSign.PLUS -> "+Rp "
        AmountSign.MINUS -> "-Rp "
        AmountSign.NONE -> if (amountIdr < 0) "-Rp " else "Rp "
    }
    return "$prefix$grouped"
}

fun formatRupiahCompact(amountIdr: Long): String {
    val absVal = abs(amountIdr)
    val prefix = if (amountIdr < 0) "-Rp " else "Rp "
    return when {
        absVal >= 1_000_000_000L -> "$prefix${formatScaled(absVal, 1_000_000_000L)}M"
        absVal >= 1_000_000L -> "$prefix${formatScaled(absVal, 1_000_000L)}jt"
        absVal >= 1_000L -> "$prefix${formatScaled(absVal, 1_000L)}rb"
        else -> "$prefix$absVal"
    }
}

private fun formatDotGrouped(absVal: Long): String {
    val digits = absVal.toString()
    val sb = StringBuilder()
    for (i in digits.indices) {
        if (i > 0 && (digits.length - i) % 3 == 0) {
            sb.append('.')
        }
        sb.append(digits[i])
    }
    return sb.toString()
}

private fun formatScaled(value: Long, divisor: Long): String {
    val bd = BigDecimal.valueOf(value)
        .divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
    return bd.toPlainString().replace('.', ',')
}

private val MONTHS_SHORT_ID = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
    "Jul", "Agu", "Sep", "Okt", "Nov", "Des"
)

private val MONTHS_LONG_ID = arrayOf(
    "Januari", "Februari", "Maret", "April", "Mei", "Juni",
    "Juli", "Agustus", "September", "Oktober", "November", "Desember"
)

fun monthShortId(month: Int): String = MONTHS_SHORT_ID[(month - 1).coerceIn(0, 11)]

fun monthLongId(month: Int): String = MONTHS_LONG_ID[(month - 1).coerceIn(0, 11)]

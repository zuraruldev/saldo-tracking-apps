package dev.zurarul.saldotracking.parser

import dev.zurarul.saldotracking.domain.TxType
import dev.zurarul.saldotracking.domain.Wallet
import dev.zurarul.saldotracking.domain.WalletKind

object TextRules {
    private val WHITESPACE_REGEX = Regex("""\s+""")
    private val OTP_WORD_REGEX = Regex("""\botp\b""")

    private val PROMO_STRONG_SUBSTRINGS = listOf(
        "hingga", "s.d.", "s/d", "kode otp", "kode verifikasi",
        "klaim", "dapatkan", "voucher", "promo", "diskon",
        "flash sale", "kupon", "undian", "menangkan",
        "berlaku sampai", "buruan"
    )

    private val PROMO_WEAK_SUBSTRINGS = listOf(
        "cashback", "gratis", "hadiah"
    )

    private val COMPLETION_SUBSTRINGS = listOf(
        "berhasil", "sukses", "telah masuk", "diterima", "bertambah"
    )

    private val FAILED_OR_PENDING_SUBSTRINGS = listOf(
        "gagal", "dibatalkan", "failed", "pending", "menunggu",
        "sedang diproses", "sedang dalam proses", "kedaluwarsa",
        "expired", "ditolak"
    )

    private val NON_SALDO_SUBSTRINGS = listOf(
        "paylater", "pay later", "cicil", "kartu kredit", "credit card"
    )

    private val NON_SALDO_WORDS_REGEX = Regex(
        """\b(coins|koin|poin|points|goals|tabungan|deposito|reksa dana|reksadana|investasi|emas)\b"""
    )

    private val TOP_UP_REGEX = Regex(
        """\b(top ?up|isi saldo|isi ulang(?: saldo)?)\b(?:\s+(?:ke|saldo|kartu|dompet))*\s*([a-z-]+)?"""
    )

    private val EXTERNAL_TOPUP_TARGETS = setOf(
        "gopay", "ovo", "shopeepay", "dana", "linkaja", "flazz",
        "e-money", "emoney", "brizzi", "tapcash", "jakcard", "isaku", "sakuku"
    )

    private val PURCHASE_TOPUP_TARGETS = setOf(
        "pulsa", "paket", "data", "game", "pln", "token", "listrik", "tagihan"
    )

    private val EXPLICIT_PAYMENT_MARKERS = listOf(
        "pembayaran", "pembelian", "bayar", "transfer ke", "kirim", "terkirim",
        "tarik tunai", "penarikan", "berkurang", "dipotong", "terdebet", "qris",
        "paid", "payment", "sent"
    )

    private val STRONG_INCOME_SUBSTRINGS = listOf(
        "menerima", "dana masuk", "uang masuk",
        "transfer masuk", "saldo masuk", "masuk ke saldo",
        "bertambah", "refund", "pengembalian dana",
        "kamu dapat", "anda mendapatkan", "received", "incoming"
    )

    private val INCOME_KREDIT_WORD_REGEX = Regex("""\bkredit\b""")

    private val EXPENSE_SUBSTRINGS = listOf(
        "pembayaran", "bayar", "transfer ke", "kirim", "terkirim",
        "pembelian", "beli", "tarik tunai", "penarikan",
        "berkurang", "dipotong", "terdebet", "qris",
        "paid", "payment", "sent"
    )

    private val EXPENSE_DEBIT_WORD_REGEX = Regex("""\bdebit\b""")

    fun normalize(title: String, text: String): String =
        "$title $text"
            .lowercase()
            .replace("terima kasih", " ")
            .replace("thank you", " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    fun isPromo(t: String): Boolean {
        val strong = PROMO_STRONG_SUBSTRINGS.any { t.contains(it) } || OTP_WORD_REGEX.containsMatchIn(t)
        if (strong) return true
        val weak = PROMO_WEAK_SUBSTRINGS.any { t.contains(it) }
        if (!weak) return false
        val completed = COMPLETION_SUBSTRINGS.any { t.contains(it) }
        return !completed
    }

    fun isFailedOrPending(t: String): Boolean =
        FAILED_OR_PENDING_SUBSTRINGS.any { t.contains(it) }

    fun isNonSaldo(t: String): Boolean =
        NON_SALDO_SUBSTRINGS.any { t.contains(it) } || NON_SALDO_WORDS_REGEX.containsMatchIn(t)

    fun classify(t: String, wallet: Wallet): TxType? {
        val topUpMatch = TOP_UP_REGEX.find(t)
        if (topUpMatch != null) {
            return when (wallet.kind) {
                WalletKind.BANK -> TxType.EXPENSE
                WalletKind.EWALLET -> {
                    val target = topUpMatch.groupValues.getOrNull(2)?.lowercase().orEmpty()
                    val externalTargets = wallet.selfAlias?.let { EXTERNAL_TOPUP_TARGETS - it } ?: EXTERNAL_TOPUP_TARGETS
                    val hasExplicitPayment = EXPLICIT_PAYMENT_MARKERS.any { t.contains(it) }
                    if (target in externalTargets || target in PURCHASE_TOPUP_TARGETS || hasExplicitPayment) {
                        TxType.EXPENSE
                    } else {
                        TxType.INCOME
                    }
                }
            }
        }

        if (STRONG_INCOME_SUBSTRINGS.any { t.contains(it) } || INCOME_KREDIT_WORD_REGEX.containsMatchIn(t)) {
            return TxType.INCOME
        }

        if (EXPENSE_SUBSTRINGS.any { t.contains(it) } || EXPENSE_DEBIT_WORD_REGEX.containsMatchIn(t)) {
            return TxType.EXPENSE
        }

        if (t.contains("diterima")) {
            return TxType.INCOME
        }

        return null
    }
}

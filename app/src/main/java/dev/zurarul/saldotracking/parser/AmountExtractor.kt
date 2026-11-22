package dev.zurarul.saldotracking.parser

object AmountExtractor {
    private val AMOUNT_REGEX = Regex(
        """(?i)\b(?:rp|idr)\.?\s*(\d{1,3}(?:[.,]\d{3})+|\d+)(?:[.,](\d{1,2}))?(?!\d)"""
    )

    private val BALANCE_PREFIX_REGEX = Regex(
        """(?i)(sisa saldo|saldo akhir|saldo rekening|saldo (?:(anda|kamu|mu)\s*)?(saat ini|sekarang)|saldo (anda|kamu|mu)|saldo tersedia|saldo efektif)\s*(:|adalah|sebesar|menjadi)?\s*$"""
    )

    fun extract(text: String): Long? {
        data class Candidate(val amount: Long, val hasTotalPrefix: Boolean)

        val candidates = mutableListOf<Candidate>()
        for (match in AMOUNT_REGEX.findAll(text)) {
            val before = text.substring(0, match.range.first)
            if (BALANCE_PREFIX_REGEX.containsMatchIn(before)) {
                continue
            }
            val digitsOnly = match.groupValues[1].replace(".", "").replace(",", "")
            val parsed = digitsOnly.toLongOrNull() ?: continue
            if (parsed <= 0L || parsed > 10_000_000_000L) {
                continue
            }
            val windowStart = (match.range.first - 20).coerceAtLeast(0)
            val prefixWindow = text.substring(windowStart, match.range.first)
            val hasTotal = prefixWindow.contains("total", ignoreCase = true)
            candidates.add(Candidate(parsed, hasTotal))
        }

        if (candidates.isEmpty()) return null
        val totalCandidate = candidates.lastOrNull { it.hasTotalPrefix }
        return (totalCandidate ?: candidates.first()).amount
    }
}

package dev.zurarul.saldotracking.parser

import dev.zurarul.saldotracking.domain.LogStatus
import dev.zurarul.saldotracking.domain.NotificationLog
import dev.zurarul.saldotracking.domain.Transaction
import dev.zurarul.saldotracking.domain.TxType
import dev.zurarul.saldotracking.domain.Wallet

data class RawNotification(
    val packageName: String,
    val title: String,
    val text: String,
    val postedAtMillis: Long,
    val isOngoing: Boolean = false,
    val isGroupSummary: Boolean = false
)

data class ProcessOutcome(
    val log: NotificationLog,
    val transaction: Transaction?
)

class NotificationProcessor(
    private val deduplicator: Deduplicator = Deduplicator()
) {
    fun process(
        raw: RawNotification,
        enabledWallets: Set<Wallet>
    ): ProcessOutcome? {
        val wallet = Wallet.forPackage(raw.packageName) ?: return null
        if (wallet !in enabledWallets) return null
        if (raw.isOngoing || raw.isGroupSummary) return null
        if (raw.title.isBlank() && raw.text.isBlank()) return null

        val norm = TextRules.normalize(raw.title, raw.text)
        if (norm.isBlank()) return null

        fun buildLog(
            status: LogStatus,
            amount: Long? = null,
            type: TxType? = null
        ) = NotificationLog(
            packageName = raw.packageName,
            sourceApp = wallet.displayName,
            title = raw.title,
            text = raw.text,
            postedAtMillis = raw.postedAtMillis,
            status = status,
            parsedAmountIdr = amount,
            parsedType = type
        )

        if (TextRules.isPromo(norm)) {
            return ProcessOutcome(buildLog(LogStatus.IGNORED_PROMO), null)
        }

        if (TextRules.isFailedOrPending(norm)) {
            return ProcessOutcome(buildLog(LogStatus.IGNORED_FAILED), null)
        }

        val requiredKw = Wallet.requiredKeyword(raw.packageName)
        if (TextRules.isNonSaldo(norm) || (requiredKw != null && !norm.contains(requiredKw))) {
            return ProcessOutcome(buildLog(LogStatus.IGNORED_NON_SALDO), null)
        }

        val amount = AmountExtractor.extract("${raw.title} ${raw.text}")
            ?: return ProcessOutcome(buildLog(LogStatus.IGNORED_UNPARSED), null)

        val type = TextRules.classify(norm, wallet)
            ?: return ProcessOutcome(buildLog(LogStatus.IGNORED_UNPARSED, amount = amount), null)

        if (deduplicator.isDuplicate(wallet, amount, type, raw.postedAtMillis)) {
            return ProcessOutcome(buildLog(LogStatus.IGNORED_DUPLICATE, amount = amount, type = type), null)
        }

        val txTitle = raw.title.ifBlank { raw.text.take(60) }
        val tx = Transaction(
            amountIdr = amount,
            type = type,
            sourceApp = wallet.displayName,
            sourcePackage = raw.packageName,
            title = txTitle,
            rawNotificationText = "${raw.title}\n${raw.text}",
            timestampMillis = raw.postedAtMillis,
            isManual = false
        )
        return ProcessOutcome(
            log = buildLog(LogStatus.CAPTURED, amount = amount, type = type),
            transaction = tx
        )
    }
}

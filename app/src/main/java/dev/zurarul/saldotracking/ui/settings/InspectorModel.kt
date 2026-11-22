package dev.zurarul.saldotracking.ui.settings

import dev.zurarul.saldotracking.domain.LogStatus
import dev.zurarul.saldotracking.domain.NotificationLog
import dev.zurarul.saldotracking.domain.TxType
import dev.zurarul.saldotracking.ui.home.TransactionDraft

fun badgeLabel(status: LogStatus): String = when (status) {
    LogStatus.CAPTURED -> "Captured"
    LogStatus.IGNORED_PROMO -> "Promo"
    LogStatus.IGNORED_FAILED -> "Gagal/Pending"
    LogStatus.IGNORED_NON_SALDO -> "Non-Saldo"
    LogStatus.IGNORED_DUPLICATE -> "Duplicate"
    LogStatus.IGNORED_UNPARSED -> "Unparsed"
}

fun NotificationLog.canPromote(promotedIds: Set<Long> = emptySet()): Boolean =
    status != LogStatus.CAPTURED && id !in promotedIds

fun NotificationLog.toDraft(): TransactionDraft = TransactionDraft(
    id = 0L,
    amountInput = parsedAmountIdr?.toString() ?: "",
    type = parsedType ?: TxType.EXPENSE,
    sourceApp = sourceApp,
    title = title.ifBlank { text.take(60) },
    timestampMillis = postedAtMillis
)

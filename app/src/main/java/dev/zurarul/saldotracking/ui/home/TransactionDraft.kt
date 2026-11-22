package dev.zurarul.saldotracking.ui.home

import dev.zurarul.saldotracking.domain.MANUAL_SOURCE
import dev.zurarul.saldotracking.domain.Transaction
import dev.zurarul.saldotracking.domain.TxType

data class TransactionDraft(
    val id: Long = 0L,
    val amountInput: String = "",
    val type: TxType = TxType.EXPENSE,
    val sourceApp: String = MANUAL_SOURCE,
    val title: String = "",
    val timestampMillis: Long = System.currentTimeMillis()
)

fun parseAmountInput(input: String): Long? {
    val cleaned = input.replace(".", "").replace(",", "").replace(" ", "")
    if (cleaned.isEmpty() || cleaned.length > 12) return null
    if (!cleaned.all { it.isDigit() }) return null
    val parsed = cleaned.toLongOrNull() ?: return null
    return if (parsed > 0L) parsed else null
}

fun TransactionDraft.toTransaction(original: Transaction?): Transaction? {
    val amount = parseAmountInput(amountInput) ?: return null
    val cleanTitle = title.trim()
    if (cleanTitle.isEmpty()) return null
    val cleanSource = sourceApp.trim().ifEmpty { MANUAL_SOURCE }
    return Transaction(
        id = original?.id ?: id,
        amountIdr = amount,
        type = type,
        sourceApp = cleanSource,
        sourcePackage = original?.sourcePackage,
        title = cleanTitle,
        rawNotificationText = original?.rawNotificationText,
        timestampMillis = timestampMillis,
        isManual = original?.isManual ?: true
    )
}

fun Transaction.toDraft(): TransactionDraft = TransactionDraft(
    id = id,
    amountInput = amountIdr.toString(),
    type = type,
    sourceApp = sourceApp,
    title = title,
    timestampMillis = timestampMillis
)

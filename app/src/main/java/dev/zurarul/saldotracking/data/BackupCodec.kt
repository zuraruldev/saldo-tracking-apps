package dev.zurarul.saldotracking.data

import dev.zurarul.saldotracking.domain.Transaction
import dev.zurarul.saldotracking.domain.TxType
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupTransaction(
    val amountIdr: Long,
    val type: String,
    val sourceApp: String,
    val sourcePackage: String? = null,
    val title: String,
    val rawNotificationText: String? = null,
    val timestampMillis: Long,
    val isManual: Boolean
)

@Serializable
data class BackupData(
    val version: Int = 1,
    val baseBalanceIdr: Long,
    val transactions: List<BackupTransaction>
)

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupCodec {
    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(data: BackupData): String = jsonFormat.encodeToString(data)

    fun decode(json: String): BackupData {
        val data = try {
            jsonFormat.decodeFromString<BackupData>(json)
        } catch (e: SerializationException) {
            throw BackupFormatException("Invalid JSON format", e)
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("Invalid backup payload", e)
        }

        if (data.version != 1) {
            throw BackupFormatException("Unsupported backup version: ${data.version}")
        }
        for (tx in data.transactions) {
            if (tx.amountIdr <= 0L) {
                throw BackupFormatException("Invalid transaction amount: ${tx.amountIdr}")
            }
            if (runCatching { TxType.valueOf(tx.type) }.isFailure) {
                throw BackupFormatException("Invalid transaction type: ${tx.type}")
            }
            if (tx.sourceApp.isBlank() || tx.title.isBlank()) {
                throw BackupFormatException("Transaction missing sourceApp or title")
            }
            if (tx.timestampMillis !in 1L..4_102_444_800_000L) {
                throw BackupFormatException("Invalid transaction timestamp: ${tx.timestampMillis}")
            }
        }
        return data
    }

    fun fromTransactions(base: Long, txs: List<Transaction>): BackupData =
        BackupData(
            version = 1,
            baseBalanceIdr = base,
            transactions = txs.map { tx ->
                BackupTransaction(
                    amountIdr = tx.amountIdr,
                    type = tx.type.name,
                    sourceApp = tx.sourceApp,
                    sourcePackage = tx.sourcePackage,
                    title = tx.title,
                    rawNotificationText = tx.rawNotificationText,
                    timestampMillis = tx.timestampMillis,
                    isManual = tx.isManual
                )
            }
        )

    fun toTransactions(data: BackupData): List<Transaction> =
        data.transactions.map { b ->
            val type = runCatching { TxType.valueOf(b.type) }.getOrElse {
                throw BackupFormatException("Invalid transaction type: ${b.type}", it)
            }
            if (b.amountIdr <= 0L) {
                throw BackupFormatException("Invalid transaction amount: ${b.amountIdr}")
            }
            Transaction(
                id = 0L,
                amountIdr = b.amountIdr,
                type = type,
                sourceApp = b.sourceApp,
                sourcePackage = b.sourcePackage,
                title = b.title,
                rawNotificationText = b.rawNotificationText,
                timestampMillis = b.timestampMillis,
                isManual = b.isManual
            )
        }
}

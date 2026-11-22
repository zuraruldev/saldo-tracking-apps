package dev.zurarul.saldotracking.domain

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LogStatus {
    CAPTURED,
    IGNORED_PROMO,
    IGNORED_FAILED,
    IGNORED_NON_SALDO,
    IGNORED_DUPLICATE,
    IGNORED_UNPARSED
}

@Entity(tableName = "notification_logs")
data class NotificationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val sourceApp: String,
    val title: String,
    val text: String,
    val postedAtMillis: Long,
    val status: LogStatus,
    val parsedAmountIdr: Long?,
    val parsedType: TxType?
)

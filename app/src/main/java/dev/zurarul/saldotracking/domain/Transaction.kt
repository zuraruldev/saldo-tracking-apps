package dev.zurarul.saldotracking.domain

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TxType {
    INCOME,
    EXPENSE
}

@Entity(
    tableName = "transactions",
    indices = [Index("timestampMillis")]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountIdr: Long,
    val type: TxType,
    val sourceApp: String,
    val sourcePackage: String?,
    val title: String,
    val rawNotificationText: String?,
    val timestampMillis: Long,
    val isManual: Boolean
)

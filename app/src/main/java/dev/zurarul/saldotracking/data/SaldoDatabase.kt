package dev.zurarul.saldotracking.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.zurarul.saldotracking.domain.NotificationLog
import dev.zurarul.saldotracking.domain.Transaction

@Database(
    entities = [Transaction::class, NotificationLog::class],
    version = 1,
    exportSchema = false
)
abstract class SaldoDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun notificationLogDao(): NotificationLogDao

    companion object {
        fun create(context: Context): SaldoDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                SaldoDatabase::class.java,
                "saldo.db"
            ).build()
    }
}

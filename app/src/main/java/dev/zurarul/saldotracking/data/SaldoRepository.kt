package dev.zurarul.saldotracking.data

import androidx.room.withTransaction
import dev.zurarul.saldotracking.domain.BalanceCalculator
import dev.zurarul.saldotracking.domain.NotificationLog
import dev.zurarul.saldotracking.domain.Transaction
import dev.zurarul.saldotracking.parser.ProcessOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class SaldoRepository(
    private val db: SaldoDatabase,
    private val settings: SettingsRepository
) {
    private val txDao = db.transactionDao()
    private val logDao = db.notificationLogDao()

    val transactions: Flow<List<Transaction>> = txDao.observeAll()
    val recentLogs: Flow<List<NotificationLog>> = logDao.observeRecent(100)

    suspend fun record(outcome: ProcessOutcome) {
        db.withTransaction {
            outcome.transaction?.let { txDao.insert(it) }
            logDao.insert(outcome.log)
            logDao.trimTo(100)
        }
    }

    suspend fun add(tx: Transaction) {
        txDao.insert(tx.copy(id = 0L))
    }

    suspend fun update(tx: Transaction) {
        txDao.update(tx)
    }

    suspend fun delete(tx: Transaction) {
        txDao.delete(tx)
    }

    suspend fun reconcileTo(actualSaldoIdr: Long) {
        val all = txDao.getAll()
        val newBase = BalanceCalculator.rebaseFor(actualSaldoIdr, all)
        settings.setBaseBalance(newBase)
    }

    suspend fun exportJson(): String {
        val currentSettings = settings.settings.first()
        val all = txDao.getAll()
        val data = BackupCodec.fromTransactions(currentSettings.baseBalanceIdr, all)
        return BackupCodec.encode(data)
    }

    fun prepareImport(json: String): BackupData = BackupCodec.decode(json)

    suspend fun applyImport(data: BackupData) {
        val txs = BackupCodec.toTransactions(data)
        db.withTransaction {
            txDao.deleteAll()
            txDao.insertAll(txs)
        }
        settings.setBaseBalance(data.baseBalanceIdr)
    }
}

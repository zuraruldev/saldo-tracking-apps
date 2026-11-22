package dev.zurarul.saldotracking.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.zurarul.saldotracking.domain.NotificationLog
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationLogDao {
    @Query("SELECT * FROM notification_logs ORDER BY postedAtMillis DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<NotificationLog>>

    @Insert
    suspend fun insert(log: NotificationLog)

    @Query(
        "DELETE FROM notification_logs WHERE id NOT IN (" +
            "SELECT id FROM notification_logs ORDER BY postedAtMillis DESC, id DESC LIMIT :keep" +
            ")"
    )
    suspend fun trimTo(keep: Int = 100)
}

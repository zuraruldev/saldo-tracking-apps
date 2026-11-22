package dev.zurarul.saldotracking

import android.content.Context
import dev.zurarul.saldotracking.data.SaldoDatabase
import dev.zurarul.saldotracking.data.SaldoRepository
import dev.zurarul.saldotracking.data.SettingsRepository
import dev.zurarul.saldotracking.data.settingsDataStore
import dev.zurarul.saldotracking.parser.NotificationProcessor

class AppContainer(context: Context) {
    val database: SaldoDatabase = SaldoDatabase.create(context)
    val settingsRepository: SettingsRepository = SettingsRepository(context.settingsDataStore)
    val repository: SaldoRepository = SaldoRepository(database, settingsRepository)
    val processor: NotificationProcessor = NotificationProcessor()
}

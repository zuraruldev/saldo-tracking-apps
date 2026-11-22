package dev.zurarul.saldotracking.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.zurarul.saldotracking.domain.Wallet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

data class AppSettings(
    val baseBalanceIdr: Long = 0L,
    val onboardingDone: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val keepAliveEnabled: Boolean = true,
    val disabledWallets: Set<Wallet> = emptySet()
) {
    val enabledWallets: Set<Wallet>
        get() = Wallet.entries.toSet() - disabledWallets
}

class SettingsRepository(
    private val store: DataStore<Preferences>
) {
    private object Keys {
        val BASE_BALANCE = longPreferencesKey("base_balance_idr")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val KEEP_ALIVE = booleanPreferencesKey("keep_alive_enabled")
        val DISABLED_WALLETS = stringSetPreferencesKey("disabled_wallets")
    }

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        val theme = prefs[Keys.THEME_MODE]?.let {
            runCatching { ThemeMode.valueOf(it) }.getOrNull()
        } ?: ThemeMode.SYSTEM
        val disabled = prefs[Keys.DISABLED_WALLETS].orEmpty().mapNotNull { name ->
            runCatching { Wallet.valueOf(name) }.getOrNull()
        }.toSet()
        AppSettings(
            baseBalanceIdr = prefs[Keys.BASE_BALANCE] ?: 0L,
            onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false,
            themeMode = theme,
            keepAliveEnabled = prefs[Keys.KEEP_ALIVE] ?: true,
            disabledWallets = disabled
        )
    }

    suspend fun setBaseBalance(v: Long) {
        store.edit { it[Keys.BASE_BALANCE] = v }
    }

    suspend fun setOnboardingDone() {
        store.edit { it[Keys.ONBOARDING_DONE] = true }
    }

    suspend fun setThemeMode(m: ThemeMode) {
        store.edit { it[Keys.THEME_MODE] = m.name }
    }

    suspend fun setKeepAlive(enabled: Boolean) {
        store.edit { it[Keys.KEEP_ALIVE] = enabled }
    }

    suspend fun setWalletEnabled(w: Wallet, enabled: Boolean) {
        store.edit { prefs ->
            val current = prefs[Keys.DISABLED_WALLETS].orEmpty().toMutableSet()
            if (enabled) current.remove(w.name) else current.add(w.name)
            prefs[Keys.DISABLED_WALLETS] = current
        }
    }
}

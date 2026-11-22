package dev.zurarul.saldotracking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.zurarul.saldotracking.data.AppSettings
import dev.zurarul.saldotracking.service.KeepAliveService
import dev.zurarul.saldotracking.ui.SaldoAppRoot
import dev.zurarul.saldotracking.ui.theme.SaldoTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SaldoApplication).container
        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = AppSettings()
            )
            SaldoTheme(themeMode = settings.themeMode) {
                SaldoAppRoot(container = container)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val container = (application as SaldoApplication).container
        lifecycleScope.launch {
            val s = container.settingsRepository.settings.first()
            if (s.keepAliveEnabled) {
                KeepAliveService.start(this@MainActivity)
            }
        }
    }
}

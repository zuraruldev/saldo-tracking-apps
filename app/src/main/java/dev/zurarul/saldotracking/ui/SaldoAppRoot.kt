package dev.zurarul.saldotracking.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.zurarul.saldotracking.AppContainer
import dev.zurarul.saldotracking.R
import dev.zurarul.saldotracking.data.AppSettings
import dev.zurarul.saldotracking.ui.home.HomeScreen
import dev.zurarul.saldotracking.ui.home.HomeViewModel
import dev.zurarul.saldotracking.ui.onboarding.OnboardingScreen
import dev.zurarul.saldotracking.ui.settings.SettingsScreen
import dev.zurarul.saldotracking.ui.settings.SettingsViewModel
import dev.zurarul.saldotracking.ui.stats.StatsScreen
import dev.zurarul.saldotracking.ui.stats.StatsViewModel
import kotlinx.coroutines.launch

enum class AppTab(val label: String) {
    HOME("Home"),
    STATS("Stats"),
    SETTINGS("Settings")
}

@Composable
fun SaldoAppRoot(container: AppContainer) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings(onboardingDone = true)
    )
    val scope = rememberCoroutineScope()
    var currentTab by rememberSaveable { mutableStateOf(AppTab.HOME) }

    if (!settings.onboardingDone) {
        OnboardingScreen(
            onComplete = { initialBase ->
                scope.launch {
                    container.settingsRepository.setBaseBalance(initialBase)
                    container.settingsRepository.setOnboardingDone()
                }
            }
        )
        return
    }

    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
    val statsViewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == AppTab.HOME,
                    onClick = { currentTab = AppTab.HOME },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.STATS,
                    onClick = { currentTab = AppTab.STATS },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_tab_stats),
                            contentDescription = "Stats"
                        )
                    },
                    label = { Text("Stats") }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.SETTINGS,
                    onClick = { currentTab = AppTab.SETTINGS },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.HOME -> HomeScreen(
                    viewModel = homeViewModel,
                    onOpenSettings = { currentTab = AppTab.SETTINGS }
                )
                AppTab.STATS -> StatsScreen(viewModel = statsViewModel)
                AppTab.SETTINGS -> SettingsScreen(viewModel = settingsViewModel)
            }
        }
    }
}

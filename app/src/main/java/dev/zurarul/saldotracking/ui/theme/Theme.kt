package dev.zurarul.saldotracking.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.zurarul.saldotracking.data.ThemeMode

@Immutable
data class CashflowColors(
    val income: Color,
    val expense: Color
)

val LocalCashflowColors = staticCompositionLocalOf {
    CashflowColors(income = DarkIncome, expense = DarkExpense)
}

private val DarkColors = darkColorScheme(
    primary = DarkIncome,
    secondary = Color(0xFF38BDF8),
    tertiary = DarkExpense,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = Color(0xFFF0F6FC),
    onSurface = Color(0xFFF0F6FC),
    onSurfaceVariant = Color(0xFF8B949E)
)

private val LightColors = lightColorScheme(
    primary = LightIncome,
    secondary = Color(0xFF0284C7),
    tertiary = LightExpense,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF64748B)
)

@Composable
fun SaldoTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colorScheme = if (isDark) DarkColors else LightColors
    val cashflow = if (isDark) {
        CashflowColors(income = DarkIncome, expense = DarkExpense)
    } else {
        CashflowColors(income = LightIncome, expense = LightExpense)
    }

    CompositionLocalProvider(LocalCashflowColors provides cashflow) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

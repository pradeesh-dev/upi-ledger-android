package com.newdreamdata.upiledger.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Teal = Color(0xFF0F766E)
private val TealLight = Color(0xFF34C3B4)
private val TealContainer = Color(0xFFD7F0EC)
private val TealContainerDark = Color(0xFF163A37)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = Color(0xFF05302C),
    secondary = Color(0xFF4E6B68),
    background = Color(0xFFF3F5F7),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9EDF1),
    onSurfaceVariant = Color(0xFF5E6B78),
    error = Color(0xFFB42318),
    tertiary = Color(0xFF9A5B00),
    tertiaryContainer = Color(0xFFFBEED4)
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF06211E),
    primaryContainer = TealContainerDark,
    onPrimaryContainer = Color(0xFFBDEDE6),
    secondary = Color(0xFFB0CDC9),
    background = Color(0xFF0F1519),
    surface = Color(0xFF182028),
    surfaceVariant = Color(0xFF212B35),
    onSurfaceVariant = Color(0xFF97A5B2),
    error = Color(0xFFFF7D72),
    tertiary = Color(0xFFF2B357),
    tertiaryContainer = Color(0xFF3B2D12)
)

@Composable
fun UpiLedgerTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

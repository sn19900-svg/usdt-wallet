package com.nabil.usdtwallet.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ألوان المحفظة - داكن احترافي
val CryptoGreen = Color(0xFF00D4AA)
val CryptoGreenDim = Color(0xFF00A882)
val CryptoDark = Color(0xFF0A0E1A)
val CryptoDarkCard = Color(0xFF111827)
val CryptoDarkSurface = Color(0xFF1A2235)
val CryptoWhite = Color(0xFFF0F4FF)
val CryptoGray = Color(0xFF8B9BB4)
val CryptoRed = Color(0xFFFF4D6D)
val CryptoYellow = Color(0xFFFFB800)
val CryptoBlue = Color(0xFF3D8EFF)

private val DarkColorScheme = darkColorScheme(
    primary = CryptoGreen,
    onPrimary = CryptoDark,
    primaryContainer = CryptoGreenDim,
    secondary = CryptoBlue,
    background = CryptoDark,
    surface = CryptoDarkCard,
    surfaceVariant = CryptoDarkSurface,
    onBackground = CryptoWhite,
    onSurface = CryptoWhite,
    onSurfaceVariant = CryptoGray,
    error = CryptoRed,
    outline = Color(0xFF2A3547)
)

@Composable
fun USDTWalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}

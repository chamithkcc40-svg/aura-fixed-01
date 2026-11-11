package com.aura.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** AURA visual identity: deep navy/black with purple, blue and cyan light. */
object AuraColors {
    val Bg = Color(0xFF05060F)
    val Navy = Color(0xFF0A1030)
    val Ink = Color(0xFF0E1230)
    val Purple = Color(0xFF7B5CFF)
    val Blue = Color(0xFF3B82FF)
    val Cyan = Color(0xFF22D3EE)
    val Pink = Color(0xFFFF5C9D)
    val TextPrimary = Color(0xFFF5F7FF)
    val TextSecondary = Color(0xB8DCE3FF)
    val TextTertiary = Color(0x80C9D1F5)
    val Danger = Color(0xFFFF6B7D)
}

object AuraType {
    private val sans = FontFamily.SansSerif
    val Display = TextStyle(fontFamily = sans, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.9).sp)
    val Headline = TextStyle(fontFamily = sans, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
    val Title = TextStyle(fontFamily = sans, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
    val Section = TextStyle(fontFamily = sans, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    val Body = TextStyle(fontFamily = sans, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val Caption = TextStyle(fontFamily = sans, fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val Label = TextStyle(fontFamily = sans, fontSize = 10.5.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
    val Wordmark = TextStyle(fontFamily = sans, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
}

@Composable
fun AuraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AuraColors.Purple,
            secondary = AuraColors.Cyan,
            background = AuraColors.Bg,
            surface = AuraColors.Ink,
            onBackground = AuraColors.TextPrimary,
            onSurface = AuraColors.TextPrimary,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides AuraColors.TextPrimary, content = content)
    }
}

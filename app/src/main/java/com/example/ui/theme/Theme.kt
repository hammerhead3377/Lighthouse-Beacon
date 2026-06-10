package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = PolishAccentBlue,
    secondary = PolishHighlightBlue,
    tertiary = PolishSageGreen,
    background = PolishBackground,
    surface = PolishSurface,
    onBackground = PolishTextWhite,
    onSurface = PolishTextWhite,
    primaryContainer = PolishWidget,
    onPrimaryContainer = PolishAccentBlue,
    secondaryContainer = PolishSurface,
    onSecondaryContainer = PolishTextMuted
  )

private val LightColorScheme = DarkColorScheme


@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

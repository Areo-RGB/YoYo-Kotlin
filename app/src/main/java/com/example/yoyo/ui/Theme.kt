package com.example.yoyo.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

// IBM Carbon-inspired palette, shared with the Flutter app.
val Blue = Color(0xff0f62fe)
val TextColor = Color(0xff161616)
val Muted = Color(0xff6f6f6f)
val BorderColor = Color(0xffe0e0e0)
val Layer = Color(0xfff4f4f4)
val LayerTwo = Color(0xffe8e8e8)
val Green = Color(0xff198038)
val Red = Color(0xffda1e28)
val Purple = Color(0xff8a3ffc)
val Orange = Color(0xffba4e00)
val Charcoal = Color(0xff525252)

private val colors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    background = Color.White,
    onBackground = TextColor,
    surface = Color.White,
    onSurface = TextColor,
    surfaceVariant = Layer,
    onSurfaceVariant = Muted,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Layer,
    surfaceContainerLow = Color.White,
    secondaryContainer = LayerTwo,
    onSecondaryContainer = TextColor,
    outline = BorderColor,
    outlineVariant = BorderColor,
    error = Red,
)

// No colours here: a colour in the text style would override the content
// colour buttons and dialogs provide.
private val typography = Typography(
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
)

@Composable
fun YoYoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography) {
        CompositionLocalProvider(LocalContentColor provides TextColor, content = content)
    }
}

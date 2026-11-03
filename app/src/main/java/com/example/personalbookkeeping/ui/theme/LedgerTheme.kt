package com.example.personalbookkeeping.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object LedgerColors {
    val Canvas = Color(0xFFF5F3EB)
    val Surface = Color(0xFFFFFDF8)
    val Ink = Color(0xFF1D2924)
    val MutedInk = Color(0xFF68746B)
    val Green = Color(0xFF2C6B57)
    val Income = Color(0xFF34775D)
    val Expense = Color(0xFFA84E37)
    val Divider = Color(0xFFE1DED3)
    val SoftGreen = Color(0xFFE4EEE7)
    val SoftTerracotta = Color(0xFFF4E6DF)
}

private val LedgerLightColors = lightColorScheme(
    primary = LedgerColors.Green,
    onPrimary = Color.White,
    primaryContainer = LedgerColors.SoftGreen,
    onPrimaryContainer = LedgerColors.Ink,
    secondary = LedgerColors.Income,
    onSecondary = Color.White,
    secondaryContainer = LedgerColors.SoftGreen,
    onSecondaryContainer = LedgerColors.Ink,
    error = LedgerColors.Expense,
    onError = Color.White,
    background = LedgerColors.Canvas,
    onBackground = LedgerColors.Ink,
    surface = LedgerColors.Surface,
    onSurface = LedgerColors.Ink,
    surfaceVariant = Color(0xFFECE9DF),
    onSurfaceVariant = LedgerColors.MutedInk,
    outline = LedgerColors.Divider
)

private val LedgerTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        lineHeight = 40.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
)

@Composable
fun LedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LedgerLightColors,
        typography = LedgerTypography,
        content = content
    )
}

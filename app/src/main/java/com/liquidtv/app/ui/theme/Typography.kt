package com.liquidtv.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Uses system sans-serif for compatibility; swap in a custom font if bundled
object LiquidTypography {
    private val SansFontFamily = FontFamily.SansSerif

    // Display — hero text
    val DisplayLarge = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 72.sp,
        lineHeight = 80.sp,
        letterSpacing = (-1).sp,
        color = LiquidColors.TextPrimary,
    )
    val DisplayMedium = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 52.sp,
        lineHeight = 60.sp,
        letterSpacing = (-0.5).sp,
        color = LiquidColors.TextPrimary,
    )

    // Headlines
    val HeadlineLarge = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
        color = LiquidColors.TextPrimary,
    )
    val HeadlineMedium = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
        color = LiquidColors.TextPrimary,
    )
    val HeadlineSmall = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
        color = LiquidColors.TextPrimary,
    )

    // Title
    val TitleLarge = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        color = LiquidColors.TextPrimary,
    )
    val TitleMedium = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
        color = LiquidColors.TextPrimary,
    )
    val TitleSmall = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = LiquidColors.TextSecondary,
    )

    // Body
    val BodyLarge = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        color = LiquidColors.TextSecondary,
    )
    val BodyMedium = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
        color = LiquidColors.TextSecondary,
    )
    val BodySmall = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        color = LiquidColors.TextTertiary,
    )

    // Label
    val LabelLarge = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = LiquidColors.TextPrimary,
    )
    val LabelMedium = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        color = LiquidColors.TextSecondary,
    )
    val LabelSmall = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
        color = LiquidColors.TextTertiary,
    )

    // Navigation
    val NavLabel = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    )

    // Clock / ambient
    val ClockLarge = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.Thin,
        fontSize = 96.sp,
        lineHeight = 100.sp,
        letterSpacing = (-4).sp,
        color = LiquidColors.TextPrimary,
    )
    val ClockSmall = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 2.sp,
        color = LiquidColors.TextSecondary,
    )
}


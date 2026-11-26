package com.liquidtv.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.liquidtv.app.R

val CairoFontFamily = FontFamily(
    Font(R.font.cairo, FontWeight.Light),
    Font(R.font.cairo, FontWeight.Normal),
    Font(R.font.cairo, FontWeight.Medium),
    Font(R.font.cairo, FontWeight.SemiBold),
    Font(R.font.cairo, FontWeight.Bold),
)

object LiquidTypography {
    private val AppFontFamily = CairoFontFamily

    // Display — hero text
    val DisplayLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 64.sp,
        lineHeight = 76.sp,
        letterSpacing = (-0.5).sp,
    )
    val DisplayMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 46.sp,
        lineHeight = 56.sp,
        letterSpacing = 0.sp,
    )

    // Headlines
    val HeadlineLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 42.sp,
        letterSpacing = 0.sp,
    )
    val HeadlineMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    )
    val HeadlineSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    )

    // Title
    val TitleLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    )
    val TitleMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    )
    val TitleSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.sp,
    )

    // Body
    val BodyLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    )
    val BodyMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    )
    val BodySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    )

    // Label
    val LabelLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    )
    val LabelMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    )
    val LabelSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.sp,
    )

    // Navigation
    val NavLabel = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    )

    // Clock / ambient
    val ClockLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 90.sp,
        lineHeight = 96.sp,
        letterSpacing = (-2).sp,
    )
    val ClockSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
    )
}

val LiquidMaterialTypography = Typography(
    displayLarge = LiquidTypography.DisplayLarge,
    displayMedium = LiquidTypography.DisplayMedium,
    headlineLarge = LiquidTypography.HeadlineLarge,
    headlineMedium = LiquidTypography.HeadlineMedium,
    headlineSmall = LiquidTypography.HeadlineSmall,
    titleLarge = LiquidTypography.TitleLarge,
    titleMedium = LiquidTypography.TitleMedium,
    titleSmall = LiquidTypography.TitleSmall,
    bodyLarge = LiquidTypography.BodyLarge,
    bodyMedium = LiquidTypography.BodyMedium,
    bodySmall = LiquidTypography.BodySmall,
    labelLarge = LiquidTypography.LabelLarge,
    labelMedium = LiquidTypography.LabelMedium,
    labelSmall = LiquidTypography.LabelSmall,
)

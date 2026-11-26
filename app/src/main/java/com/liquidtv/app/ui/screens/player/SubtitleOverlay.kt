package com.liquidtv.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquidtv.app.domain.model.SubtitleBackgroundOption
import com.liquidtv.app.domain.model.SubtitleEdgeOption
import com.liquidtv.app.domain.model.SubtitleFontOption
import com.liquidtv.app.domain.model.SubtitlePositionOption
import com.liquidtv.app.domain.model.SubtitleStyleSettings

fun SubtitleFontOption.toComposeFontFamily(): FontFamily = FontFamily(
    Font(
        resId = fontResId,
        weight = FontWeight(actualWeight),
    )
)

@Composable
fun SubtitleOverlay(
    cueText: String?,
    style: SubtitleStyleSettings,
    controlsVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (cueText.isNullOrBlank()) return

    val fontOption = style.fontOption
    val fontFamily = remember(fontOption) { fontOption.toComposeFontFamily() }
    val fontSize = style.fontSizeSp.sp
    val lineHeight = (style.fontSizeSp * 1.35f).sp
    val textColor = Color(style.colorOption.argbHex.toInt())

    val containerBg = when (style.backgroundOption) {
        SubtitleBackgroundOption.OFF -> Color.Transparent
        SubtitleBackgroundOption.TRANSLUCENT -> Color.Black.copy(alpha = 0.62f)
        SubtitleBackgroundOption.SOLID -> Color.Black
    }

    val alignment = when (style.positionOption) {
        SubtitlePositionOption.BOTTOM -> Alignment.BottomCenter
        SubtitlePositionOption.RAISED -> Alignment.BottomCenter
        SubtitlePositionOption.MIDDLE -> Alignment.Center
    }

    val bottomPadding = when (style.positionOption) {
        SubtitlePositionOption.BOTTOM -> if (controlsVisible) 130.dp else 32.dp
        SubtitlePositionOption.RAISED -> if (controlsVisible) 170.dp else 96.dp
        SubtitlePositionOption.MIDDLE -> 0.dp
    }

    val baseTextStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight(fontOption.actualWeight),
        fontSize = fontSize,
        lineHeight = lineHeight,
        textAlign = TextAlign.Center,
        textDirection = TextDirection.ContentOrRtl,
        shadow = if (style.edgeOption == SubtitleEdgeOption.SHADOW) {
            Shadow(
                color = Color.Black.copy(alpha = 0.92f),
                offset = Offset(2f, 2f),
                blurRadius = 6f,
            )
        } else {
            null
        },
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .padding(bottom = bottomPadding),
        contentAlignment = alignment,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(containerBg)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("external_subtitle_overlay"),
            contentAlignment = Alignment.Center,
        ) {
            if (style.edgeOption == SubtitleEdgeOption.OUTLINE) {
                Text(
                    text = cueText,
                    style = baseTextStyle.copy(
                        color = Color.Black,
                        drawStyle = Stroke(
                            width = 7f,
                            join = StrokeJoin.Round,
                        ),
                    ),
                )
            }
            Text(
                text = cueText,
                style = baseTextStyle.copy(color = textColor),
            )
        }
    }
}

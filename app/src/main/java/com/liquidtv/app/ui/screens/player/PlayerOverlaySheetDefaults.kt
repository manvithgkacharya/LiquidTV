package com.liquidtv.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.theme.AppTheme

object PlayerOverlaySheetDefaults {
    val SheetMaxWidth = 460.dp
    val SheetMaxHeight = 460.dp
    val SheetMinWidth = 280.dp
    val SheetMinHeight = 260.dp
    val SheetCornerRadius = 20.dp
    val SheetContentHorizontalPadding = 14.dp
    val SheetContentVerticalPadding = 12.dp
    val SheetSectionSpacing = 6.dp

    val OptionCardHeight = 48.dp
    val OptionCardCornerRadius = 12.dp
    val OptionCardHorizontalPadding = 10.dp
    val OptionCardVerticalPadding = 4.dp
}

@Composable
fun PlayerOverlaySheetCard(
    testTag: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxCardWidth: androidx.compose.ui.unit.Dp = PlayerOverlaySheetDefaults.SheetMaxWidth,
    maxCardHeight: androidx.compose.ui.unit.Dp = PlayerOverlaySheetDefaults.SheetMaxHeight,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val horizontalMargin = if (maxWidth < 480.dp) 10.dp else 20.dp
        val verticalMargin = if (maxHeight < 480.dp) 8.dp else 18.dp
        val cardWidth = (maxWidth - horizontalMargin * 2).coerceIn(
            PlayerOverlaySheetDefaults.SheetMinWidth,
            maxCardWidth,
        )
        val cardHeight = (maxHeight - verticalMargin * 2).coerceIn(
            PlayerOverlaySheetDefaults.SheetMinHeight,
            maxCardHeight,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.dialogScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        LiquidGlassSurface(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .testTag(testTag),
            cornerRadius = PlayerOverlaySheetDefaults.SheetCornerRadius,
            glassAlpha = 0.95f,
            glassColor = colors.panel,
            borderAlpha = 0.78f,
            borderColor = colors.border,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = PlayerOverlaySheetDefaults.SheetContentHorizontalPadding,
                        vertical = PlayerOverlaySheetDefaults.SheetContentVerticalPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(PlayerOverlaySheetDefaults.SheetSectionSpacing),
                content = content,
            )
        }
    }
}

@Composable
fun PlayerCompactOptionCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val backgroundColor = when {
        isActive -> colors.success.copy(alpha = 0.16f)
        selected -> colors.selected
        isFocused -> colors.surface.copy(alpha = 0.90f)
        else -> colors.card.copy(alpha = 0.65f)
    }
    val borderColor = when {
        isFocused -> colors.focus
        isActive -> colors.success
        selected -> colors.accent
        else -> colors.border.copy(alpha = 0.50f)
    }
    val shape = RoundedCornerShape(PlayerOverlaySheetDefaults.OptionCardCornerRadius)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PlayerOverlaySheetDefaults.OptionCardHeight)
            .clip(shape)
            .background(backgroundColor)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = shape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource)
            .padding(
                horizontal = PlayerOverlaySheetDefaults.OptionCardHorizontalPadding,
                vertical = PlayerOverlaySheetDefaults.OptionCardVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

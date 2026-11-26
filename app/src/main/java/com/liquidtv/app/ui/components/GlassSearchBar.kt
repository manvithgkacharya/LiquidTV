package com.liquidtv.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidTypography

/**
 * Pill-shaped glass search bar optimised for Android TV.
 *
 * - Expands horizontally when focused via an animated [width] spring.
 * - Uses [BasicTextField] for full TV-keyboard compatibility.
 * - Exposes [onFocusChange] so callers can react to keyboard visibility.
 */
@Composable
fun GlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    placeholder: String = "ابحث عن قناة أو فيلم أو مسلسل...",
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }

    val targetWidth = if (isFocused) 520.dp else 360.dp
    val barWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
        label = "searchBarWidth",
    )

    val colors = AppTheme.colors
    val placeholderAlpha by animateFloatAsState(
        targetValue = if (query.isEmpty()) 1f else 0f,
        label = "placeholderAlpha",
    )

    LiquidGlassSurface(
        modifier = modifier.widthIn(max = barWidth).fillMaxWidth(),
        cornerRadius = 50.dp,
        glassColor = colors.surface,
        glassAlpha = 0.86f,
        isFocused = isFocused,
        onClick = { focusRequester.requestFocus() },
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Search icon
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "بحث",
                tint = if (isFocused) colors.accent else colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )

            // Text field with inline placeholder
            Box(modifier = Modifier.weight(1f)) {
                // Placeholder
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = LiquidTypography.BodySmall,
                        color = colors.muted,
                        modifier = Modifier.alpha(placeholderAlpha),
                    )
                }

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = LiquidTypography.BodySmall.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { /* caller handles */ },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            isFocused = state.isFocused
                            onFocusChange(state.isFocused)
                        }
                        .focusable(interactionSource = interactionSource),
                )
            }

            // Clear button – visible when there's text
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "مسح البحث",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

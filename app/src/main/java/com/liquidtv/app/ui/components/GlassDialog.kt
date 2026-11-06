package com.liquidtv.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography

/**
 * Premium glass confirmation / information dialog for Android TV.
 *
 * Features:
 * - Scale + fade entrance animation via [AnimatedVisibility].
 * - Optional [icon] with tinted treatment.
 * - Confirm and optional cancel actions, both glass-styled.
 * - Auto-focuses the primary confirm button so D-pad navigation works
 *   immediately after the dialog opens.
 */
@Composable
fun GlassDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String = "OK",
    cancelLabel: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = LiquidColors.AccentBlue,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
) {
    if (!visible) return

    val confirmFocusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = properties,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(220)) +
                    scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 350f)),
            exit = fadeOut(animationSpec = tween(180)) +
                    scaleOut(targetScale = 0.88f),
        ) {
            // Scrim + card wrapper
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                LiquidGlassSurface(
                    modifier = Modifier
                        .widthIn(min = 320.dp, max = 500.dp)
                        .wrapContentHeight(),
                    cornerRadius = 24.dp,
                    borderColor = LiquidColors.GlassBorderFocused,
                    onClick = { /* consume clicks */ },
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Optional icon
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(48.dp),
                            )
                        }

                        // Title
                        Text(
                            text = title,
                            style = LiquidTypography.TitleMedium,
                            color = LiquidColors.TextPrimary,
                            textAlign = TextAlign.Center,
                        )

                        // Message
                        Text(
                            text = message,
                            style = LiquidTypography.BodySmall,
                            color = LiquidColors.TextSecondary,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Action row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            // Cancel (optional)
                            if (cancelLabel != null) {
                                GlassButton(
                                    label = cancelLabel,
                                    onClick = onDismiss,
                                    isPrimary = false,
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            // Confirm
                            GlassButton(
                                label = confirmLabel,
                                onClick = onConfirm,
                                isPrimary = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(confirmFocusRequester),
                            )
                        }
                    }
                }
            }
        }
    }

    // Auto-focus confirm button when dialog opens
    LaunchedEffect(visible) {
        if (visible) {
            confirmFocusRequester.requestFocus()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading dialog variant
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Glass-styled loading dialog showing a spinner and optional [message].
 */
@Composable
fun GlassLoadingDialog(
    visible: Boolean,
    message: String = "Loading…",
    onDismiss: () -> Unit = {},
    properties: DialogProperties = DialogProperties(
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
        usePlatformDefaultWidth = false,
    ),
) {
    if (!visible) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = properties,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.88f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 350f)),
            exit = fadeOut(tween(180)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.50f)),
                contentAlignment = Alignment.Center,
            ) {
                LiquidGlassSurface(
                    modifier = Modifier
                        .width(280.dp)
                        .wrapContentHeight(),
                    cornerRadius = 24.dp,
                    borderColor = LiquidColors.GlassBorderFocused,
                    onClick = {},
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        LoadingSpinner(size = 56.dp)

                        Text(
                            text = message,
                            style = LiquidTypography.BodySmall,
                            color = LiquidColors.TextSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal – glass-styled button used within dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GlassButton(
    label: String,
    onClick: () -> Unit,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusedColor = if (isPrimary) LiquidColors.AccentBlue else LiquidColors.GlassBorderFocused

    LiquidGlassSurface(
        modifier = modifier.height(48.dp),
        cornerRadius = 12.dp,
        borderColor = if (isPrimary) LiquidColors.AccentBlue.copy(alpha = 0.7f) else LiquidColors.GlassBorder,
        onClick = onClick,
        interactionSource = interactionSource,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = LiquidTypography.TitleMedium,
                color = if (isPrimary) LiquidColors.AccentBlue else LiquidColors.TextPrimary,
            )
        }
    }
}

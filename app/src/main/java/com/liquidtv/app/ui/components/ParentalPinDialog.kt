package com.liquidtv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.liquidtv.app.ui.theme.AppTheme
import com.liquidtv.app.ui.theme.LiquidTypography

@Composable
fun ParentalPinDialog(
    visible: Boolean,
    title: String,
    subtitle: String,
    confirmLabel: String = "تأكيد",
    errorMessage: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val colors = AppTheme.colors
    var pin by remember(visible) { mutableStateOf("") }
    var localError by remember(visible, errorMessage) { mutableStateOf(errorMessage) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(visible) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(errorMessage) {
        localError = errorMessage
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.dialogScrim),
            contentAlignment = Alignment.Center,
        ) {
            LiquidGlassSurface(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(min = 320.dp, max = 440.dp),
                cornerRadius = 24.dp,
                glassColor = colors.panel,
                glassAlpha = 0.95f,
                borderAlpha = 0.8f,
                borderColor = colors.border,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "رمز الرقابة الأبوية",
                        tint = colors.accent,
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        text = title,
                        style = LiquidTypography.TitleMedium,
                        color = colors.text,
                    )
                    Text(
                        text = subtitle,
                        style = LiquidTypography.BodySmall,
                        color = colors.textSecondary,
                    )

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { input ->
                            val digits = input.filter { it.isDigit() }.take(8)
                            pin = digits
                            localError = null
                        },
                        singleLine = true,
                        label = { Text("رمز مكوّن من 4 إلى 8 أرقام") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (pin.length < 4) {
                                    localError = "أدخل 4 أرقام على الأقل"
                                } else {
                                    onConfirm(pin)
                                }
                            },
                        ),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.text,
                            unfocusedTextColor = colors.text,
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border,
                            focusedLabelColor = colors.accentText,
                            unfocusedLabelColor = colors.textSecondary,
                            cursorColor = colors.accent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .testTag("pin_input_field"),
                    )

                    localError?.takeIf { it.isNotBlank() }?.let { err ->
                        Text(
                            text = err,
                            style = LiquidTypography.BodySmall,
                            color = colors.danger,
                            modifier = Modifier.testTag("pin_error_text"),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PinDialogButton(
                            label = "إلغاء",
                            isPrimary = false,
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pin_cancel_button"),
                        )
                        PinDialogButton(
                            label = confirmLabel,
                            isPrimary = true,
                            onClick = {
                                if (pin.length < 4) {
                                    localError = "أدخل 4 أرقام على الأقل"
                                } else {
                                    onConfirm(pin)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pin_confirm_button"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinDialogButton(
    label: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isPrimary) {
                    Brush.horizontalGradient(listOf(colors.accent, colors.waveTwo))
                } else {
                    val bg = if (isFocused) colors.selected else colors.surface
                    Brush.horizontalGradient(listOf(bg, bg))
                },
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) colors.focus else colors.border,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = LiquidTypography.LabelLarge,
            color = if (isPrimary) colors.onAccent else colors.text,
        )
    }
}

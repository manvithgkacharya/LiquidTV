package com.liquidtv.app.ui.utils

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun wideCategoriesSidebarWidth(contentWidth: Dp): Dp =
    (contentWidth * 0.31f).coerceIn(232.dp, 400.dp)

package org.thoughtcrime.securesms.ui

import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

fun boldStyle(size: TextUnit) = TextStyle.Default.copy(
    fontWeight = FontWeight.Bold,
    fontSize = size
)

fun defaultStyle(size: TextUnit) = TextStyle.Default.copy(
    fontSize = size,
    lineHeight = size * 1.2
)

val sessionTypography = Typography(
    h1 = boldStyle(36.sp),
    h2 = boldStyle(32.sp),
    h3 = boldStyle(29.sp),
    h4 = boldStyle(26.sp),
    h5 = boldStyle(23.sp),
    h6 = boldStyle(20.sp),
)

val Typography.xl get() = defaultStyle(18.sp)
val Typography.large get() = defaultStyle(16.sp)
val Typography.base get() = defaultStyle(14.sp)
val Typography.baseBold get() = boldStyle(14.sp)
val Typography.baseMonospace get() = defaultStyle(14.sp).copy(fontFamily = FontFamily.Monospace)
val Typography.small get() = defaultStyle(12.sp)
val Typography.smallMonospace get() = defaultStyle(12.sp).copy(fontFamily = FontFamily.Monospace)
val Typography.extraSmall get() = defaultStyle(11.sp)
val Typography.extraSmallMonospace get() = defaultStyle(11.sp).copy(fontFamily = FontFamily.Monospace)
val Typography.fine get() = defaultStyle(9.sp)

val Typography.h7 get() = boldStyle(18.sp)
val Typography.h8 get() = boldStyle(16.sp)
val Typography.h9 get() = boldStyle(14.sp)

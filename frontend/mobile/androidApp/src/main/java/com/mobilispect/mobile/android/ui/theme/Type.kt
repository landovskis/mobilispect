package com.mobilispect.mobile.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp


val baseline = Typography()

val AppTypography = Typography(
    titleMedium = baseline.titleMedium.copy(fontWeight = FontWeight.Bold),
    bodySmall = baseline.bodySmall.copy(fontWeight = FontWeight.Normal, fontSize = 24.sp),
    bodyMedium = baseline.bodyMedium.copy(fontWeight = FontWeight.Normal, fontSize = 32.sp),
    bodyLarge = baseline.bodyLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 40.sp),
)

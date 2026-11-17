package com.resolvecommunity.awaydoomscrollin

import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Thumb and edge of a switch that is off; clearly visible on the app's dark cards. */
private val OffSwitchGray = Color(0xFF8B95A7)

/**
 * One look for every switch in the app: a white thumb on the accent color
 * when on, a gray thumb and gray edge when off. Material's default edge uses
 * the theme's faint outline color, which vanished on the dark cards.
 */
@Composable
internal fun appSwitchColors(accent: Color): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = accent,
    checkedBorderColor = accent,
    uncheckedThumbColor = OffSwitchGray,
    uncheckedTrackColor = Color(0xFF1E2A40),
    uncheckedBorderColor = OffSwitchGray
)

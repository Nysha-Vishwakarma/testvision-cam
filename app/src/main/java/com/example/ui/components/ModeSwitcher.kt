package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.CameraMode

/**
 * Minimal horizontal glass pill mode switcher.
 * Provides modes: "Photo", "Filter", "Pose Guide", "AI Photo"
 * with animated sliding highlight and haptic feedback.
 */
@Composable
fun ModeSwitcher(
    currentMode: CameraMode,
    onModeSelected: (CameraMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val modes = CameraMode.entries.toTypedArray()
    val selectedIndex = modes.indexOf(currentMode).coerceAtLeast(0)

    // Animated indicator position offset
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "mode_switcher_indicator"
    )

    GlassPanel(
        shape = RoundedCornerShape(20.dp),
        elevation = 6.dp,
        modifier = modifier.testTag("mode_switcher")
    ) {
        Box(
            modifier = Modifier.padding(3.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val itemWidth = 76.dp
            val itemHeight = 32.dp

            // Sliding background highlight capsule
            Box(
                modifier = Modifier
                    .padding(start = itemWidth * animatedIndex)
                    .width(itemWidth)
                    .height(itemHeight)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.18f))
                    .testTag("mode_highlight_indicator")
            )

            // Mode Label Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                modes.forEach { mode ->
                    val isSelected = mode == currentMode
                    val textAlpha = if (isSelected) 1.0f else 0.45f
                    val fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    val fontSize = if (isSelected) 12.sp else 11.5.sp

                    Box(
                        modifier = Modifier
                            .width(itemWidth)
                            .height(itemHeight)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (mode != currentMode) {
                                    onModeSelected(mode)
                                } else if (mode != CameraMode.PHOTO) {
                                    // Tapping already active sub-mode returns to standard Photo mode
                                    onModeSelected(CameraMode.PHOTO)
                                }
                            }
                            .testTag("mode_tab_${mode.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.shortLabel,
                            color = Color.White.copy(alpha = textAlpha),
                            fontWeight = fontWeight,
                            fontSize = fontSize,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

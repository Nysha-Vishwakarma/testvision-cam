package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Minimalist, tactile shutter button for capturing photos.
 * Features an outer frosted glass ring and an inner solid white trigger
 * with smooth press and capture animations.
 */
@Composable
fun ShutterButton(
    onClick: () -> Unit,
    isCapturing: Boolean,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isCapturing -> 0.88f
            isPressed -> 0.92f
            else -> 1.0f
        },
        animationSpec = tween(durationMillis = 120),
        label = "shutter_scale"
    )

    val innerScale by animateFloatAsState(
        targetValue = when {
            isCapturing -> 0.85f
            isPressed -> 0.90f
            else -> 1.0f
        },
        animationSpec = tween(durationMillis = 120),
        label = "shutter_inner_scale"
    )

    Box(
        modifier = modifier
            .size(76.dp)
            .scale(scale)
            .testTag("shutter_button")
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isCapturing
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        // Outer Frosted Glass Ring
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.9f),
                            Color.White.copy(alpha = 0.5f)
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Inner Shutter Core
        Box(
            modifier = Modifier
                .size(60.dp)
                .scale(innerScale)
                .clip(CircleShape)
                .background(
                    if (isCapturing) Color.White.copy(alpha = 0.6f) else Color.White
                )
        )
    }
}

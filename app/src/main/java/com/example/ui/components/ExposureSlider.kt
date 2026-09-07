package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Slim vertical glassmorphic exposure slider.
 * Allows adjusting the camera's exposure index with live preview updates.
 * Perfectly aligned with the active camera viewfinder area.
 */
@Composable
fun ExposureSlider(
    exposureIndex: Int,
    minExposure: Int,
    maxExposure: Int,
    onExposureChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    // Ensure safe bounds even on emulators where camera hardware exposure is 0..0
    val effectiveMin = if (minExposure < maxExposure) minExposure else -4
    val effectiveMax = if (minExposure < maxExposure) maxExposure else 4
    val totalRange = (effectiveMax - effectiveMin).toFloat().coerceAtLeast(1f)

    // Current normalized fraction between 0f (min/bottom) and 1f (max/top)
    val clampedIndex = exposureIndex.coerceIn(effectiveMin, effectiveMax)
    val normalizedExposure = ((clampedIndex - effectiveMin) / totalRange).coerceIn(0f, 1f)

    val currentExposure by rememberUpdatedState(clampedIndex)
    val onExposureChangeState by rememberUpdatedState(onExposureChange)
    val haptic = LocalHapticFeedback.current

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        GlassPanel(
            shape = RoundedCornerShape(22.dp),
            elevation = 8.dp,
            modifier = Modifier
                .width(42.dp)
                .height(180.dp)
                .testTag("exposure_slider")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                // Sun Icon at top with exposure accent color
                val hasAdjusted = currentExposure != 0
                val activeAccent = Color(0xFFFFD54F)
                Icon(
                    imageVector = Icons.Rounded.WbSunny,
                    contentDescription = "Exposure",
                    tint = if (hasAdjusted) activeAccent else Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Vertical Slider Track with Fluid Drag and Tap Gestures
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(effectiveMin, effectiveMax) {
                            detectTapGestures(
                                onPress = { offset ->
                                    val topY = 8.dp.toPx()
                                    val bottomY = size.height - 8.dp.toPx()
                                    val usableHeight = (bottomY - topY).coerceAtLeast(1f)
                                    val fraction = ((bottomY - offset.y) / usableHeight).coerceIn(0f, 1f)
                                    val targetIndex = (effectiveMin + fraction * totalRange).roundToInt().coerceIn(effectiveMin, effectiveMax)
                                    if (targetIndex != currentExposure) {
                                        onExposureChangeState(targetIndex)
                                    }
                                }
                            )
                        }
                        .pointerInput(effectiveMin, effectiveMax) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val topY = 8.dp.toPx()
                                    val bottomY = size.height - 8.dp.toPx()
                                    val usableHeight = (bottomY - topY).coerceAtLeast(1f)
                                    val fraction = ((bottomY - offset.y) / usableHeight).coerceIn(0f, 1f)
                                    val targetIndex = (effectiveMin + fraction * totalRange).roundToInt().coerceIn(effectiveMin, effectiveMax)
                                    if (targetIndex != currentExposure) {
                                        onExposureChangeState(targetIndex)
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val topY = 8.dp.toPx()
                                    val bottomY = size.height - 8.dp.toPx()
                                    val usableHeight = (bottomY - topY).coerceAtLeast(1f)
                                    val fraction = ((bottomY - change.position.y) / usableHeight).coerceIn(0f, 1f)
                                    val targetIndex = (effectiveMin + fraction * totalRange).roundToInt().coerceIn(effectiveMin, effectiveMax)
                                    if (targetIndex != currentExposure) {
                                        onExposureChangeState(targetIndex)
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth()
                    ) {
                        val centerX = size.width / 2f
                        val topY = 8.dp.toPx()
                        val bottomY = size.height - 8.dp.toPx()
                        val usableHeight = (bottomY - topY).coerceAtLeast(1f)

                        // Neutral (0 EV) Center notch location
                        val zeroFraction = ((0 - effectiveMin).toFloat() / totalRange).coerceIn(0f, 1f)
                        val zeroY = bottomY - (zeroFraction * usableHeight)

                        // Thumb position
                        val thumbY = bottomY - (normalizedExposure * usableHeight)

                        // 1. Background full track line
                        drawLine(
                            color = Color.White.copy(alpha = 0.22f),
                            start = Offset(centerX, topY),
                            end = Offset(centerX, bottomY),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // 2. Active colored track line (between 0 EV neutral notch and active thumb)
                        if (hasAdjusted) {
                            drawLine(
                                color = activeAccent.copy(alpha = 0.85f),
                                start = Offset(centerX, zeroY),
                                end = Offset(centerX, thumbY),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        // 3. Neutral center reference mark (0 EV)
                        drawLine(
                            color = Color.White.copy(alpha = 0.65f),
                            start = Offset(centerX - 5.dp.toPx(), zeroY),
                            end = Offset(centerX + 5.dp.toPx(), zeroY),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // 4. Glowing halo behind active thumb
                        drawCircle(
                            color = activeAccent.copy(alpha = 0.35f),
                            radius = 7.5.dp.toPx(),
                            center = Offset(centerX, thumbY)
                        )

                        // 5. Solid center thumb
                        drawCircle(
                            color = Color.White,
                            radius = 4.5.dp.toPx(),
                            center = Offset(centerX, thumbY)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // EV Readout at bottom
                val evText = when {
                    currentExposure > 0 -> "+$currentExposure"
                    currentExposure < 0 -> "$currentExposure"
                    else -> "0"
                }
                Text(
                    text = evText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hasAdjusted) activeAccent else Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

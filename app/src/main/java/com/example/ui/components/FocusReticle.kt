package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.camera.FocusState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Animated tap-to-focus reticle synchronized with actual camera focus completion.
 * - Scales down gracefully while focusing.
 * - Snaps with spring feedback upon focus lock.
 * - Gently fades out after focus lock or cancellation.
 */
@Composable
fun FocusReticle(
    focusPoint: Offset?,
    triggerId: Long,
    focusState: FocusState = FocusState.IDLE,
    modifier: Modifier = Modifier
) {
    if (focusPoint == null || triggerId == 0L) return

    val scale = remember(triggerId) { Animatable(1.35f) }
    val alpha = remember(triggerId) { Animatable(1.0f) }

    // Initial contraction on tap
    LaunchedEffect(triggerId) {
        scale.snapTo(1.35f)
        alpha.snapTo(1.0f)

        // Smooth contraction toward target size during active focus hunting
        scale.animateTo(
            targetValue = 1.05f,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
        )
    }

    // Reaction when camera hardware completes focus (LOCKED or FAILED)
    LaunchedEffect(triggerId, focusState) {
        when (focusState) {
            FocusState.LOCKED -> {
                // Crisply lock onto the focused target with subtle spring affirmation
                scale.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
                // Hold briefly on locked target so user clearly sees sharp confirmation
                delay(800)
                // Smooth fade out
                alpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 300, easing = LinearEasing)
                )
            }
            FocusState.FAILED -> {
                delay(400)
                alpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 250, easing = LinearEasing)
                )
            }
            FocusState.FOCUSING -> {
                // If focus cycle takes longer than 3 seconds without callback, gracefully timeout
                delay(3200)
                if (alpha.value > 0f) {
                    alpha.animateTo(0f, tween(300))
                }
            }
            FocusState.IDLE -> Unit
        }
    }

    val reticleSize = 68.dp

    Box(
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (focusPoint.x - (reticleSize.toPx() * scale.value) / 2f).roundToInt(),
                        y = (focusPoint.y - (reticleSize.toPx() * scale.value) / 2f).roundToInt()
                    )
                }
                .size(reticleSize * scale.value)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val currentAlpha = alpha.value
                if (currentAlpha <= 0.01f) return@Canvas

                val strokeWidth = 1.5.dp.toPx()
                // Golden camera yellow for locked/focusing, subtle warm tint
                val baseColor = when (focusState) {
                    FocusState.FAILED -> Color(0xFFFF8A80)
                    else -> Color(0xFFFFD54F)
                }
                val reticleColor = baseColor.copy(alpha = currentAlpha)
                val cornerLength = 14.dp.toPx()
                val w = size.width
                val h = size.height

                // 4 Sleek Corner Brackets
                // Top-Left
                drawLine(reticleColor, Offset(0f, 0f), Offset(cornerLength, 0f), strokeWidth, StrokeCap.Round)
                drawLine(reticleColor, Offset(0f, 0f), Offset(0f, cornerLength), strokeWidth, StrokeCap.Round)

                // Top-Right
                drawLine(reticleColor, Offset(w, 0f), Offset(w - cornerLength, 0f), strokeWidth, StrokeCap.Round)
                drawLine(reticleColor, Offset(w, 0f), Offset(w, cornerLength), strokeWidth, StrokeCap.Round)

                // Bottom-Left
                drawLine(reticleColor, Offset(0f, h), Offset(cornerLength, h), strokeWidth, StrokeCap.Round)
                drawLine(reticleColor, Offset(0f, h), Offset(0f, h - cornerLength), strokeWidth, StrokeCap.Round)

                // Bottom-Right
                drawLine(reticleColor, Offset(w, h), Offset(w - cornerLength, h), strokeWidth, StrokeCap.Round)
                drawLine(reticleColor, Offset(w, h), Offset(w, h - cornerLength), strokeWidth, StrokeCap.Round)

                // Center subtle indicator
                drawCircle(
                    color = reticleColor.copy(alpha = currentAlpha * 0.75f),
                    radius = 2.dp.toPx(),
                    center = Offset(w / 2f, h / 2f)
                )
            }
        }
    }
}


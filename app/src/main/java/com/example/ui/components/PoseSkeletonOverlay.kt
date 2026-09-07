package com.example.ui.components

import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.camera.pose.PoseSkeleton
import kotlin.math.min

/**
 * Translucent canvas skeleton overlay rendering reference posture guide lines.
 * Scaled and mapped dynamically to the camera viewfinder.
 * - Aligned limbs glow in vibrant emerald green.
 * - Pending/adjusting limbs render in luminous gold or warm amber.
 * - Glowing circular nodes at all key joint positions.
 */
@Composable
fun PoseSkeletonOverlay(
    targetSkeleton: PoseSkeleton?,
    alignedLimbs: Set<String>,
    isMatched: Boolean,
    noPoseDetected: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (targetSkeleton == null || targetSkeleton.keypoints.isEmpty()) return

    // Subtle breathing pulse for guide lines
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val matchGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "match_glow"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val kpMap = targetSkeleton.keypoints.associateBy { it.name }

        fun toScreen(x: Float, y: Float): Offset {
            return Offset(x * w, y * h)
        }

        // Standard limb connections mapped to their joint names
        val connections = listOf(
            // Shoulders & Torso
            Triple("left_shoulder", "right_shoulder", "shoulders"),
            Triple("left_shoulder", "left_hip", "torso_left"),
            Triple("right_shoulder", "right_hip", "torso_right"),
            Triple("left_hip", "right_hip", "hips"),
            // Left Arm
            Triple("left_shoulder", "left_elbow", "left_shoulder"),
            Triple("left_elbow", "left_wrist", "left_elbow"),
            // Right Arm
            Triple("right_shoulder", "right_elbow", "right_shoulder"),
            Triple("right_elbow", "right_wrist", "right_elbow"),
            // Left Leg
            Triple("left_hip", "left_knee", "left_hip"),
            Triple("left_knee", "left_ankle", "left_knee"),
            // Right Leg
            Triple("right_hip", "right_knee", "right_hip"),
            Triple("right_knee", "right_ankle", "right_knee")
        )

        val defaultLineColor = Color(0xFFFFD54F) // Golden posture guide (idle/no pose)
        val alignedColor = Color(0xFF00E676)     // Emerald green (aligned)
        val misalignedColor = Color(0xFFFF5252)  // Coral red (active & misaligned)
        val torsoAmber = Color(0xFFFFB74D)       // Soft amber (transitional torso)

        // 1. Draw connecting skeleton bones with per-limb real-time coloring
        connections.forEach { (p1Name, p2Name, jointKey) ->
            val p1 = kpMap[p1Name]
            val p2 = kpMap[p2Name]

            if (p1 != null && p2 != null) {
                val start = toScreen(p1.x, p1.y)
                val end = toScreen(p2.x, p2.y)

                val isAligned = when (jointKey) {
                    "shoulders" -> alignedLimbs.contains("left_shoulder") || alignedLimbs.contains("right_shoulder")
                    "hips" -> alignedLimbs.contains("left_knee") || alignedLimbs.contains("right_knee")
                    "torso_left" -> alignedLimbs.contains("left_shoulder")
                    "torso_right" -> alignedLimbs.contains("right_shoulder")
                    else -> alignedLimbs.contains(jointKey)
                }

                val baseColor = when {
                    noPoseDetected -> defaultLineColor
                    isMatched -> alignedColor
                    isAligned -> alignedColor
                    jointKey == "shoulders" || jointKey == "hips" -> torsoAmber
                    else -> misalignedColor
                }

                val currentAlpha = when {
                    isMatched || isAligned -> matchGlowAlpha
                    noPoseDetected -> pulseAlpha
                    else -> 0.85f
                }

                // Outer soft glow line for visual depth
                drawLine(
                    color = baseColor.copy(alpha = currentAlpha * 0.35f),
                    start = start,
                    end = end,
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Inner crisp bone line
                drawLine(
                    color = baseColor.copy(alpha = currentAlpha * 0.95f),
                    start = start,
                    end = end,
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // 2. Draw glowing joint nodes
        targetSkeleton.keypoints.forEach { kp ->
            val center = toScreen(kp.x, kp.y)
            val isHead = kp.name.contains("nose") || kp.name.contains("eye") || kp.name.contains("ear")
            val radius = if (isHead) 3.5.dp.toPx() else 5.dp.toPx()

            val isJointAligned = alignedLimbs.contains(kp.name) ||
                (kp.name == "left_elbow" && alignedLimbs.contains("left_elbow")) ||
                (kp.name == "right_elbow" && alignedLimbs.contains("right_elbow")) ||
                (kp.name == "left_shoulder" && alignedLimbs.contains("left_shoulder")) ||
                (kp.name == "right_shoulder" && alignedLimbs.contains("right_shoulder")) ||
                (kp.name == "left_knee" && alignedLimbs.contains("left_knee")) ||
                (kp.name == "right_knee" && alignedLimbs.contains("right_knee"))

            val nodeColor = when {
                noPoseDetected -> defaultLineColor
                isMatched -> alignedColor
                isJointAligned -> alignedColor
                else -> misalignedColor
            }

            // Outer node glow halo
            drawCircle(
                color = nodeColor.copy(alpha = 0.35f),
                radius = radius * 1.8f,
                center = center
            )

            // Solid inner node
            drawCircle(
                color = Color.White.copy(alpha = 0.95f),
                radius = radius * 0.7f,
                center = center
            )

            // Dynamic color ring
            drawCircle(
                color = nodeColor,
                radius = radius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}

/**
 * Lightweight vector canvas component that draws a pose skeleton outline as a thumbnail.
 * Used in PoseSelectionSheet instead of reference photos to display clean stick-figure outlines.
 */
@Composable
fun PoseSkeletonThumbnail(
    skeleton: PoseSkeleton,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    lineColor: Color = if (isSelected) Color(0xFF00E676) else Color(0xFFFFD54F)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0 || skeleton.keypoints.isEmpty()) return@Canvas

        // Calculate bounding box to fit skeleton nicely inside the thumbnail
        val xs = skeleton.keypoints.map { it.x }
        val ys = skeleton.keypoints.map { it.y }
        val minX = xs.minOrNull() ?: 0f
        val maxX = xs.maxOrNull() ?: 1f
        val minY = ys.minOrNull() ?: 0f
        val maxY = ys.maxOrNull() ?: 1f

        val poseW = (maxX - minX).coerceAtLeast(0.1f)
        val poseH = (maxY - minY).coerceAtLeast(0.1f)

        // Padding within thumbnail
        val pad = 10.dp.toPx()
        val availW = (w - pad * 2).coerceAtLeast(1f)
        val availH = (h - pad * 2).coerceAtLeast(1f)
        val scale = min(availW / poseW, availH / poseH)

        val offsetX = pad + (availW - poseW * scale) / 2f
        val offsetY = pad + (availH - poseH * scale) / 2f

        fun toThumbScreen(x: Float, y: Float): Offset {
            return Offset(
                x = offsetX + (x - minX) * scale,
                y = offsetY + (y - minY) * scale
            )
        }

        val kpMap = skeleton.keypoints.associateBy { it.name }

        val connections = listOf(
            Triple("left_shoulder", "right_shoulder", "shoulders"),
            Triple("left_shoulder", "left_hip", "torso_left"),
            Triple("right_shoulder", "right_hip", "torso_right"),
            Triple("left_hip", "right_hip", "hips"),
            Triple("left_shoulder", "left_elbow", "left_upper_arm"),
            Triple("left_elbow", "left_wrist", "left_forearm"),
            Triple("right_shoulder", "right_elbow", "right_upper_arm"),
            Triple("right_elbow", "right_wrist", "right_forearm"),
            Triple("left_hip", "left_knee", "left_thigh"),
            Triple("left_knee", "left_ankle", "left_shin"),
            Triple("right_hip", "right_knee", "right_thigh"),
            Triple("right_knee", "right_ankle", "right_shin")
        )

        // Draw connections
        val strokeWidth = if (isSelected) 3.dp.toPx() else 2.dp.toPx()
        connections.forEach { (p1, p2, _) ->
            val k1 = kpMap[p1]
            val k2 = kpMap[p2]
            if (k1 != null && k2 != null) {
                drawLine(
                    color = lineColor,
                    start = toThumbScreen(k1.x, k1.y),
                    end = toThumbScreen(k2.x, k2.y),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }

        // Draw head
        val nose = kpMap["nose"]
        val leftEar = kpMap["left_ear"]
        val rightEar = kpMap["right_ear"]
        val headCenter = when {
            nose != null -> toThumbScreen(nose.x, nose.y)
            leftEar != null && rightEar != null -> Offset(
                (toThumbScreen(leftEar.x, leftEar.y).x + toThumbScreen(rightEar.x, rightEar.y).x) / 2f,
                (toThumbScreen(leftEar.x, leftEar.y).y + toThumbScreen(rightEar.x, rightEar.y).y) / 2f
            )
            else -> null
        }

        if (headCenter != null) {
            val headRadius = (scale * 0.055f).coerceIn(4.dp.toPx(), 9.dp.toPx())
            drawCircle(
                color = lineColor,
                radius = headRadius,
                center = headCenter,
                style = Stroke(width = strokeWidth)
            )
        }

        // Draw joint points
        skeleton.keypoints.forEach { kp ->
            val isHead = kp.name.contains("nose") || kp.name.contains("eye") || kp.name.contains("ear")
            if (!isHead) {
                val pt = toThumbScreen(kp.x, kp.y)
                drawCircle(
                    color = Color.White,
                    radius = (strokeWidth * 0.85f).coerceAtLeast(1.8.dp.toPx()),
                    center = pt
                )
            }
        }
    }
}

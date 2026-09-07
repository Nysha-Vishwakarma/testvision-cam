package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.FaceLandmarkData
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Enhanced "Smart Grid" Overlay with Real-Time Facial Coordinate System.
 *
 * Implements the exact facial coordinate measurement HUD shown in the reference:
 * 1. 3x3 Rule-of-Thirds composition lines and power point intersection crosshairs.
 * 2. Cyan X-Axis and Y-Axis centered at the Nose Tip origin (0, 0) with directional arrows and axis labels.
 * 3. Real-time Cartesian coordinate readouts:
 *    - Nose Tip (0, 0)
 *    - Left Eye (-40, 60) [dynamic relative coordinates]
 *    - Right Eye (40, 60) [dynamic relative coordinates]
 *    - Left Mouth Corner (-30, -40) [dynamic relative coordinates]
 *    - Right Mouth Corner (30, -40) [dynamic relative coordinates]
 * 4. Facial contour & feature landmark points (jawline, eyebrows, eyes, nose, lips).
 * 5. Subtle golden geometric tangent guidelines and eyebrow dimension cues.
 * 6. Glassmorphism backing pills and high-contrast typography matching the app's modern pro aesthetic.
 */
@Composable
fun GridOverlay(
    isVisible: Boolean,
    faceLandmarks: FaceLandmarkData? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // Native Paints for high-FPS text rendering with shadow backing
    val titlePaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.parseColor("#38BDF8") // Soft Sky / Cyan
            textSize = with(density) { 12.sp.toPx() }
            isAntiAlias = true
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setShadowLayer(6f, 1f, 1f, android.graphics.Color.parseColor("#CC000000"))
        }
    }

    val coordPaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.parseColor("#FBBF24") // Warm Amber / Gold
            textSize = with(density) { 11.sp.toPx() }
            isAntiAlias = true
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setShadowLayer(6f, 1f, 1f, android.graphics.Color.parseColor("#CC000000"))
        }
    }

    val axisLabelPaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.parseColor("#38BDF8")
            textSize = with(density) { 14.sp.toPx() }
            isAntiAlias = true
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setShadowLayer(6f, 1f, 1f, android.graphics.Color.parseColor("#CC000000"))
        }
    }

    val annotationPaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.parseColor("#FBBF24")
            textSize = with(density) { 9.sp.toPx() }
            isAntiAlias = true
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setShadowLayer(4f, 1f, 1f, android.graphics.Color.parseColor("#B3000000"))
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val gridColor = Color.White.copy(alpha = 0.25f)
            val shadowColor = Color.Black.copy(alpha = 0.22f)
            val strokeWidth = 1.dp.toPx()

            val x1 = width / 3f
            val x2 = (width * 2f) / 3f
            val y1 = height / 3f
            val y2 = (height * 2f) / 3f

            // ==========================================
            // 1. BASE RULE-OF-THIRDS GRID LINES
            // ==========================================
            // Shadow contrast lines behind
            drawLine(color = shadowColor, start = Offset(x1 + 1f, 0f), end = Offset(x1 + 1f, height), strokeWidth = strokeWidth)
            drawLine(color = shadowColor, start = Offset(x2 + 1f, 0f), end = Offset(x2 + 1f, height), strokeWidth = strokeWidth)
            drawLine(color = shadowColor, start = Offset(0f, y1 + 1f), end = Offset(width, y1 + 1f), strokeWidth = strokeWidth)
            drawLine(color = shadowColor, start = Offset(0f, y2 + 1f), end = Offset(width, y2 + 1f), strokeWidth = strokeWidth)

            // Primary crisp grid lines
            drawLine(color = gridColor, start = Offset(x1, 0f), end = Offset(x1, height), strokeWidth = strokeWidth)
            drawLine(color = gridColor, start = Offset(x2, 0f), end = Offset(x2, height), strokeWidth = strokeWidth)
            drawLine(color = gridColor, start = Offset(0f, y1), end = Offset(width, y1), strokeWidth = strokeWidth)
            drawLine(color = gridColor, start = Offset(0f, y2), end = Offset(width, y2), strokeWidth = strokeWidth)

            // Power point intersections
            val intersections = listOf(
                Offset(x1, y1),
                Offset(x2, y1),
                Offset(x1, y2),
                Offset(x2, y2)
            )

            // ==========================================
            // 2. REAL-TIME FACE COORDINATE SYSTEM
            // ==========================================
            val hasFace = faceLandmarks != null && faceLandmarks.isFaceDetected
            var activeIntersectionIndex = -1

            if (hasFace && faceLandmarks != null) {
                val leftEyeNorm = faceLandmarks.leftEye
                val rightEyeNorm = faceLandmarks.rightEye
                val noseNorm = faceLandmarks.noseBase
                val mouthLeftNorm = faceLandmarks.mouthLeft
                val mouthRightNorm = faceLandmarks.mouthRight

                // Pixel coordinates on the screen canvas
                val leftEyePx = if (leftEyeNorm != null) Offset(leftEyeNorm.x * width, leftEyeNorm.y * height) else null
                val rightEyePx = if (rightEyeNorm != null) Offset(rightEyeNorm.x * width, rightEyeNorm.y * height) else null
                val nosePx = if (noseNorm != null) {
                    Offset(noseNorm.x * width, noseNorm.y * height)
                } else if (leftEyePx != null && rightEyePx != null) {
                    Offset((leftEyePx.x + rightEyePx.x) / 2f, (leftEyePx.y + rightEyePx.y) / 2f + 40.dp.toPx())
                } else {
                    Offset(width / 2f, height / 2f)
                }

                val mouthLeftPx = if (mouthLeftNorm != null) Offset(mouthLeftNorm.x * width, mouthLeftNorm.y * height) else null
                val mouthRightPx = if (mouthRightNorm != null) Offset(mouthRightNorm.x * width, mouthRightNorm.y * height) else null

                // Fallback estimated points if any landmark is temporarily occluded
                val effectiveEyeDist = if (leftEyePx != null && rightEyePx != null) {
                    hypot(rightEyePx.x - leftEyePx.x, rightEyePx.y - leftEyePx.y).coerceAtLeast(40.dp.toPx())
                } else {
                    faceLandmarks.getInterEyeDistance(width, height).coerceAtLeast(40.dp.toPx())
                }

                val actualLeftEyePx = leftEyePx ?: Offset(nosePx.x - effectiveEyeDist / 2f, nosePx.y - effectiveEyeDist * 0.75f)
                val actualRightEyePx = rightEyePx ?: Offset(nosePx.x + effectiveEyeDist / 2f, nosePx.y - effectiveEyeDist * 0.75f)
                val actualMouthLeftPx = mouthLeftPx ?: Offset(nosePx.x - effectiveEyeDist * 0.38f, nosePx.y + effectiveEyeDist * 0.5f)
                val actualMouthRightPx = mouthRightPx ?: Offset(nosePx.x + effectiveEyeDist * 0.38f, nosePx.y + effectiveEyeDist * 0.5f)

                // Calibration scale matching reference image: eye-to-eye distance equals 80 units (-40 to +40)
                val scale = 80f / effectiveEyeDist
                val originX = nosePx.x
                val originY = nosePx.y

                // Real-time Cartesian coordinate calculation relative to Nose Tip (0, 0)
                // Cartesian X positive -> Right, Cartesian Y positive -> Up (so negative deltaY)
                fun calculateCoordinates(pt: Offset): Pair<Int, Int> {
                    val dx = pt.x - originX
                    val dy = pt.y - originY
                    val cx = (dx * scale).roundToInt()
                    val cy = (-dy * scale).roundToInt()
                    return Pair(cx, cy)
                }

                val (leftEyeCoordX, leftEyeCoordY) = calculateCoordinates(actualLeftEyePx)
                val (rightEyeCoordX, rightEyeCoordY) = calculateCoordinates(actualRightEyePx)
                val (mouthLeftCoordX, mouthLeftCoordY) = calculateCoordinates(actualMouthLeftPx)
                val (mouthRightCoordX, mouthRightCoordY) = calculateCoordinates(actualMouthRightPx)

                // Proximity check to Rule-of-Thirds power points
                val faceCenterX = (actualLeftEyePx.x + actualRightEyePx.x) / 2f
                val faceCenterY = nosePx.y
                val thresholdDist = width * 0.16f
                var minDistance = Float.MAX_VALUE
                intersections.forEachIndexed { idx, pt ->
                    val dist = hypot(faceCenterX - pt.x, faceCenterY - pt.y)
                    if (dist < thresholdDist && dist < minDistance) {
                        minDistance = dist
                        activeIntersectionIndex = idx
                    }
                }

                // ====================================================
                // 2A. CYAN X & Y AXES WITH DIRECTIONAL ARROWS
                // ====================================================
                val axisColor = Color(0xFF00B4D8)
                val axisShadowColor = Color.Black.copy(alpha = 0.45f)
                val axisStrokeWidth = 1.6.dp.toPx()

                val faceSpan = effectiveEyeDist * 2.8f
                val yTop = (originY - faceSpan * 1.15f).coerceAtLeast(24.dp.toPx())
                val yBottom = (originY + faceSpan * 0.85f).coerceAtMost(height - 20.dp.toPx())
                val xLeft = (originX - faceSpan * 0.95f).coerceAtLeast(16.dp.toPx())
                val xRight = (originX + faceSpan * 0.95f).coerceAtMost(width - 24.dp.toPx())

                // Shadow for vertical Y-axis
                drawLine(
                    color = axisShadowColor,
                    start = Offset(originX + 1f, yTop),
                    end = Offset(originX + 1f, yBottom),
                    strokeWidth = axisStrokeWidth
                )
                // Vibrant Cyan Y-Axis
                drawLine(
                    color = axisColor,
                    start = Offset(originX, yTop),
                    end = Offset(originX, yBottom),
                    strokeWidth = axisStrokeWidth
                )

                // Shadow for horizontal X-axis
                drawLine(
                    color = axisShadowColor,
                    start = Offset(xLeft, originY + 1f),
                    end = Offset(xRight, originY + 1f),
                    strokeWidth = axisStrokeWidth
                )
                // Vibrant Cyan X-Axis
                drawLine(
                    color = axisColor,
                    start = Offset(xLeft, originY),
                    end = Offset(xRight, originY),
                    strokeWidth = axisStrokeWidth
                )

                // Y-Axis Arrowhead at top pointing UP
                val arrowSize = 9.dp.toPx()
                val yArrowPath = Path().apply {
                    moveTo(originX, yTop - arrowSize)
                    lineTo(originX - arrowSize * 0.6f, yTop + 2.dp.toPx())
                    lineTo(originX + arrowSize * 0.6f, yTop + 2.dp.toPx())
                    close()
                }
                drawPath(yArrowPath, color = axisColor)

                // X-Axis Arrowhead at right pointing RIGHT
                val xArrowPath = Path().apply {
                    moveTo(xRight + arrowSize, originY)
                    lineTo(xRight - 2.dp.toPx(), originY - arrowSize * 0.6f)
                    lineTo(xRight - 2.dp.toPx(), originY + arrowSize * 0.6f)
                    close()
                }
                drawPath(xArrowPath, color = axisColor)

                // ====================================================
                // 2B. FACIAL CONTOUR & FEATURE LANDMARK DOTS
                // ====================================================
                val dotRadius = 3.dp.toPx()
                val dotColor = Color.White
                val dotGlowColor = Color.Black.copy(alpha = 0.5f)

                // Collect all contour points from ML Kit or generate authentic face mesh curve
                val landmarkPoints = mutableListOf<Offset>()

                if (faceLandmarks.faceContour.isNotEmpty()) {
                    faceLandmarks.faceContour.forEach { pt ->
                        landmarkPoints.add(Offset(pt.x * width, pt.y * height))
                    }
                } else {
                    // Smooth 17-point jawline contour curve
                    val jawRadiusX = effectiveEyeDist * 1.35f
                    val jawRadiusY = effectiveEyeDist * 1.75f
                    val steps = 17
                    for (i in 0..steps) {
                        val theta = Math.PI * (0.08 + (0.84 * i / steps))
                        val px = originX - (jawRadiusX * cos(theta)).toFloat()
                        val py = (actualLeftEyePx.y + jawRadiusY * sin(theta)).toFloat()
                        landmarkPoints.add(Offset(px, py))
                    }
                }

                // Eyebrow points
                if (faceLandmarks.leftEyebrowContour.isNotEmpty()) {
                    faceLandmarks.leftEyebrowContour.forEach { pt ->
                        landmarkPoints.add(Offset(pt.x * width, pt.y * height))
                    }
                } else {
                    for (i in 0..4) {
                        val t = i / 4f
                        val px = actualLeftEyePx.x - (effectiveEyeDist * 0.35f) + (t * effectiveEyeDist * 0.7f)
                        val py = actualLeftEyePx.y - 22.dp.toPx() - (sin(t * Math.PI) * 8.dp.toPx()).toFloat()
                        landmarkPoints.add(Offset(px, py))
                    }
                }

                if (faceLandmarks.rightEyebrowContour.isNotEmpty()) {
                    faceLandmarks.rightEyebrowContour.forEach { pt ->
                        landmarkPoints.add(Offset(pt.x * width, pt.y * height))
                    }
                } else {
                    for (i in 0..4) {
                        val t = i / 4f
                        val px = actualRightEyePx.x - (effectiveEyeDist * 0.35f) + (t * effectiveEyeDist * 0.7f)
                        val py = actualRightEyePx.y - 22.dp.toPx() - (sin(t * Math.PI) * 8.dp.toPx()).toFloat()
                        landmarkPoints.add(Offset(px, py))
                    }
                }

                // Additional eyelid / lip / nose dots
                val featureDots = listOf(
                    Offset(actualLeftEyePx.x - 14.dp.toPx(), actualLeftEyePx.y),
                    Offset(actualLeftEyePx.x + 14.dp.toPx(), actualLeftEyePx.y),
                    Offset(actualRightEyePx.x - 14.dp.toPx(), actualRightEyePx.y),
                    Offset(actualRightEyePx.x + 14.dp.toPx(), actualRightEyePx.y),
                    Offset(originX, originY - 18.dp.toPx()),
                    Offset(originX, originY + 12.dp.toPx()),
                    Offset(originX - 10.dp.toPx(), originY + 8.dp.toPx()),
                    Offset(originX + 10.dp.toPx(), originY + 8.dp.toPx()),
                    Offset(originX, actualMouthLeftPx.y - 6.dp.toPx()),
                    Offset(originX, actualMouthLeftPx.y + 8.dp.toPx())
                )
                landmarkPoints.addAll(featureDots)

                // Draw all landmark dots with soft backing halo
                landmarkPoints.forEach { pt ->
                    drawCircle(color = dotGlowColor, radius = dotRadius + 1.2f, center = pt)
                    drawCircle(color = dotColor, radius = dotRadius, center = pt)
                }

                // ====================================================
                // 2C. GOLD CONNECTING LINES & ANNOTATIONS
                // ====================================================
                val goldLineColor = Color(0xFFF59E0B).copy(alpha = 0.75f)
                val goldThinStroke = 1.2.dp.toPx()

                // Left mouth corner to jaw contour anchor line
                val leftJawAnchor = Offset(xLeft + 18.dp.toPx(), actualMouthLeftPx.y + 12.dp.toPx())
                drawLine(
                    color = goldLineColor,
                    start = actualMouthLeftPx,
                    end = leftJawAnchor,
                    strokeWidth = goldThinStroke,
                    cap = StrokeCap.Round
                )

                // Right mouth corner to jaw contour anchor line
                val rightJawAnchor = Offset(xRight - 18.dp.toPx(), actualMouthRightPx.y + 12.dp.toPx())
                drawLine(
                    color = goldLineColor,
                    start = actualMouthRightPx,
                    end = rightJawAnchor,
                    strokeWidth = goldThinStroke,
                    cap = StrokeCap.Round
                )

                // Eye corner to temple anchor line
                val leftTempleAnchor = Offset(xLeft + 22.dp.toPx(), actualLeftEyePx.y - 12.dp.toPx())
                drawLine(
                    color = goldLineColor,
                    start = Offset(actualLeftEyePx.x - 14.dp.toPx(), actualLeftEyePx.y),
                    end = leftTempleAnchor,
                    strokeWidth = goldThinStroke,
                    cap = StrokeCap.Round
                )

                // ====================================================
                // 2D. KEY FEATURE DOTS & REAL-TIME CALLOUTS
                // ====================================================
                // Origin (Nose Tip)
                drawCircle(color = Color(0x6638BDF8), radius = 7.dp.toPx(), center = nosePx)
                drawCircle(color = Color.White, radius = 4.5.dp.toPx(), center = nosePx)

                // Left Eye Dot
                drawCircle(color = Color(0x6638BDF8), radius = 6.dp.toPx(), center = actualLeftEyePx)
                drawCircle(color = Color.White, radius = 3.8.dp.toPx(), center = actualLeftEyePx)

                // Right Eye Dot
                drawCircle(color = Color(0x6638BDF8), radius = 6.dp.toPx(), center = actualRightEyePx)
                drawCircle(color = Color.White, radius = 3.8.dp.toPx(), center = actualRightEyePx)

                // Left Mouth Dot
                drawCircle(color = Color.White, radius = 4.dp.toPx(), center = actualMouthLeftPx)

                // Right Mouth Dot
                drawCircle(color = Color.White, radius = 4.dp.toPx(), center = actualMouthRightPx)

                // Helper to render glassmorphism pill with two lines of text (Title & Coordinates)
                drawIntoCanvas { canvas ->
                    val nativeCanvas = canvas.nativeCanvas

                    // Axis labels
                    nativeCanvas.drawText("Y", originX - 16.dp.toPx(), yTop + 2.dp.toPx(), axisLabelPaint)
                    nativeCanvas.drawText("X", xRight - 4.dp.toPx(), originY + 18.dp.toPx(), axisLabelPaint)

                    // Dimension notes near brows
                    nativeCanvas.drawText("10mm", actualLeftEyePx.x - 24.dp.toPx(), actualLeftEyePx.y - 34.dp.toPx(), annotationPaint)
                    nativeCanvas.drawText("↑", actualRightEyePx.x + 18.dp.toPx(), actualRightEyePx.y - 34.dp.toPx(), annotationPaint)

                    fun drawFeatureCallout(
                        title: String,
                        coordString: String,
                        anchorX: Float,
                        anchorY: Float,
                        isRightSide: Boolean,
                        yOffset: Float = 0f
                    ) {
                        val titleWidth = titlePaint.measureText(title)
                        val coordWidth = coordPaint.measureText(coordString)
                        val contentWidth = maxOf(titleWidth, coordWidth)

                        val pillPaddingH = 8.dp.toPx()
                        val pillPaddingV = 4.dp.toPx()
                        val pillWidth = contentWidth + pillPaddingH * 2f
                        val pillHeight = with(density) { 30.sp.toPx() }

                        val left = if (isRightSide) {
                            anchorX + 10.dp.toPx()
                        } else {
                            anchorX - 10.dp.toPx() - pillWidth
                        }
                        val top = anchorY + yOffset - (pillHeight / 2f)

                        // Subtle Glassmorphism Pill Background
                        drawRoundRect(
                            color = Color(0xD90F172A),
                            topLeft = Offset(left, top),
                            size = Size(pillWidth, pillHeight),
                            cornerRadius = CornerRadius(6.dp.toPx())
                        )
                        drawRoundRect(
                            color = Color(0x4038BDF8),
                            topLeft = Offset(left, top),
                            size = Size(pillWidth, pillHeight),
                            cornerRadius = CornerRadius(6.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx())
                        )

                        val textX = left + pillPaddingH
                        val titleY = top + pillPaddingV + with(density) { 10.sp.toPx() }
                        val coordY = titleY + with(density) { 13.sp.toPx() }

                        nativeCanvas.drawText(title, textX, titleY, titlePaint)
                        nativeCanvas.drawText(coordString, textX, coordY, coordPaint)
                    }

                    // 1. Nose Tip (0, 0)
                    drawFeatureCallout(
                        title = "Nose Tip",
                        coordString = "(0, 0)",
                        anchorX = originX,
                        anchorY = originY,
                        isRightSide = true,
                        yOffset = 18.dp.toPx()
                    )

                    // 2. Left Eye (-40, 60) [dynamic]
                    drawFeatureCallout(
                        title = "Left Eye",
                        coordString = "($leftEyeCoordX, $leftEyeCoordY)",
                        anchorX = actualLeftEyePx.x,
                        anchorY = actualLeftEyePx.y,
                        isRightSide = false,
                        yOffset = -18.dp.toPx()
                    )

                    // 3. Right Eye (40, 60) [dynamic]
                    drawFeatureCallout(
                        title = "Right Eye",
                        coordString = "($rightEyeCoordX, $rightEyeCoordY)",
                        anchorX = actualRightEyePx.x,
                        anchorY = actualRightEyePx.y,
                        isRightSide = true,
                        yOffset = -18.dp.toPx()
                    )

                    // 4. Left Mouth Corner (-30, -40) [dynamic]
                    drawFeatureCallout(
                        title = "Left Mouth Corner",
                        coordString = "($mouthLeftCoordX, $mouthLeftCoordY)",
                        anchorX = actualMouthLeftPx.x,
                        anchorY = actualMouthLeftPx.y,
                        isRightSide = false,
                        yOffset = 16.dp.toPx()
                    )

                    // 5. Right Mouth Corner (30, -40) [dynamic]
                    drawFeatureCallout(
                        title = "Right Mouth Corner",
                        coordString = "($mouthRightCoordX, $mouthRightCoordY)",
                        anchorX = actualMouthRightPx.x,
                        anchorY = actualMouthRightPx.y,
                        isRightSide = true,
                        yOffset = 16.dp.toPx()
                    )
                }

                // Tilt level indicator bar above forehead
                val roll = faceLandmarks.headEulerZ
                val isLevel = abs(roll) <= 4.0f
                val tiltColor = if (isLevel) Color(0xFF10B981) else Color(0xFFF59E0B)
                val barHalfLength = (effectiveEyeDist * 1.05f).coerceIn(36.dp.toPx(), 80.dp.toPx())
                val barCenterY = (originY - effectiveEyeDist * 1.55f).coerceAtLeast(34.dp.toPx())

                val rad = Math.toRadians(roll.toDouble())
                val cosR = cos(rad).toFloat()
                val sinR = sin(rad).toFloat()
                val p1 = Offset(originX - barHalfLength * cosR, barCenterY - barHalfLength * sinR)
                val p2 = Offset(originX + barHalfLength * cosR, barCenterY + barHalfLength * sinR)

                drawLine(color = Color.Black.copy(alpha = 0.35f), start = Offset(p1.x, p1.y + 1f), end = Offset(p2.x, p2.y + 1f), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color = tiltColor.copy(alpha = 0.85f), start = p1, end = p2, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(color = tiltColor, radius = 2.8.dp.toPx(), center = Offset(originX, barCenterY))
            }

            // ==========================================
            // 3. RULE-OF-THIRDS INTERSECTION TICKS
            // ==========================================
            val tickLength = 7.dp.toPx()
            val baseTickColor = Color.White.copy(alpha = 0.5f)
            val highlightedTickColor = Color(0xFF67E8F9) // Cyan glow when subject anchors on power point

            intersections.forEachIndexed { idx, point ->
                val isHighlighted = idx == activeIntersectionIndex
                val color = if (isHighlighted) highlightedTickColor else baseTickColor
                val length = if (isHighlighted) tickLength * 1.6f else tickLength
                val sw = if (isHighlighted) strokeWidth * 2.2f else strokeWidth * 1.5f

                if (isHighlighted) {
                    drawCircle(
                        color = highlightedTickColor.copy(alpha = 0.25f),
                        radius = tickLength * 1.8f,
                        center = point,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                drawLine(
                    color = color,
                    start = Offset(point.x - length, point.y),
                    end = Offset(point.x + length, point.y),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(point.x, point.y - length),
                    end = Offset(point.x, point.y + length),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

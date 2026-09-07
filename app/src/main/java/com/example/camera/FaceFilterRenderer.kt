package com.example.camera

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.atan2

/**
 * Pure Skia/Android Canvas renderer for real-time facial filters.
 * Reused for both live preview rendering and baking filters into captured photos.
 *
 * Implements config-driven, dynamically scaled face filters anchored to facial landmarks.
 */
object FaceFilterRenderer {

    fun drawFilter(
        canvas: Canvas,
        width: Float,
        height: Float,
        filter: CameraFilter,
        landmarks: FaceLandmarkData?
    ) {
        if (filter == CameraFilter.NONE || landmarks == null || !landmarks.isFaceDetected) {
            return
        }

        val leftEye = landmarks.leftEye
        val rightEye = landmarks.rightEye
        if (leftEye == null || rightEye == null) return

        val eye1X = leftEye.x * width
        val eye1Y = leftEye.y * height
        val eye2X = rightEye.x * width
        val eye2Y = rightEye.y * height

        // Rotation angle derived from eye slope and headEulerZ
        val eyeAngleDeg = Math.toDegrees(atan2((eye2Y - eye1Y).toDouble(), (eye2X - eye1X).toDouble())).toFloat()
        val rotationDeg = if (Math.abs(landmarks.headEulerZ) > 1f) {
            landmarks.headEulerZ
        } else {
            eyeAngleDeg
        }

        // Config-driven asset parameters
        val config = FaceFilterRegistry.getConfig(filter)

        // Dynamic scale factor derived from measured inter-eye distance
        val scaleFactor = FaceFilterScaleUtil.getFaceScaleFactor(
            landmarks = landmarks,
            referenceEyeDistance = config.referenceEyeDistance,
            canvasWidth = width,
            canvasHeight = height
        )

        // Anchor landmark point
        val anchorPoint = FaceFilterScaleUtil.resolveAnchorPoint(
            landmarks = landmarks,
            anchor = config.anchor,
            canvasWidth = width,
            canvasHeight = height,
            rotationDeg = rotationDeg,
            verticalOffsetRatio = config.verticalOffsetRatio
        )

        when (filter) {
            CameraFilter.NEON_GLASSES -> drawNeonGlasses(
                canvas = canvas,
                anchorX = anchorPoint.x,
                anchorY = anchorPoint.y,
                scaleFactor = scaleFactor,
                rotationDeg = rotationDeg,
                config = config
            )
            CameraFilter.AVIATOR_SHADES -> drawAviatorShades(
                canvas = canvas,
                anchorX = anchorPoint.x,
                anchorY = anchorPoint.y,
                scaleFactor = scaleFactor,
                rotationDeg = rotationDeg,
                config = config
            )
            CameraFilter.CAT_EARS -> drawCatEars(
                canvas = canvas,
                anchorX = anchorPoint.x,
                anchorY = anchorPoint.y,
                scaleFactor = scaleFactor,
                rotationDeg = rotationDeg,
                config = config
            )
            CameraFilter.CELESTIAL_HALO -> drawCelestialHalo(
                canvas = canvas,
                anchorX = anchorPoint.x,
                anchorY = anchorPoint.y,
                scaleFactor = scaleFactor,
                rotationDeg = rotationDeg,
                pitchDeg = landmarks.headEulerX,
                config = config
            )
            CameraFilter.CYBER_MESH -> drawCyberMesh(
                canvas = canvas,
                width = width,
                height = height,
                landmarks = landmarks,
                scaleFactor = scaleFactor,
                rotationDeg = rotationDeg
            )
            CameraFilter.NONE -> { /* no-op */ }
        }
    }

    /**
     * Futuristic Neon Cyber Sunglasses / Cyber Goggles.
     * Proportioned to wrap temple-to-temple on an adult face and dynamically scaled.
     */
    private fun drawNeonGlasses(
        canvas: Canvas,
        anchorX: Float,
        anchorY: Float,
        scaleFactor: Float,
        rotationDeg: Float,
        config: FilterAssetConfig
    ) {
        canvas.save()
        canvas.translate(anchorX, anchorY)
        canvas.rotate(rotationDeg)

        val (glassesWidth, glassesHeight) = config.getRenderDimensions(scaleFactor)
        val halfW = glassesWidth / 2f
        val halfH = glassesHeight / 2f

        val lensWidth = glassesWidth * 0.40f
        val lensSpacing = glassesWidth * 0.08f

        // Dark shaded lens paint with cyan/purple gradient
        val lensGradient = LinearGradient(
            -halfW, -halfH, halfW, halfH,
            intArrayOf(Color.argb(230, 10, 20, 35), Color.argb(230, 50, 15, 60)),
            null,
            Shader.TileMode.CLAMP
        )

        val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = lensGradient
            style = Paint.Style.FILL
        }

        val glowRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 240, 255) // Cyber cyan
            strokeWidth = 5.5f * scaleFactor
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val magentaAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 30, 140) // Neon magenta
            strokeWidth = 3.5f * scaleFactor
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(130, 255, 255, 255)
            strokeWidth = 2.5f * scaleFactor
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        // Left Lens Path (angular cyber hexagonal teardrop centered over eye)
        val leftLensStart = -halfW + (glassesWidth - (lensWidth * 2 + lensSpacing)) / 2f
        val leftLensEnd = leftLensStart + lensWidth
        val leftLensPath = Path().apply {
            moveTo(leftLensStart, -halfH * 0.52f)
            lineTo(leftLensEnd, -halfH * 0.52f)
            lineTo(leftLensEnd - 10f * scaleFactor, halfH * 0.55f)
            lineTo(leftLensStart + 15f * scaleFactor, halfH * 0.55f)
            close()
        }

        // Right Lens Path
        val rightLensStart = leftLensEnd + lensSpacing
        val rightLensEnd = rightLensStart + lensWidth
        val rightLensPath = Path().apply {
            moveTo(rightLensStart, -halfH * 0.52f)
            lineTo(rightLensEnd, -halfH * 0.52f)
            lineTo(rightLensEnd - 15f * scaleFactor, halfH * 0.55f)
            lineTo(rightLensStart + 10f * scaleFactor, halfH * 0.55f)
            close()
        }

        // Draw Lenses
        canvas.drawPath(leftLensPath, lensPaint)
        canvas.drawPath(rightLensPath, lensPaint)

        // Draw Glowing Cyber Rims
        canvas.drawPath(leftLensPath, glowRimPaint)
        canvas.drawPath(rightLensPath, glowRimPaint)

        // Top Brow Bar (sweeping cyber visor bar above eyes)
        val browY = -halfH * 0.62f
        canvas.drawLine(-halfW * 0.98f, browY, halfW * 0.98f, browY, magentaAccentPaint)

        // Center Nose Bridge Connector
        canvas.drawLine(-lensSpacing / 2f, -halfH * 0.15f, lensSpacing / 2f, -halfH * 0.15f, glowRimPaint)

        // Cyber Temples / Side Wings extending back past the eyes
        canvas.drawLine(-halfW * 0.98f, browY, -halfW * 0.85f, halfH * 0.15f, glowRimPaint)
        canvas.drawLine(halfW * 0.98f, browY, halfW * 0.85f, halfH * 0.15f, glowRimPaint)

        // High-tech specular visor glare reflection
        val glarePath = Path().apply {
            moveTo(-halfW * 0.60f, -halfH * 0.35f)
            lineTo(-halfW * 0.30f, halfH * 0.35f)
        }
        canvas.drawPath(glarePath, highlightPaint)

        canvas.restore()
    }

    /**
     * Classic Wireframe Aviator Sunglasses.
     * Anchored to nose bridge with classic teardrop curvature and dynamic scale factor.
     */
    private fun drawAviatorShades(
        canvas: Canvas,
        anchorX: Float,
        anchorY: Float,
        scaleFactor: Float,
        rotationDeg: Float,
        config: FilterAssetConfig
    ) {
        canvas.save()
        canvas.translate(anchorX, anchorY)
        canvas.rotate(rotationDeg)

        val (glassesWidth, glassesHeight) = config.getRenderDimensions(scaleFactor)
        val lensWidth = glassesWidth * 0.42f
        val lensHeight = glassesHeight * 0.88f
        val bridgeWidth = glassesWidth * 0.09f

        val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(225, 24, 28, 32)
            style = Paint.Style.FILL
        }

        val wireFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(230, 195, 95) // Classic gold wire
            strokeWidth = 4.5f * scaleFactor
            style = Paint.Style.STROKE
        }

        val browBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(230, 195, 95)
            strokeWidth = 4.0f * scaleFactor
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        val leftRect = RectF(
            -bridgeWidth / 2f - lensWidth,
            -lensHeight * 0.46f,
            -bridgeWidth / 2f,
            lensHeight * 0.54f
        )
        val rightRect = RectF(
            bridgeWidth / 2f,
            -lensHeight * 0.46f,
            bridgeWidth / 2f + lensWidth,
            lensHeight * 0.54f
        )

        val cornerRadius = lensWidth * 0.45f

        // Teardrop rounded lenses
        canvas.drawRoundRect(leftRect, cornerRadius, cornerRadius, lensPaint)
        canvas.drawRoundRect(rightRect, cornerRadius, cornerRadius, lensPaint)

        // Wire rims
        canvas.drawRoundRect(leftRect, cornerRadius, cornerRadius, wireFramePaint)
        canvas.drawRoundRect(rightRect, cornerRadius, cornerRadius, wireFramePaint)

        // Center nose bridge between eye line
        canvas.drawLine(-bridgeWidth / 2f, -lensHeight * 0.10f, bridgeWidth / 2f, -lensHeight * 0.10f, wireFramePaint)

        // Top sweat brow bar
        canvas.drawLine(
            -bridgeWidth / 2f - lensWidth * 0.72f,
            -lensHeight * 0.50f,
            bridgeWidth / 2f + lensWidth * 0.72f,
            -lensHeight * 0.50f,
            browBarPaint
        )

        // Lens specular reflections
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 255, 255, 255)
            strokeWidth = 3.5f * scaleFactor
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(
            -bridgeWidth / 2f - lensWidth * 0.65f,
            -lensHeight * 0.20f,
            -bridgeWidth / 2f - lensWidth * 0.40f,
            lensHeight * 0.45f,
            sheenPaint
        )
        canvas.drawLine(
            bridgeWidth / 2f + lensWidth * 0.35f,
            -lensHeight * 0.20f,
            bridgeWidth / 2f + lensWidth * 0.60f,
            lensHeight * 0.45f,
            sheenPaint
        )

        canvas.restore()
    }

    /**
     * Stylized Cat / Fox Ears.
     * Anchored to forehead crown and dynamically scaled.
     */
    private fun drawCatEars(
        canvas: Canvas,
        anchorX: Float,
        anchorY: Float,
        scaleFactor: Float,
        rotationDeg: Float,
        config: FilterAssetConfig
    ) {
        canvas.save()
        canvas.translate(anchorX, anchorY)
        canvas.rotate(rotationDeg)

        val (totalWidth, totalHeight) = config.getRenderDimensions(scaleFactor)
        val earSpread = totalWidth * 0.42f
        val earWidth = totalWidth * 0.32f
        val earBaseY = 0f
        val earTipY = -totalHeight

        val outerEarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(32, 34, 40)
            style = Paint.Style.FILL
        }

        val innerEarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 140, 175) // Soft pink
            style = Paint.Style.FILL
        }

        val earBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 255, 255, 255)
            strokeWidth = 3.5f * scaleFactor
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        // Left Outer Ear
        val leftEar = Path().apply {
            moveTo(-earSpread - earWidth / 2f, earBaseY)
            lineTo(-earSpread, earTipY)
            lineTo(-earSpread + earWidth / 2f, earBaseY + totalHeight * 0.08f)
            close()
        }

        // Left Inner Ear
        val leftInner = Path().apply {
            moveTo(-earSpread - earWidth * 0.32f, earBaseY - totalHeight * 0.04f)
            lineTo(-earSpread, earTipY + totalHeight * 0.22f)
            lineTo(-earSpread + earWidth * 0.32f, earBaseY)
            close()
        }

        // Right Outer Ear
        val rightEar = Path().apply {
            moveTo(earSpread - earWidth / 2f, earBaseY + totalHeight * 0.08f)
            lineTo(earSpread, earTipY)
            lineTo(earSpread + earWidth / 2f, earBaseY)
            close()
        }

        // Right Inner Ear
        val rightInner = Path().apply {
            moveTo(earSpread - earWidth * 0.32f, earBaseY)
            lineTo(earSpread, earTipY + totalHeight * 0.22f)
            lineTo(earSpread + earWidth * 0.32f, earBaseY - totalHeight * 0.04f)
            close()
        }

        // Draw Left
        canvas.drawPath(leftEar, outerEarPaint)
        canvas.drawPath(leftEar, earBorderPaint)
        canvas.drawPath(leftInner, innerEarPaint)

        // Draw Right
        canvas.drawPath(rightEar, outerEarPaint)
        canvas.drawPath(rightEar, earBorderPaint)
        canvas.drawPath(rightInner, innerEarPaint)

        // Cute whisker accents extending from lower face
        val whiskerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(170, 255, 255, 255)
            strokeWidth = 2.5f * scaleFactor
            strokeCap = Paint.Cap.ROUND
        }

        val whiskerBaseY = totalHeight * 1.05f
        val innerWhiskerX = totalWidth * 0.45f
        val outerWhiskerX = totalWidth * 0.78f
        val whiskerSpanY = totalHeight * 0.14f

        // Left Whiskers
        canvas.drawLine(-innerWhiskerX, whiskerBaseY - whiskerSpanY, -outerWhiskerX, whiskerBaseY - whiskerSpanY * 2f, whiskerPaint)
        canvas.drawLine(-innerWhiskerX, whiskerBaseY, -outerWhiskerX * 1.05f, whiskerBaseY, whiskerPaint)
        canvas.drawLine(-innerWhiskerX, whiskerBaseY + whiskerSpanY, -outerWhiskerX, whiskerBaseY + whiskerSpanY * 2f, whiskerPaint)

        // Right Whiskers
        canvas.drawLine(innerWhiskerX, whiskerBaseY - whiskerSpanY, outerWhiskerX, whiskerBaseY - whiskerSpanY * 2f, whiskerPaint)
        canvas.drawLine(innerWhiskerX, whiskerBaseY, outerWhiskerX * 1.05f, whiskerBaseY, whiskerPaint)
        canvas.drawLine(innerWhiskerX, whiskerBaseY + whiskerSpanY, outerWhiskerX, whiskerBaseY + whiskerSpanY * 2f, whiskerPaint)

        canvas.restore()
    }

    /**
     * Celestial Golden Halo with Sparkles.
     * Anchored above head crown with dynamic scaling and pitch tilt.
     */
    private fun drawCelestialHalo(
        canvas: Canvas,
        anchorX: Float,
        anchorY: Float,
        scaleFactor: Float,
        rotationDeg: Float,
        pitchDeg: Float,
        config: FilterAssetConfig
    ) {
        canvas.save()
        canvas.translate(anchorX, anchorY)
        canvas.rotate(rotationDeg)

        val (haloWidth, haloHeight) = config.getRenderDimensions(scaleFactor)
        val haloRadiusX = haloWidth / 2f
        // Perspective foreshortening based on pitch
        val haloRadiusY = (haloHeight / 2f) * (1f + (pitchDeg * 0.015f)).coerceIn(0.2f, 2.0f)

        val haloRect = RectF(
            -haloRadiusX,
            -haloRadiusY,
            haloRadiusX,
            haloRadiusY
        )

        // Outer golden glow
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 255, 215, 0)
            style = Paint.Style.STROKE
            strokeWidth = 18f * scaleFactor
        }

        // Main radiant ring
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 235, 120)
            style = Paint.Style.STROKE
            strokeWidth = 8f * scaleFactor
        }

        // Inner bright white core
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f * scaleFactor
        }

        canvas.drawOval(haloRect, glowPaint)
        canvas.drawOval(haloRect, ringPaint)
        canvas.drawOval(haloRect, corePaint)

        // Floating sparkle stars scaled dynamically
        fun drawStar(x: Float, y: Float, radius: Float) {
            val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                strokeWidth = (radius * 0.25f).coerceAtLeast(1.5f)
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(x - radius, y, x + radius, y, starPaint)
            canvas.drawLine(x, y - radius, x, y + radius, starPaint)
        }

        drawStar(-haloRadiusX * 1.08f, -haloRadiusY * 0.2f, 12f * scaleFactor)
        drawStar(haloRadiusX * 1.12f, haloRadiusY * 0.2f, 15f * scaleFactor)
        drawStar(-haloRadiusX * 0.30f, -haloRadiusY * 1.2f, 9f * scaleFactor)

        canvas.restore()
    }

    /**
     * Futuristic Facial Landmark Mesh / Tracking HUD.
     * All line strokes, tracking dots, and HUD text scale dynamically with face dimensions.
     */
    private fun drawCyberMesh(
        canvas: Canvas,
        width: Float,
        height: Float,
        landmarks: FaceLandmarkData,
        scaleFactor: Float,
        rotationDeg: Float
    ) {
        val strokeWidthPx = (2.2f * scaleFactor).coerceIn(1.5f, 6.0f)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, 0, 240, 255)
            strokeWidth = strokeWidthPx
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(
                floatArrayOf((8f * scaleFactor).coerceAtLeast(4f), (6f * scaleFactor).coerceAtLeast(3f)),
                0f
            )
        }

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 240, 255)
            style = Paint.Style.FILL
        }

        val dotGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 0, 240, 255)
            style = Paint.Style.FILL
        }

        val textSizePx = (22f * scaleFactor).coerceIn(16f, 44f)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 240, 255)
            textSize = textSizePx
            typeface = android.graphics.Typeface.MONOSPACE
        }

        val points = mutableListOf<Pair<Float, Float>>()

        fun addPoint(offset: androidx.compose.ui.geometry.Offset?) {
            if (offset != null) {
                points.add(Pair(offset.x * width, offset.y * height))
            }
        }

        addPoint(landmarks.leftEye)
        addPoint(landmarks.rightEye)
        addPoint(landmarks.noseBase)
        addPoint(landmarks.leftCheek)
        addPoint(landmarks.rightCheek)
        addPoint(landmarks.mouthLeft)
        addPoint(landmarks.mouthRight)
        addPoint(landmarks.mouthBottom)

        // Connect constellation lines
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            canvas.drawLine(p1.first, p1.second, p2.first, p2.second, linePaint)
        }

        // Cross connection between eyes and nose
        if (landmarks.leftEye != null && landmarks.noseBase != null) {
            canvas.drawLine(
                landmarks.leftEye.x * width, landmarks.leftEye.y * height,
                landmarks.noseBase.x * width, landmarks.noseBase.y * height,
                linePaint
            )
        }
        if (landmarks.rightEye != null && landmarks.noseBase != null) {
            canvas.drawLine(
                landmarks.rightEye.x * width, landmarks.rightEye.y * height,
                landmarks.noseBase.x * width, landmarks.noseBase.y * height,
                linePaint
            )
        }

        // Draw tracking nodes scaled dynamically
        val glowRadius = (9f * scaleFactor).coerceIn(6f, 24f)
        val coreRadius = (4f * scaleFactor).coerceIn(3f, 10f)

        for (p in points) {
            canvas.drawCircle(p.first, p.second, glowRadius, dotGlowPaint)
            canvas.drawCircle(p.first, p.second, coreRadius, dotPaint)
        }

        // HUD Readout positioned relative to first landmark
        if (points.isNotEmpty()) {
            val first = points.first()
            val textOffsetX = 80f * scaleFactor
            val textOffsetY = 45f * scaleFactor
            canvas.drawText("TRACKING: ACTIVE", first.first - textOffsetX, first.second - textOffsetY, textPaint)
            canvas.drawText(
                String.format(
                    java.util.Locale.US,
                    "SCALE: %.2fx | ROT: %.1f°",
                    scaleFactor,
                    rotationDeg
                ),
                first.first - textOffsetX,
                first.second - textOffsetY + textSizePx * 1.25f,
                textPaint
            )
        }
    }
}

package com.example.camera

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Anchor landmark attachment point for face filters.
 * Defines where the origin of the filter asset should be pinned on the face.
 */
enum class FilterAnchor {
    BRIDGE_OF_NOSE, // Between eyes, resting naturally on the nose bridge (ideal for glasses/goggles)
    EYE_CENTER,     // Exact geometric midpoint between left & right eye pupils
    NOSE_BASE,      // Base of the nose
    FOREHEAD,       // Forehead brow area above eyes (ideal for ears, headbands)
    TOP_OF_HEAD,    // Crown above forehead (ideal for halos, floating headwear)
    MOUTH_CENTER    // Midpoint between mouth corners (ideal for mustaches, masks)
}

/**
 * Configuration specification for a face filter asset.
 *
 * Config-driven scaling:
 * An asset designed at [designWidth] x [designHeight] when reference inter-eye distance is [referenceEyeDistance].
 *
 * liveScaleFactor = (currentInterEyeDistance / referenceEyeDistance) * scaleMultiplier
 */
data class FilterAssetConfig(
    val filter: CameraFilter,
    val anchor: FilterAnchor,
    val referenceEyeDistance: Float = 100f, // Canonical design reference inter-eye distance in pixels
    val designWidth: Float,                 // Asset width at reference eye distance
    val designHeight: Float,                // Asset height at reference eye distance
    val verticalOffsetRatio: Float = 0f,    // Configurable vertical offset along face longitudinal axis (in units of eye distance, applied AFTER scaling)
    val scaleMultiplier: Float = 1.0f       // Fine-tuning scale multiplier
) {
    /**
     * Backwards-compatible alias for previous property name.
     */
    val anchorOffsetYRatio: Float get() = verticalOffsetRatio

    /**
     * Resolves rendered dimensions for the current frame's scale factor.
     */
    fun getRenderDimensions(scaleFactor: Float): Pair<Float, Float> {
        val w = designWidth * scaleFactor
        val h = designHeight * scaleFactor
        return Pair(w, h)
    }
}

/**
 * Central registry of all filter asset configurations.
 */
object FaceFilterRegistry {
    private val configs: Map<CameraFilter, FilterAssetConfig> = mapOf(
        CameraFilter.NEON_GLASSES to FilterAssetConfig(
            filter = CameraFilter.NEON_GLASSES,
            anchor = FilterAnchor.EYE_CENTER, // Anchored directly to eye landmarks midpoint
            referenceEyeDistance = 100f,
            designWidth = 300f,   // Adult cyber goggles spanning temple-to-temple (3.0x eye distance)
            designHeight = 110f,  // Full orbital and upper-cheek coverage (1.1x eye distance)
            verticalOffsetRatio = -0.02f, // Fine-tuned per-asset offset: optical center directly over eye landmarks
            scaleMultiplier = 1.0f
        ),
        CameraFilter.AVIATOR_SHADES to FilterAssetConfig(
            filter = CameraFilter.AVIATOR_SHADES,
            anchor = FilterAnchor.EYE_CENTER, // Anchored directly to eye landmarks midpoint
            referenceEyeDistance = 100f,
            designWidth = 270f,   // Adult wireframe aviators with wide brow bar (2.7x eye distance)
            designHeight = 112f,  // Classic teardrop depth (1.12x eye distance)
            verticalOffsetRatio = -0.01f, // Fine-tuned per-asset offset: classic wireframe brow sits naturally on orbital rim
            scaleMultiplier = 1.0f
        ),
        CameraFilter.CAT_EARS to FilterAssetConfig(
            filter = CameraFilter.CAT_EARS,
            anchor = FilterAnchor.FOREHEAD,
            referenceEyeDistance = 100f,
            designWidth = 240f,   // Natural ear spread on head crown (2.4x eye distance)
            designHeight = 165f,  // Height from base to tip (1.65x eye distance)
            verticalOffsetRatio = 0.0f,
            scaleMultiplier = 1.0f
        ),
        CameraFilter.CELESTIAL_HALO to FilterAssetConfig(
            filter = CameraFilter.CELESTIAL_HALO,
            anchor = FilterAnchor.TOP_OF_HEAD,
            referenceEyeDistance = 100f,
            designWidth = 270f,   // Radiant floating halo ring (2.7x eye distance)
            designHeight = 75f,   // Ring vertical perspective foreshortening
            verticalOffsetRatio = 0.0f,
            scaleMultiplier = 1.0f
        ),
        CameraFilter.CYBER_MESH to FilterAssetConfig(
            filter = CameraFilter.CYBER_MESH,
            anchor = FilterAnchor.EYE_CENTER,
            referenceEyeDistance = 100f,
            designWidth = 300f,
            designHeight = 350f,
            verticalOffsetRatio = 0.0f,
            scaleMultiplier = 1.0f
        )
    )

    fun getConfig(filter: CameraFilter): FilterAssetConfig {
        return configs[filter] ?: FilterAssetConfig(
            filter = filter,
            anchor = FilterAnchor.EYE_CENTER,
            referenceEyeDistance = 100f,
            designWidth = 200f,
            designHeight = 200f
        )
    }
}

/**
 * Shared utility for dynamic face filter scaling and landmark anchoring.
 * Guarantees all filters scale reliably relative to actual measured facial dimensions.
 */
object FaceFilterScaleUtil {
    const val DEFAULT_REFERENCE_EYE_DISTANCE = 100f

    /**
     * Calculates the reliable dynamic face scale factor derived from measured inter-eye distance.
     *
     * @param landmarks The detected (and smoothed) face landmark data.
     * @param referenceEyeDistance The canonical design reference inter-eye distance in pixels (default: 100px).
     * @param canvasWidth Viewport/Canvas width in pixels.
     * @param canvasHeight Viewport/Canvas height in pixels.
     * @return Smooth, unitless scale factor: (currentInterEyeDistance / referenceEyeDistance).
     */
    fun getFaceScaleFactor(
        landmarks: FaceLandmarkData?,
        referenceEyeDistance: Float = DEFAULT_REFERENCE_EYE_DISTANCE,
        canvasWidth: Float = 1000f,
        canvasHeight: Float = 1000f
    ): Float {
        if (landmarks == null || !landmarks.isFaceDetected) return 1.0f
        val currentEyeDistance = landmarks.getInterEyeDistance(canvasWidth, canvasHeight)
        val ref = if (referenceEyeDistance <= 0f) DEFAULT_REFERENCE_EYE_DISTANCE else referenceEyeDistance
        return (currentEyeDistance / ref).coerceIn(0.15f, 10.0f)
    }

    /**
     * Overload for direct usage with canvas dimensions.
     */
    fun getFaceScaleFactor(
        landmarks: FaceLandmarkData?,
        canvasWidth: Float,
        canvasHeight: Float
    ): Float {
        return getFaceScaleFactor(
            landmarks = landmarks,
            referenceEyeDistance = DEFAULT_REFERENCE_EYE_DISTANCE,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight
        )
    }

    /**
     * Resolves the anchor point in canvas pixel space based on face orientation and landmarks.
     * Applies [verticalOffsetRatio] along the face's rotated longitudinal axis AFTER scaling,
     * ensuring proportional consistency across all face distances.
     */
    fun resolveAnchorPoint(
        landmarks: FaceLandmarkData,
        anchor: FilterAnchor,
        canvasWidth: Float,
        canvasHeight: Float,
        rotationDeg: Float,
        verticalOffsetRatio: Float = 0f
    ): Offset {
        val leftEye = landmarks.leftEye ?: Offset(0.4f, 0.4f)
        val rightEye = landmarks.rightEye ?: Offset(0.6f, 0.4f)

        val eye1X = leftEye.x * canvasWidth
        val eye1Y = leftEye.y * canvasHeight
        val eye2X = rightEye.x * canvasWidth
        val eye2Y = rightEye.y * canvasHeight

        val eyeCenterX = (eye1X + eye2X) / 2f
        val eyeCenterY = (eye1Y + eye2Y) / 2f
        val eyeDistance = landmarks.getInterEyeDistance(canvasWidth, canvasHeight)

        // Head longitudinal and transversal orientation unit vectors
        val rotRad = Math.toRadians(rotationDeg.toDouble())
        val upVecX = sin(rotRad).toFloat()
        val upVecY = -cos(rotRad).toFloat()
        val downVecX = -sin(rotRad).toFloat()
        val downVecY = cos(rotRad).toFloat()

        val baseAnchor = when (anchor) {
            FilterAnchor.EYE_CENTER -> Offset(eyeCenterX, eyeCenterY)

            FilterAnchor.BRIDGE_OF_NOSE -> {
                if (landmarks.noseBase != null) {
                    val noseX = landmarks.noseBase.x * canvasWidth
                    val noseY = landmarks.noseBase.y * canvasHeight
                    // Perched 35% down the nose bridge from eye line
                    Offset(eyeCenterX * 0.65f + noseX * 0.35f, eyeCenterY * 0.65f + noseY * 0.35f)
                } else {
                    Offset(
                        eyeCenterX + downVecX * (eyeDistance * 0.15f),
                        eyeCenterY + downVecY * (eyeDistance * 0.15f)
                    )
                }
            }

            FilterAnchor.NOSE_BASE -> {
                if (landmarks.noseBase != null) {
                    Offset(landmarks.noseBase.x * canvasWidth, landmarks.noseBase.y * canvasHeight)
                } else {
                    Offset(
                        eyeCenterX + downVecX * (eyeDistance * 0.70f),
                        eyeCenterY + downVecY * (eyeDistance * 0.70f)
                    )
                }
            }

            FilterAnchor.FOREHEAD -> {
                Offset(
                    eyeCenterX + upVecX * (eyeDistance * 1.15f),
                    eyeCenterY + upVecY * (eyeDistance * 1.15f)
                )
            }

            FilterAnchor.TOP_OF_HEAD -> {
                Offset(
                    eyeCenterX + upVecX * (eyeDistance * 1.85f),
                    eyeCenterY + upVecY * (eyeDistance * 1.85f)
                )
            }

            FilterAnchor.MOUTH_CENTER -> {
                if (landmarks.mouthLeft != null && landmarks.mouthRight != null) {
                    val m1X = landmarks.mouthLeft.x * canvasWidth
                    val m1Y = landmarks.mouthLeft.y * canvasHeight
                    val m2X = landmarks.mouthRight.x * canvasWidth
                    val m2Y = landmarks.mouthRight.y * canvasHeight
                    Offset((m1X + m2X) / 2f, (m1Y + m2Y) / 2f)
                } else {
                    Offset(
                        eyeCenterX + downVecX * (eyeDistance * 1.35f),
                        eyeCenterY + downVecY * (eyeDistance * 1.35f)
                    )
                }
            }
        }

        // Apply filter-specific secondary longitudinal offset AFTER scaling (scaled via eyeDistance)
        return if (verticalOffsetRatio != 0f) {
            Offset(
                baseAnchor.x + downVecX * (eyeDistance * verticalOffsetRatio),
                baseAnchor.y + downVecY * (eyeDistance * verticalOffsetRatio)
            )
        } else {
            baseAnchor
        }
    }
}

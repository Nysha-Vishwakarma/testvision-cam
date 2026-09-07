package com.example.camera

import android.graphics.Rect
import androidx.compose.ui.geometry.Offset

/**
 * Facial landmark data extracted and smoothed from ML Kit Face Detection.
 */
data class FaceLandmarkData(
    val isFaceDetected: Boolean = false,
    val leftEye: Offset? = null,
    val rightEye: Offset? = null,
    val noseBase: Offset? = null,
    val mouthLeft: Offset? = null,
    val mouthRight: Offset? = null,
    val mouthBottom: Offset? = null,
    val leftCheek: Offset? = null,
    val rightCheek: Offset? = null,
    val leftEar: Offset? = null,
    val rightEar: Offset? = null,
    val headEulerX: Float = 0f, // Pitch
    val headEulerY: Float = 0f, // Yaw
    val headEulerZ: Float = 0f, // Roll
    val boundingBox: Rect = Rect(),
    val sourceImageWidth: Int = 480,
    val sourceImageHeight: Int = 640,
    val isFrontFacing: Boolean = false,
    val smoothedInterEyeDistance: Float = 0f, // Smoothed normalized distance between eyes for jitter-free scaling
    val faceContour: List<Offset> = emptyList(),
    val leftEyebrowContour: List<Offset> = emptyList(),
    val rightEyebrowContour: List<Offset> = emptyList(),
    val noseBridgeContour: List<Offset> = emptyList(),
    val lipsContour: List<Offset> = emptyList()
) {
    /**
     * Returns the distance between the two eyes in actual canvas pixels.
     */
    fun getInterEyeDistance(canvasWidth: Float, canvasHeight: Float): Float {
        val left = leftEye
        val right = rightEye
        if (left == null || right == null) return 80f

        val dx = (right.x - left.x) * canvasWidth
        val dy = (right.y - left.y) * canvasHeight
        val rawPxDist = kotlin.math.hypot(dx, dy)

        return if (smoothedInterEyeDistance > 0f) {
            // Scale smoothed normalized distance by canvas width
            val smoothedPx = smoothedInterEyeDistance * canvasWidth
            // Blend slightly with raw distance for high responsiveness while eliminating jitter
            (smoothedPx * 0.85f + rawPxDist * 0.15f).coerceAtLeast(15f)
        } else {
            rawPxDist.coerceAtLeast(15f)
        }
    }
}

/**
 * Exponential Moving Average (EMA) landmark smoother.
 * Prevents jitter between frames and stabilizes filter positioning and scale.
 */
class LandmarkSmoother(
    private val alpha: Float = 0.45f
) {
    private var lastData: FaceLandmarkData? = null
    private var lostFrameCount: Int = 0

    fun smooth(newData: FaceLandmarkData?): FaceLandmarkData? {
        if (newData == null || !newData.isFaceDetected) {
            lostFrameCount++
            if (lostFrameCount > 6) {
                lastData = null
            }
            return lastData?.copy(isFaceDetected = false)
        }

        lostFrameCount = 0
        val previous = lastData

        if (previous == null || !previous.isFaceDetected) {
            val initialEyeDist = if (newData.leftEye != null && newData.rightEye != null) {
                kotlin.math.hypot(newData.rightEye.x - newData.leftEye.x, newData.rightEye.y - newData.leftEye.y)
            } else {
                0.22f
            }
            val initialData = newData.copy(smoothedInterEyeDistance = initialEyeDist)
            lastData = initialData
            return initialData
        }

        fun smoothOffset(curr: Offset?, prev: Offset?): Offset? {
            if (curr == null) return prev
            if (prev == null) return curr
            return Offset(
                x = alpha * curr.x + (1f - alpha) * prev.x,
                y = alpha * curr.y + (1f - alpha) * prev.y
            )
        }

        fun smoothFloat(curr: Float, prev: Float): Float {
            return alpha * curr + (1f - alpha) * prev
        }

        val rawNormalizedEyeDist = if (newData.leftEye != null && newData.rightEye != null) {
            kotlin.math.hypot(newData.rightEye.x - newData.leftEye.x, newData.rightEye.y - newData.leftEye.y)
        } else {
            previous.smoothedInterEyeDistance
        }

        val smoothedEyeDistance = if (rawNormalizedEyeDist > 0f) {
            smoothFloat(rawNormalizedEyeDist, previous.smoothedInterEyeDistance)
        } else {
            previous.smoothedInterEyeDistance
        }

        val smoothed = FaceLandmarkData(
            isFaceDetected = true,
            leftEye = smoothOffset(newData.leftEye, previous.leftEye),
            rightEye = smoothOffset(newData.rightEye, previous.rightEye),
            noseBase = smoothOffset(newData.noseBase, previous.noseBase),
            mouthLeft = smoothOffset(newData.mouthLeft, previous.mouthLeft),
            mouthRight = smoothOffset(newData.mouthRight, previous.mouthRight),
            mouthBottom = smoothOffset(newData.mouthBottom, previous.mouthBottom),
            leftCheek = smoothOffset(newData.leftCheek, previous.leftCheek),
            rightCheek = smoothOffset(newData.rightCheek, previous.rightCheek),
            leftEar = smoothOffset(newData.leftEar, previous.leftEar),
            rightEar = smoothOffset(newData.rightEar, previous.rightEar),
            headEulerX = smoothFloat(newData.headEulerX, previous.headEulerX),
            headEulerY = smoothFloat(newData.headEulerY, previous.headEulerY),
            headEulerZ = smoothFloat(newData.headEulerZ, previous.headEulerZ),
            boundingBox = newData.boundingBox,
            sourceImageWidth = newData.sourceImageWidth,
            sourceImageHeight = newData.sourceImageHeight,
            isFrontFacing = newData.isFrontFacing,
            smoothedInterEyeDistance = smoothedEyeDistance,
            faceContour = newData.faceContour.ifEmpty { previous.faceContour },
            leftEyebrowContour = newData.leftEyebrowContour.ifEmpty { previous.leftEyebrowContour },
            rightEyebrowContour = newData.rightEyebrowContour.ifEmpty { previous.rightEyebrowContour },
            noseBridgeContour = newData.noseBridgeContour.ifEmpty { previous.noseBridgeContour },
            lipsContour = newData.lipsContour.ifEmpty { previous.lipsContour }
        )

        lastData = smoothed
        return smoothed
    }

    fun reset() {
        lastData = null
        lostFrameCount = 0
    }
}

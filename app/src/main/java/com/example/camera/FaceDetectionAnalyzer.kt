package com.example.camera

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark

/**
 * CameraX ImageAnalysis analyzer running ML Kit Face Detection.
 * Executes on downscaled frames with STRATEGY_KEEP_ONLY_LATEST for maximum FPS.
 * Normalizes coordinates, handles front-camera mirroring, and applies EMA smoothing.
 */
class FaceDetectionAnalyzer(
    private val isFrontCamera: () -> Boolean,
    private val isEnabled: () -> Boolean,
    private val onFaceUpdated: (FaceLandmarkData?) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector: FaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build()
        FaceDetection.getClient(options)
    }

    private val smoother = LandmarkSmoother(alpha = 0.45f)
    private var isBusy = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!isEnabled()) {
            smoother.reset()
            onFaceUpdated(null)
            imageProxy.close()
            return
        }

        if (isBusy) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        isBusy = true
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        val inputImage = try {
            InputImage.fromMediaImage(mediaImage, rotationDegrees)
        } catch (e: Exception) {
            Log.w("FaceAnalyzer", "Failed to create InputImage", e)
            imageProxy.close()
            isBusy = false
            return
        }

        // ML Kit upright dimensions
        val uprightWidth = if (rotationDegrees == 90 || rotationDegrees == 270) {
            imageProxy.height.toFloat()
        } else {
            imageProxy.width.toFloat()
        }

        val uprightHeight = if (rotationDegrees == 90 || rotationDegrees == 270) {
            imageProxy.width.toFloat()
        } else {
            imageProxy.height.toFloat()
        }

        val frontFacing = isFrontCamera()

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }

                if (primaryFace != null) {
                    val rawData = extractLandmarks(
                        face = primaryFace,
                        uprightWidth = uprightWidth,
                        uprightHeight = uprightHeight,
                        isFrontFacing = frontFacing
                    )
                    val smoothed = smoother.smooth(rawData)
                    onFaceUpdated(smoothed)
                } else {
                    val smoothed = smoother.smooth(null)
                    onFaceUpdated(smoothed)
                }
            }
            .addOnFailureListener { e ->
                Log.w("FaceAnalyzer", "Face detection failed", e)
            }
            .addOnCompleteListener {
                isBusy = false
                imageProxy.close()
            }
    }

    private fun extractLandmarks(
        face: Face,
        uprightWidth: Float,
        uprightHeight: Float,
        isFrontFacing: Boolean
    ): FaceLandmarkData {
        fun toNormalizedOffset(landmark: FaceLandmark?): Offset? {
            if (landmark == null) return null
            val rawX = landmark.position.x
            val rawY = landmark.position.y

            // When front camera is mirrored in preview, invert X
            val normX = if (isFrontFacing) {
                1f - (rawX / uprightWidth)
            } else {
                rawX / uprightWidth
            }
            val normY = rawY / uprightHeight

            return Offset(normX.coerceIn(0f, 1f), normY.coerceIn(0f, 1f))
        }

        fun extractContour(contourType: Int): List<Offset> {
            val contour = face.getContour(contourType) ?: return emptyList()
            return contour.points.map { pt ->
                val normX = if (isFrontFacing) 1f - (pt.x / uprightWidth) else (pt.x / uprightWidth)
                val normY = pt.y / uprightHeight
                Offset(normX.coerceIn(0f, 1f), normY.coerceIn(0f, 1f))
            }
        }

        // For front-facing camera, Left/Right are mirrored visually from user's perspective
        val leftEyeLandmark = if (isFrontFacing) {
            face.getLandmark(FaceLandmark.RIGHT_EYE)
        } else {
            face.getLandmark(FaceLandmark.LEFT_EYE)
        }

        val rightEyeLandmark = if (isFrontFacing) {
            face.getLandmark(FaceLandmark.LEFT_EYE)
        } else {
            face.getLandmark(FaceLandmark.RIGHT_EYE)
        }

        val rollAngle = if (isFrontFacing) {
            -face.headEulerAngleZ
        } else {
            face.headEulerAngleZ
        }

        val faceContourPts = extractContour(FaceContour.FACE)
        val leftEyebrowPts = extractContour(if (isFrontFacing) FaceContour.RIGHT_EYEBROW_TOP else FaceContour.LEFT_EYEBROW_TOP)
        val rightEyebrowPts = extractContour(if (isFrontFacing) FaceContour.LEFT_EYEBROW_TOP else FaceContour.RIGHT_EYEBROW_TOP)
        val noseBridgePts = extractContour(FaceContour.NOSE_BRIDGE)
        val lipsPts = extractContour(FaceContour.UPPER_LIP_TOP) + extractContour(FaceContour.LOWER_LIP_BOTTOM)

        return FaceLandmarkData(
            isFaceDetected = true,
            leftEye = toNormalizedOffset(leftEyeLandmark),
            rightEye = toNormalizedOffset(rightEyeLandmark),
            noseBase = toNormalizedOffset(face.getLandmark(FaceLandmark.NOSE_BASE)),
            mouthLeft = toNormalizedOffset(face.getLandmark(if (isFrontFacing) FaceLandmark.MOUTH_RIGHT else FaceLandmark.MOUTH_LEFT)),
            mouthRight = toNormalizedOffset(face.getLandmark(if (isFrontFacing) FaceLandmark.MOUTH_LEFT else FaceLandmark.MOUTH_RIGHT)),
            mouthBottom = toNormalizedOffset(face.getLandmark(FaceLandmark.MOUTH_BOTTOM)),
            leftCheek = toNormalizedOffset(face.getLandmark(if (isFrontFacing) FaceLandmark.RIGHT_CHEEK else FaceLandmark.LEFT_CHEEK)),
            rightCheek = toNormalizedOffset(face.getLandmark(if (isFrontFacing) FaceLandmark.LEFT_CHEEK else FaceLandmark.RIGHT_CHEEK)),
            leftEar = toNormalizedOffset(face.getLandmark(if (isFrontFacing) FaceLandmark.RIGHT_EAR else FaceLandmark.LEFT_EAR)),
            rightEar = toNormalizedOffset(face.getLandmark(if (isFrontFacing) FaceLandmark.LEFT_EAR else FaceLandmark.RIGHT_EAR)),
            headEulerX = face.headEulerAngleX,
            headEulerY = face.headEulerAngleY,
            headEulerZ = rollAngle,
            boundingBox = face.boundingBox,
            sourceImageWidth = uprightWidth.toInt(),
            sourceImageHeight = uprightHeight.toInt(),
            isFrontFacing = isFrontFacing,
            faceContour = faceContourPts,
            leftEyebrowContour = leftEyebrowPts,
            rightEyebrowContour = rightEyebrowPts,
            noseBridgeContour = noseBridgePts,
            lipsContour = lipsPts
        )
    }

    fun close() {
        try {
            detector.close()
        } catch (e: Exception) {
            Log.w("FaceAnalyzer", "Error closing detector", e)
        }
    }
}

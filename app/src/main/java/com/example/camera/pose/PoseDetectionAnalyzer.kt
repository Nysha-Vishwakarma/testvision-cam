package com.example.camera.pose

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

/**
 * Real-time on-device CameraX ImageAnalysis analyzer using ML Kit Pose Detection in STREAM_MODE.
 * Operates at camera frame rate with zero network calls for instant responsiveness.
 *
 * Enforces:
 * - Proper rotation and front-camera mirroring.
 * - Confidence gating on core anatomical joints (shoulders, hips, knees).
 * - On-device joint angle calculation against the target skeleton.
 * - Multi-frame score smoothing to prevent jitter.
 * - Comprehensive logging of raw detector outputs to verify detection and orientation.
 */
class PoseDetectionAnalyzer(
    private val isFrontCamera: () -> Boolean,
    private val isEnabled: () -> Boolean,
    private val targetSkeletonProvider: () -> PoseSkeleton?,
    private val onPoseEvaluated: (PoseCompareResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector: PoseDetector by lazy {
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        PoseDetection.getClient(options)
    }

    private val scoreSmoother = PoseScoreSmoother(windowSize = 4)
    private var isBusy = false
    private var consecutiveNoPoseFrames = 0
    private val NO_POSE_THRESHOLD_FRAMES = 3
    private var lastValidResult: PoseCompareResult? = null

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!isEnabled()) {
            reset()
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
            Log.w("PoseAnalyzer", "Failed to create InputImage for pose analysis", e)
            imageProxy.close()
            isBusy = false
            return
        }

        // Upright dimensions based on sensor rotation
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
            .addOnSuccessListener { pose ->
                val allLandmarks = pose.allPoseLandmarks

                // Core joint confidence inspection
                val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
                val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
                val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
                val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
                val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
                val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)

                val coreJoints = listOf(leftShoulder, rightShoulder, leftHip, rightHip, leftKnee, rightKnee)
                val confidentCoreCount = coreJoints.count { it != null && it.inFrameLikelihood >= 0.5f }
                val shouldersVisible = (leftShoulder?.inFrameLikelihood ?: 0f) >= 0.45f &&
                    (rightShoulder?.inFrameLikelihood ?: 0f) >= 0.45f

                val isPoseDetected = allLandmarks.isNotEmpty() && confidentCoreCount >= 3 && shouldersVisible

                // Diagnostic logging: raw ML Kit confidence and frame orientation details
                Log.d(
                    "PoseAnalyzer",
                    "Pose Detection output: totalLandmarks=${allLandmarks.size}, " +
                        "confidentCoreCount=$confidentCoreCount/6, shouldersVisible=$shouldersVisible, " +
                        "rotation=$rotationDegrees°, frontCamera=$frontFacing, uprightSize=${uprightWidth.toInt()}x${uprightHeight.toInt()}, " +
                        "L_sh=${leftShoulder?.inFrameLikelihood ?: 0f}, R_sh=${rightShoulder?.inFrameLikelihood ?: 0f}, " +
                        "L_hip=${leftHip?.inFrameLikelihood ?: 0f}, R_hip=${rightHip?.inFrameLikelihood ?: 0f}, " +
                        "L_knee=${leftKnee?.inFrameLikelihood ?: 0f}, R_knee=${rightKnee?.inFrameLikelihood ?: 0f}"
                )

                if (!isPoseDetected) {
                    consecutiveNoPoseFrames++
                    if (consecutiveNoPoseFrames >= NO_POSE_THRESHOLD_FRAMES) {
                        scoreSmoother.reset()
                        lastValidResult = null
                        onPoseEvaluated(
                            PoseCompareResult(
                                noPoseDetected = true,
                                matchScore = null,
                                isMatched = false,
                                status = "NO_POSE_DETECTED",
                                primaryFeedback = "No pose detected — step into frame",
                                corrections = emptyList(),
                                alignedLimbs = emptyList()
                            )
                        )
                    } else if (lastValidResult != null) {
                        onPoseEvaluated(lastValidResult!!)
                    }
                } else {
                    consecutiveNoPoseFrames = 0
                    val target = targetSkeletonProvider() ?: BuiltInPoseLibrary.items[0].skeleton

                    // Normalize keypoints and handle selfie camera mirroring
                    val liveKpMap = mutableMapOf<String, PoseKeypoint>()
                    val landmarkMapping = listOf(
                        PoseLandmark.NOSE to "nose",
                        PoseLandmark.LEFT_SHOULDER to "left_shoulder",
                        PoseLandmark.RIGHT_SHOULDER to "right_shoulder",
                        PoseLandmark.LEFT_ELBOW to "left_elbow",
                        PoseLandmark.RIGHT_ELBOW to "right_elbow",
                        PoseLandmark.LEFT_WRIST to "left_wrist",
                        PoseLandmark.RIGHT_WRIST to "right_wrist",
                        PoseLandmark.LEFT_HIP to "left_hip",
                        PoseLandmark.RIGHT_HIP to "right_hip",
                        PoseLandmark.LEFT_KNEE to "left_knee",
                        PoseLandmark.RIGHT_KNEE to "right_knee",
                        PoseLandmark.LEFT_ANKLE to "left_ankle",
                        PoseLandmark.RIGHT_ANKLE to "right_ankle"
                    )

                    for ((landmarkType, name) in landmarkMapping) {
                        val lm = pose.getPoseLandmark(landmarkType)
                        if (lm != null && lm.inFrameLikelihood >= 0.35f) {
                            val rawX = lm.position.x / uprightWidth
                            val rawY = lm.position.y / uprightHeight
                            val normX = if (frontFacing) (1.0f - rawX).coerceIn(0f, 1f) else rawX.coerceIn(0f, 1f)
                            val normY = rawY.coerceIn(0f, 1f)
                            liveKpMap[name] = PoseKeypoint(
                                name = name,
                                x = normX,
                                y = normY,
                                z = lm.position3D.z,
                                visibility = lm.inFrameLikelihood
                            )
                        }
                    }

                    // Compute live joint angles on-device
                    val liveAngles = BuiltInPoseLibrary.computeJointAngles(liveKpMap)
                    val rawResult = OnDevicePoseEvaluator.compare(target, liveAngles)

                    val smoothedResult = if (rawResult.matchScore != null && !rawResult.noPoseDetected) {
                        val smoothedScore = scoreSmoother.smooth(rawResult.matchScore)
                        val isMatched = smoothedScore >= 90
                        rawResult.copy(
                            matchScore = smoothedScore,
                            isMatched = isMatched
                        )
                    } else {
                        rawResult
                    }

                    lastValidResult = smoothedResult
                    onPoseEvaluated(smoothedResult)
                }
            }
            .addOnFailureListener { e ->
                Log.w("PoseAnalyzer", "ML Kit Pose Detection execution failed", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
                isBusy = false
            }
    }

    fun reset() {
        scoreSmoother.reset()
        consecutiveNoPoseFrames = 0
        lastValidResult = null
    }

    fun close() {
        try {
            detector.close()
        } catch (e: Exception) {
            Log.w("PoseAnalyzer", "Error closing pose detector", e)
        }
    }
}

/**
 * Moving average score smoother over a sliding frame window.
 */
class PoseScoreSmoother(private val windowSize: Int = 4) {
    private val buffer = mutableListOf<Int>()

    fun smooth(newScore: Int): Int {
        buffer.add(newScore)
        if (buffer.size > windowSize) {
            buffer.removeAt(0)
        }
        return buffer.average().toInt()
    }

    fun reset() {
        buffer.clear()
    }
}

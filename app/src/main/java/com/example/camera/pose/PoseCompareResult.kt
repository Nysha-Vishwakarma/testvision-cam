package com.example.camera.pose

/**
 * Directional feedback for a specific body limb/joint.
 */
data class LimbCorrection(
    val limb: String,
    val instruction: String,
    val deltaDegrees: Float,
    val status: String // "ALIGNED", "ADJUST_UP", "ADJUST_DOWN", "ADJUST_EXTEND", "ADJUST_BEND"
)

/**
 * Result of comparing live pose against target skeleton.
 */
data class PoseCompareResult(
    val noPoseDetected: Boolean = false,
    val matchScore: Int? = null, // 0 to 100, null if no pose detected
    val isMatched: Boolean = false,
    val status: String = "NO_POSE_DETECTED",
    val primaryFeedback: String = "No pose detected — step into frame",
    val corrections: List<LimbCorrection> = emptyList(),
    val alignedLimbs: List<String> = emptyList()
)

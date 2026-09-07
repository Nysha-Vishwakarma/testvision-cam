package com.example.camera.pose

import kotlin.math.abs

/**
 * High-speed mathematical evaluator that compares target pose skeleton with user posture.
 * Provides instant on-device feedback with zero network latency, and serves as an
 * autonomous fallback if the Python FastAPI backend is offline or unreachable.
 */
object OnDevicePoseEvaluator {

    private const val TOLERANCE_DEG = 18f

    fun compare(target: PoseSkeleton, liveAngles: Map<String, Float>): PoseCompareResult {
        val targetAngles = target.jointAngles
        val corrections = mutableListOf<LimbCorrection>()
        val alignedLimbs = mutableListOf<String>()
        var totalPenalty = 0f
        var evaluatedCount = 0

        val jointDisplayNames = mapOf(
            "left_elbow" to "Left elbow",
            "right_elbow" to "Right elbow",
            "left_shoulder" to "Left shoulder",
            "right_shoulder" to "Right shoulder",
            "left_knee" to "Left knee",
            "right_knee" to "Right knee"
        )

        for ((joint, targetDeg) in targetAngles) {
            val liveDeg = liveAngles[joint]
            if (liveDeg != null) {
                evaluatedCount++
                val diff = liveDeg - targetDeg
                val absDiff = abs(diff)
                val displayName = jointDisplayNames[joint] ?: joint.replace("_", " ").replaceFirstChar { it.uppercase() }

                if (absDiff <= TOLERANCE_DEG) {
                    alignedLimbs.add(joint)
                    corrections.add(
                        LimbCorrection(
                            limb = joint,
                            instruction = "$displayName: aligned",
                            deltaDegrees = absDiff,
                            status = "ALIGNED"
                        )
                    )
                } else {
                    val penalty = (absDiff * 0.75f).coerceAtMost(25f)
                    totalPenalty += penalty

                    val (instruction, status) = if (joint.contains("elbow") || joint.contains("knee")) {
                        if (diff < 0) {
                            "$displayName: straighten ${absDiff.toInt()}°" to "ADJUST_EXTEND"
                        } else {
                            "$displayName: bend ${absDiff.toInt()}°" to "ADJUST_BEND"
                        }
                    } else {
                        if (diff < 0) {
                            "$displayName: raise ${absDiff.toInt()}°" to "ADJUST_UP"
                        } else {
                            "$displayName: lower ${absDiff.toInt()}°" to "ADJUST_DOWN"
                        }
                    }

                    corrections.add(
                        LimbCorrection(
                            limb = joint,
                            instruction = instruction,
                            deltaDegrees = absDiff,
                            status = status
                        )
                    )
                }
            }
        }

        if (evaluatedCount < 3) {
            return PoseCompareResult(
                noPoseDetected = true,
                matchScore = null,
                isMatched = false,
                status = "NO_POSE_DETECTED",
                primaryFeedback = "No pose detected — step into frame",
                corrections = emptyList(),
                alignedLimbs = emptyList()
            )
        }

        val rawScore = 100f - (totalPenalty / evaluatedCount.coerceAtLeast(1) * 2.8f)
        val score = rawScore.coerceIn(0f, 100f).toInt()
        val misaligned = corrections.filter { it.status != "ALIGNED" }.sortedByDescending { it.deltaDegrees }

        val (status, primaryFeedback) = when {
            score >= 90 -> "EXCELLENT" to "Flawless pose! Hold steady for photo"
            score >= 70 -> "GOOD" to (misaligned.firstOrNull()?.instruction ?: "Looking sharp! Minor adjustment")
            else -> "ADJUSTING" to (misaligned.firstOrNull()?.instruction ?: "Align limbs with the golden skeleton")
        }

        return PoseCompareResult(
            noPoseDetected = false,
            matchScore = score,
            isMatched = score >= 90,
            status = status,
            primaryFeedback = primaryFeedback,
            corrections = corrections,
            alignedLimbs = alignedLimbs
        )
    }
}

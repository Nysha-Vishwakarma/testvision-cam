package com.example.camera.pose

import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Curated library of pre-computed poses.
 * Allows the mobile app to function instantly without waiting for server responses
 * or when offline.
 */
object BuiltInPoseLibrary {

    val items: List<PoseLibraryItem> by lazy {
        listOf(
            createPose(
                id = "casual_lean",
                name = "Casual Lean",
                category = "Casual",
                description = "Relaxed street-style lean with hand in pocket and slight torso angle.",
                difficulty = "Easy",
                thumbnailUrl = "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=300",
                rawKeypoints = listOf(
                    "nose" to (0.48f to 0.16f),
                    "left_shoulder" to (0.40f to 0.26f),
                    "right_shoulder" to (0.58f to 0.28f),
                    "left_elbow" to (0.35f to 0.38f),
                    "right_elbow" to (0.62f to 0.40f),
                    "left_wrist" to (0.41f to 0.49f),
                    "right_wrist" to (0.58f to 0.51f),
                    "left_hip" to (0.43f to 0.52f),
                    "right_hip" to (0.55f to 0.53f),
                    "left_knee" to (0.44f to 0.70f),
                    "right_knee" to (0.58f to 0.73f),
                    "left_ankle" to (0.45f to 0.90f),
                    "right_ankle" to (0.60f to 0.91f)
                )
            ),
            createPose(
                id = "golden_hour_portrait",
                name = "Golden Hour Portrait",
                category = "Portraits",
                description = "Elegant chest-up profile with gentle hand-to-chin touch and angled gaze.",
                difficulty = "Easy",
                thumbnailUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300",
                rawKeypoints = listOf(
                    "nose" to (0.52f to 0.24f),
                    "left_shoulder" to (0.38f to 0.38f),
                    "right_shoulder" to (0.62f to 0.39f),
                    "left_elbow" to (0.42f to 0.52f),
                    "right_elbow" to (0.64f to 0.55f),
                    "left_wrist" to (0.51f to 0.32f),
                    "right_wrist" to (0.60f to 0.68f),
                    "left_hip" to (0.42f to 0.76f),
                    "right_hip" to (0.58f to 0.77f),
                    "left_knee" to (0.42f to 0.92f),
                    "right_knee" to (0.58f to 0.92f),
                    "left_ankle" to (0.42f to 0.98f),
                    "right_ankle" to (0.58f to 0.98f)
                )
            ),
            createPose(
                id = "runway_stride",
                name = "Runway Stride",
                category = "Streetwear",
                description = "Dynamic walking motion with forward stride, swinging arms, and elongating posture.",
                difficulty = "Medium",
                thumbnailUrl = "https://images.unsplash.com/photo-1509631179647-0177331693ae?w=300",
                rawKeypoints = listOf(
                    "nose" to (0.50f to 0.14f),
                    "left_shoulder" to (0.42f to 0.24f),
                    "right_shoulder" to (0.58f to 0.24f),
                    "left_elbow" to (0.36f to 0.35f),
                    "right_elbow" to (0.64f to 0.34f),
                    "left_wrist" to (0.32f to 0.46f),
                    "right_wrist" to (0.67f to 0.44f),
                    "left_hip" to (0.46f to 0.50f),
                    "right_hip" to (0.54f to 0.50f),
                    "left_knee" to (0.40f to 0.68f),
                    "right_knee" to (0.60f to 0.70f),
                    "left_ankle" to (0.35f to 0.88f),
                    "right_ankle" to (0.65f to 0.89f)
                )
            ),
            createPose(
                id = "confident_cross_arm",
                name = "Confident Cross-Arm",
                category = "Editorial",
                description = "Bold editorial look with folded arms, squared shoulders, and poised gaze.",
                difficulty = "Easy",
                thumbnailUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300",
                rawKeypoints = listOf(
                    "nose" to (0.50f to 0.15f),
                    "left_shoulder" to (0.37f to 0.25f),
                    "right_shoulder" to (0.63f to 0.25f),
                    "left_elbow" to (0.35f to 0.40f),
                    "right_elbow" to (0.65f to 0.40f),
                    "left_wrist" to (0.56f to 0.42f),
                    "right_wrist" to (0.44f to 0.42f),
                    "left_hip" to (0.43f to 0.54f),
                    "right_hip" to (0.57f to 0.54f),
                    "left_knee" to (0.44f to 0.72f),
                    "right_knee" to (0.56f to 0.72f),
                    "left_ankle" to (0.44f to 0.90f),
                    "right_ankle" to (0.56f to 0.90f)
                )
            ),
            createPose(
                id = "athletic_stretch",
                name = "Athletic Stretch",
                category = "Fitness",
                description = "Dynamic side stretch with raised arm over head and bent lateral stance.",
                difficulty = "Pro",
                thumbnailUrl = "https://images.unsplash.com/photo-1518611012118-696072aa579a?w=300",
                rawKeypoints = listOf(
                    "nose" to (0.48f to 0.17f),
                    "left_shoulder" to (0.39f to 0.27f),
                    "right_shoulder" to (0.59f to 0.26f),
                    "left_elbow" to (0.33f to 0.37f),
                    "right_elbow" to (0.64f to 0.14f),
                    "left_wrist" to (0.40f to 0.48f),
                    "right_wrist" to (0.53f to 0.06f),
                    "left_hip" to (0.42f to 0.53f),
                    "right_hip" to (0.56f to 0.53f),
                    "left_knee" to (0.38f to 0.71f),
                    "right_knee" to (0.60f to 0.73f),
                    "left_ankle" to (0.36f to 0.91f),
                    "right_ankle" to (0.63f to 0.92f)
                )
            ),
            createPose(
                id = "hands_on_hips",
                name = "Power Stance",
                category = "Editorial",
                description = "Empowered fashion stance with hands anchored on hips, elbows angled out, and squared shoulders.",
                difficulty = "Easy",
                thumbnailUrl = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=300",
                rawKeypoints = listOf(
                    "nose" to (0.50f to 0.16f),
                    "left_shoulder" to (0.38f to 0.26f),
                    "right_shoulder" to (0.62f to 0.26f),
                    "left_elbow" to (0.28f to 0.38f),
                    "right_elbow" to (0.72f to 0.38f),
                    "left_wrist" to (0.40f to 0.49f),
                    "right_wrist" to (0.60f to 0.49f),
                    "left_hip" to (0.43f to 0.50f),
                    "right_hip" to (0.57f to 0.50f),
                    "left_knee" to (0.41f to 0.70f),
                    "right_knee" to (0.59f to 0.70f),
                    "left_ankle" to (0.39f to 0.90f),
                    "right_ankle" to (0.61f to 0.90f)
                )
            ),
            createPose(
                id = "over_shoulder_glance",
                name = "Over-the-Shoulder",
                category = "Portraits",
                description = "Dramatic three-quarter profile turning over the shoulder towards the camera with relaxed poise.",
                difficulty = "Medium",
                thumbnailUrl = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=300",
                rawKeypoints = listOf(
                    "nose" to (0.42f to 0.18f),
                    "left_shoulder" to (0.46f to 0.28f),
                    "right_shoulder" to (0.63f to 0.30f),
                    "left_elbow" to (0.40f to 0.41f),
                    "right_elbow" to (0.68f to 0.45f),
                    "left_wrist" to (0.44f to 0.35f),
                    "right_wrist" to (0.65f to 0.58f),
                    "left_hip" to (0.47f to 0.53f),
                    "right_hip" to (0.58f to 0.53f),
                    "left_knee" to (0.46f to 0.72f),
                    "right_knee" to (0.58f to 0.72f),
                    "left_ankle" to (0.46f to 0.91f),
                    "right_ankle" to (0.58f to 0.91f)
                )
            ),
            createPose(
                id = "seated_relaxed",
                name = "Coffee Shop Sit",
                category = "Casual",
                description = "Relaxed seated posture with bent knees, upright core, and forearms resting casually on knees.",
                difficulty = "Easy",
                thumbnailUrl = "https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=300",
                rawKeypoints = listOf(
                    "nose" to (0.50f to 0.20f),
                    "left_shoulder" to (0.39f to 0.32f),
                    "right_shoulder" to (0.61f to 0.32f),
                    "left_elbow" to (0.34f to 0.45f),
                    "right_elbow" to (0.66f to 0.45f),
                    "left_wrist" to (0.41f to 0.56f),
                    "right_wrist" to (0.59f to 0.56f),
                    "left_hip" to (0.42f to 0.58f),
                    "right_hip" to (0.58f to 0.58f),
                    "left_knee" to (0.36f to 0.72f),
                    "right_knee" to (0.64f to 0.72f),
                    "left_ankle" to (0.38f to 0.90f),
                    "right_ankle" to (0.62f to 0.90f)
                )
            ),
            createPose(
                id = "warrior_balance",
                name = "Warrior Balance",
                category = "Fitness",
                description = "Symmetrical grounded posture with wide base stance and arms extended horizontally with poise.",
                difficulty = "Medium",
                thumbnailUrl = "https://images.unsplash.com/photo-1506126613408-eca07ce68773?w=300",
                rawKeypoints = listOf(
                    "nose" to (0.50f to 0.16f),
                    "left_shoulder" to (0.42f to 0.27f),
                    "right_shoulder" to (0.58f to 0.27f),
                    "left_elbow" to (0.27f to 0.27f),
                    "right_elbow" to (0.73f to 0.27f),
                    "left_wrist" to (0.13f to 0.27f),
                    "right_wrist" to (0.87f to 0.27f),
                    "left_hip" to (0.45f to 0.52f),
                    "right_hip" to (0.55f to 0.52f),
                    "left_knee" to (0.38f to 0.70f),
                    "right_knee" to (0.62f to 0.70f),
                    "left_ankle" to (0.32f to 0.90f),
                    "right_ankle" to (0.68f to 0.90f)
                )
            ),
            createPose(
                id = "jacket_drape",
                name = "Jacket Drape",
                category = "Streetwear",
                description = "Chic streetwear attitude with hand hooked casually over shoulder and slight hip pop.",
                difficulty = "Medium",
                thumbnailUrl = "https://images.unsplash.com/photo-1488161628813-04466f872be2?w=300",
                rawKeypoints = listOf(
                    "nose" to (0.51f to 0.16f),
                    "left_shoulder" to (0.40f to 0.26f),
                    "right_shoulder" to (0.60f to 0.27f),
                    "left_elbow" to (0.32f to 0.36f),
                    "right_elbow" to (0.68f to 0.40f),
                    "left_wrist" to (0.42f to 0.20f),
                    "right_wrist" to (0.65f to 0.56f),
                    "left_hip" to (0.44f to 0.52f),
                    "right_hip" to (0.57f to 0.51f),
                    "left_knee" to (0.46f to 0.71f),
                    "right_knee" to (0.58f to 0.72f),
                    "left_ankle" to (0.48f to 0.91f),
                    "right_ankle" to (0.60f to 0.91f)
                )
            )
        )
    }

    private fun createPose(
        id: String,
        name: String,
        category: String,
        description: String,
        difficulty: String,
        thumbnailUrl: String,
        rawKeypoints: List<Pair<String, Pair<Float, Float>>>
    ): PoseLibraryItem {
        val keypoints = rawKeypoints.map { (kName, coords) ->
            PoseKeypoint(
                name = kName,
                x = coords.first,
                y = coords.second
            )
        }
        val kpMap = keypoints.associateBy { it.name }
        val angles = computeJointAngles(kpMap)

        val skeleton = PoseSkeleton(
            id = id,
            name = name,
            keypoints = keypoints,
            jointAngles = angles
        )

        return PoseLibraryItem(
            id = id,
            name = name,
            category = category,
            description = description,
            difficulty = difficulty,
            thumbnailUrl = thumbnailUrl,
            skeleton = skeleton
        )
    }

    fun computeJointAngles(kpMap: Map<String, PoseKeypoint>): Map<String, Float> {
        val angles = mutableMapOf<String, Float>()

        fun angle(p1Name: String, p2Name: String, p3Name: String): Float? {
            val p1 = kpMap[p1Name] ?: return null
            val p2 = kpMap[p2Name] ?: return null
            val p3 = kpMap[p3Name] ?: return null

            val v1x = p1.x - p2.x
            val v1y = p1.y - p2.y
            val v2x = p3.x - p2.x
            val v2y = p3.y - p2.y

            val dot = v1x * v2x + v1y * v2y
            val mag1 = sqrt(v1x * v1x + v1y * v1y)
            val mag2 = sqrt(v2x * v2x + v2y * v2y)

            if (mag1 < 1e-6f || mag2 < 1e-6f) return 0f
            val cosVal = (dot / (mag1 * mag2)).coerceIn(-1f, 1f)
            return Math.toDegrees(acos(cosVal.toDouble())).toFloat()
        }

        angle("left_shoulder", "left_elbow", "left_wrist")?.let { angles["left_elbow"] = it }
        angle("right_shoulder", "right_elbow", "right_wrist")?.let { angles["right_elbow"] = it }
        angle("left_elbow", "left_shoulder", "left_hip")?.let { angles["left_shoulder"] = it }
        angle("right_elbow", "right_shoulder", "right_hip")?.let { angles["right_shoulder"] = it }
        angle("left_hip", "left_knee", "left_ankle")?.let { angles["left_knee"] = it }
        angle("right_hip", "right_knee", "right_ankle")?.let { angles["right_knee"] = it }

        return angles
    }
}

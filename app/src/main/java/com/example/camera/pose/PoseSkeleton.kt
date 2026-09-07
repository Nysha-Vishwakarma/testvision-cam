package com.example.camera.pose

/**
 * Normalized pose skeleton model containing keypoints and computed joint angles.
 */
data class PoseSkeleton(
    val id: String,
    val name: String,
    val keypoints: List<PoseKeypoint>,
    val jointAngles: Map<String, Float> = emptyMap()
) {
    val keypointsByName: Map<String, PoseKeypoint> by lazy {
        keypoints.associateBy { it.name }
    }
}

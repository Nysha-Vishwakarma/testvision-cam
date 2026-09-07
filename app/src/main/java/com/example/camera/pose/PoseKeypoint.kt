package com.example.camera.pose

/**
 * Normalized keypoint on a 2D/3D camera sensor plane.
 * x, y are normalized to [0.0, 1.0] relative to viewfinder dimensions.
 */
data class PoseKeypoint(
    val name: String,
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val visibility: Float = 1f
)

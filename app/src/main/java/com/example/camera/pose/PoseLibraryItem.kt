package com.example.camera.pose

/**
 * Pre-computed pose item in the pose library.
 */
data class PoseLibraryItem(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val difficulty: String, // "Easy", "Medium", "Pro"
    val thumbnailUrl: String,
    val skeleton: PoseSkeleton
)

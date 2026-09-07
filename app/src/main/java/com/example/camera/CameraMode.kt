package com.example.camera

/**
 * Camera operational modes for camera switcher.
 * PHOTO: Clean standard photo camera without filters or assistive overlays.
 * FILTER: AR Face tracking filters with live rendering.
 * POSE_GUIDE: Posture and framing guides.
 * AI_PHOTOGRAPHER: Intelligent scene detection and automatic framing.
 */
enum class CameraMode(val title: String, val shortLabel: String = title) {
    PHOTO("Photo", "Photo"),
    FILTER("Filter", "Filter"),
    POSE_GUIDE("Pose Guide", "Pose Guide"),
    AI_PHOTOGRAPHER("AI Photographer", "AI Photo")
}

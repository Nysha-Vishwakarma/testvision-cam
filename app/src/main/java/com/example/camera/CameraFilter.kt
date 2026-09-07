package com.example.camera

/**
 * Available real-time face tracking filters.
 */
enum class CameraFilter(
    val id: String,
    val displayName: String,
    val subtitle: String
) {
    NONE("none", "Clean", "Natural"),
    NEON_GLASSES("neon_glasses", "Cyber", "Neon Shades"),
    AVIATOR_SHADES("aviator_shades", "Aviator", "Classic Wire"),
    CAT_EARS("cat_ears", "Kitty", "Cute Ears"),
    CELESTIAL_HALO("celestial_halo", "Halo", "Golden Radiance"),
    CYBER_MESH("cyber_mesh", "Mesh", "Tracking HUD")
}

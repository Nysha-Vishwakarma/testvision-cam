package com.example.camera

import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.compose.ui.geometry.Offset

/**
 * State representing camera settings and UI controls.
 */
data class CameraUiState(
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    val isGridVisible: Boolean = false,
    val zoomRatio: Float = 1.0f,
    val minZoom: Float = 1.0f,
    val maxZoom: Float = 5.0f,
    val exposureIndex: Int = 0,
    val minExposure: Int = -4,
    val maxExposure: Int = 4,
    val focusPoint: Offset? = null,
    val focusTriggerId: Long = 0L,
    val focusState: FocusState = FocusState.IDLE,
    val isCapturing: Boolean = false,
    val lastCapturedUri: Uri? = null,
    val previewingUri: Uri? = null,
    val isExposureSliderVisible: Boolean = false,
    val captureFlashTrigger: Long = 0L,
    val currentMode: CameraMode = CameraMode.PHOTO,
    val activeFilter: CameraFilter = CameraFilter.NONE,
    val faceLandmarks: FaceLandmarkData? = null
)

/**
 * State of autofocus execution.
 */
enum class FocusState {
    IDLE,
    FOCUSING,
    LOCKED,
    FAILED
}

/**
 * Layout placement for the live zoom indicator pill.
 * In AI Photographer mode, it anchors to the top-right to prevent overlap with
 * the centered composition guidance pill.
 */
enum class ZoomPillPlacement {
    TOP_CENTER,
    TOP_RIGHT
}

fun getZoomPillPlacement(mode: CameraMode): ZoomPillPlacement =
    if (mode == CameraMode.AI_PHOTOGRAPHER) ZoomPillPlacement.TOP_RIGHT else ZoomPillPlacement.TOP_CENTER

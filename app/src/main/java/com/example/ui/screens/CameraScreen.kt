package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.camera.CameraControls
import com.example.camera.CameraMode
import com.example.camera.ZoomPillPlacement
import com.example.camera.getZoomPillPlacement
import com.example.camera.aiphoto.rememberAIPhotographerController
import com.example.camera.pose.PoseGuideController
import com.example.camera.pose.rememberPoseGuideController
import com.example.camera.smartgrid.rememberSmartGridController
import com.example.camera.rememberCameraControls
import com.example.ui.components.AIPhotographerOverlay
import com.example.ui.components.AISettingsDialog
import com.example.ui.components.BottomControls
import com.example.ui.components.ExposureSlider
import com.example.ui.components.FaceFilterOverlay
import com.example.ui.components.FilterCarousel
import com.example.ui.components.FocusReticle
import com.example.ui.components.GlassPanel
import com.example.ui.components.GridOverlay
import com.example.ui.components.InfoModal
import com.example.ui.components.ModeStatusOverlay
import com.example.ui.components.ModeSwitcher
import com.example.ui.components.PoseFeedbackPill
import com.example.ui.components.PoseQuickBar
import com.example.ui.components.PoseSelectionSheet
import com.example.ui.components.PoseSkeletonOverlay
import com.example.ui.components.TopControls
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Main CameraScreen component.
 * Integrates full-screen live preview, ML Kit face detection filter rendering,
 * 3-way mode switcher (Filter, Pose Guide, AI Photographer), filter carousel,
 * tap-to-focus, pinch-to-zoom, exposure compensation, and photo capture.
 */
@Composable
fun CameraScreen(
    modifier: Modifier = Modifier,
    cameraControls: CameraControls = rememberCameraControls()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    if (!hasCameraPermission) {
        PermissionScreen(
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            modifier = modifier
        )
        return
    }

    val uiState = cameraControls.uiState
    val poseGuideController = rememberPoseGuideController()
    val poseGuideState = poseGuideController.uiState
    val aiPhotographerController = rememberAIPhotographerController()
    val aiPhotoState = aiPhotographerController.uiState
    val smartGridController = rememberSmartGridController()
    var isInfoModalVisible by remember { mutableStateOf(false) }
    var isAISettingsVisible by remember { mutableStateOf(false) }

    // Attach real-time on-device Pose Detection analyzer to CameraControls
    LaunchedEffect(poseGuideController) {
        cameraControls.attachPoseGuide(
            targetSkeletonProvider = { poseGuideController.uiState.targetSkeleton },
            onPoseEvaluated = { result ->
                poseGuideController.onLivePoseEvaluated(result)
            }
        )
    }

    // Reset pose evaluation state when exiting Pose Guide mode
    LaunchedEffect(uiState.currentMode) {
        if (uiState.currentMode != CameraMode.POSE_GUIDE) {
            poseGuideController.resetEvaluation()
        }
    }

    // Manage AI Photographer snapshot-to-backend loop lifecycle
    // Automatically pauses when info modal or settings dialog is open to avoid background conflict
    LaunchedEffect(uiState.currentMode, isInfoModalVisible, isAISettingsVisible) {
        if (uiState.currentMode == CameraMode.AI_PHOTOGRAPHER && !isInfoModalVisible && !isAISettingsVisible) {
            aiPhotographerController.startAnalysisLoop(cameraControls, context)
        } else {
            aiPhotographerController.stopAnalysisLoop()
        }
    }

    // Manage Smart Grid periodic deep pose telemetry loop to backend
    // Strictly active ONLY when Grid is visible in base CameraMode.PHOTO, and stops immediately otherwise
    LaunchedEffect(uiState.currentMode, uiState.isGridVisible, isInfoModalVisible) {
        val shouldRunSmartGridTelemetry = uiState.currentMode == CameraMode.PHOTO &&
                uiState.isGridVisible &&
                !isInfoModalVisible
        if (shouldRunSmartGridTelemetry) {
            smartGridController.startTelemetryLoop(cameraControls)
        } else {
            smartGridController.stopTelemetryLoop()
        }
    }

    // Tactile haptic feedback tick when match crosses the threshold (isMatched = true)
    var previousMatched by remember { mutableStateOf(false) }
    LaunchedEffect(poseGuideState.isMatched) {
        if (poseGuideState.isMatched && !previousMatched && uiState.currentMode == CameraMode.POSE_GUIDE) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        previousMatched = poseGuideState.isMatched
    }

    // Shutter capture flash effect
    val flashAlpha = remember { Animatable(0f) }
    LaunchedEffect(uiState.captureFlashTrigger) {
        if (uiState.captureFlashTrigger > 0L) {
            flashAlpha.snapTo(0.75f)
            flashAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            )
        }
    }

    // Clean unbind on disposal
    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraControls.unbind()
            aiPhotographerController.stopAnalysisLoop()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Full-screen Camera Viewfinder with TextureView-backed COMPATIBLE mode
        // preventing SurfaceView BufferQueue abandonment during Compose recomposition & overlays
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    cameraControls.bindCamera(ctx, lifecycleOwner, this)
                }
            },
            onRelease = {
                cameraControls.unbind()
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1.0f) {
                            cameraControls.pinchZoom(zoom)
                        }
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downPos = down.position
                            var totalDragX = 0f
                            var isMultiTouch = false
                            var isDrag = false

                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.size > 1) {
                                    isMultiTouch = true
                                }
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) {
                                    break
                                }
                                val dragX = change.position.x - downPos.x
                                val dragY = change.position.y - downPos.y
                                if (hypot(dragX.toDouble(), dragY.toDouble()) > 25.0) {
                                    isDrag = true
                                    totalDragX = dragX
                                }
                            }

                            if (!isMultiTouch) {
                                if (isDrag && abs(totalDragX) > 65f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (totalDragX < 0) {
                                        cameraControls.nextMode()
                                    } else {
                                        cameraControls.previousMode()
                                    }
                                } else if (!isDrag) {
                                    cameraControls.tapToFocus(downPos.x, downPos.y)
                                }
                            }
                        }
                    }
                }
        )

        // Real-time Face Filter Skia Canvas Overlay (runs in Filter mode)
        if (uiState.currentMode == CameraMode.FILTER) {
            FaceFilterOverlay(
                activeFilter = uiState.activeFilter,
                faceLandmarks = uiState.faceLandmarks,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Real-time Pose Guide Reference Skeleton Overlay (runs in Pose Guide mode)
        if (uiState.currentMode == CameraMode.POSE_GUIDE) {
            PoseSkeletonOverlay(
                targetSkeleton = poseGuideState.targetSkeleton,
                alignedLimbs = poseGuideState.alignedLimbs,
                isMatched = poseGuideState.isMatched,
                noPoseDetected = poseGuideState.noPoseDetected,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Rule-of-Thirds Grid Overlay (with real-time Smart Grid tilt & intersection cues)
        GridOverlay(
            isVisible = uiState.isGridVisible,
            faceLandmarks = uiState.faceLandmarks,
            modifier = Modifier.fillMaxSize()
        )

        // Tap-to-Focus Reticle
        FocusReticle(
            focusPoint = uiState.focusPoint,
            triggerId = uiState.focusTriggerId,
            focusState = uiState.focusState,
            modifier = Modifier.fillMaxSize()
        )

        // Floating Top Capsule: Flash, Grid, & Info controls
        TopControls(
            flashMode = uiState.flashMode,
            onToggleFlash = { cameraControls.toggleFlash() },
            isGridVisible = uiState.isGridVisible,
            onToggleGrid = { cameraControls.toggleGrid() },
            onOpenInfo = { isInfoModalVisible = true },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
        )

        // Filter Mode: "Looking for a face…" guidance pill when filter is active and no face detected
        if (uiState.currentMode == CameraMode.FILTER) {
            ModeStatusOverlay(
                currentMode = uiState.currentMode,
                activeFilter = uiState.activeFilter,
                faceLandmarks = uiState.faceLandmarks,
                onReturnToBase = { cameraControls.returnToBaseCamera() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 76.dp)
            )
        }

        // Pose Guide Live Score & Directional Guidance Pill
        if (uiState.currentMode == CameraMode.POSE_GUIDE) {
            PoseFeedbackPill(
                noPoseDetected = poseGuideState.noPoseDetected,
                matchScore = poseGuideState.matchScore,
                primaryFeedback = poseGuideState.primaryFeedback,
                isMatched = poseGuideState.isMatched,
                isReconnecting = poseGuideState.isReconnecting,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 74.dp)
            )
        }

        // AI Photographer Live Composition & Framing Guidance Overlay
        if (uiState.currentMode == CameraMode.AI_PHOTOGRAPHER) {
            AIPhotographerOverlay(
                state = aiPhotoState,
                onOpenSettings = { isAISettingsVisible = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 76.dp)
            )
        }

        // Slim Vertical Exposure Slider (Right Edge)
        // Perfectly aligned in the active camera viewfinder area, vertically centered between
        // the top status capsule (80dp) and the bottom controls cluster (215dp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 80.dp, bottom = 215.dp, end = 16.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            ExposureSlider(
                exposureIndex = uiState.exposureIndex,
                minExposure = uiState.minExposure,
                maxExposure = uiState.maxExposure,
                onExposureChange = { newExp -> cameraControls.setExposure(newExp) },
                isVisible = uiState.isExposureSliderVisible
            )
        }

        // Zoom Level Pill Indicator
        // Mode-conditional placement:
        // In AI Photographer mode: Anchored to top-right corner to prevent overlapping
        // the centered composition analysis pill or colliding with top controls.
        // In all other modes (Base camera, Filters, Pose Guide): Retains existing top-center position.
        val zoomPillModifier = when (getZoomPillPlacement(uiState.currentMode)) {
            ZoomPillPlacement.TOP_RIGHT -> Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 16.dp)
            ZoomPillPlacement.TOP_CENTER -> Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 74.dp)
        }

        AnimatedVisibility(
            visible = uiState.zoomRatio > 1.05f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = zoomPillModifier
        ) {
            GlassPanel(
                shape = RoundedCornerShape(14.dp),
                elevation = 4.dp,
                modifier = Modifier.testTag("zoom_indicator_pill")
            ) {
                Text(
                    text = String.format(Locale.US, "%.1fx", uiState.zoomRatio),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Bottom Control Cluster:
        // 1. Horizontal Filter Carousel (visible in Filter mode)
        // 2. 3-Way Mode Switcher (Filter | Pose Guide | AI Photographer)
        // 3. Shutter Button, Thumbnail, Camera Switch Row
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp)
        ) {
            // Filter Carousel (animated slide in/out based on mode)
            AnimatedVisibility(
                visible = uiState.currentMode == CameraMode.FILTER,
                enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { 30 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { 30 }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Quick return to Camera chip
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { cameraControls.returnToBaseCamera() }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                            .testTag("exit_filter_mode_chip")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Return to camera",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Back to Camera",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    FilterCarousel(
                        activeFilter = uiState.activeFilter,
                        onFilterSelected = { filter -> cameraControls.setFilter(filter) },
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }

            // Pose Guide Quick Bar (animated slide in/out based on mode)
            AnimatedVisibility(
                visible = uiState.currentMode == CameraMode.POSE_GUIDE,
                enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { 30 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { 30 }
            ) {
                PoseQuickBar(
                    selectedPose = poseGuideState.selectedPose,
                    onOpenPoseSheet = { poseGuideController.openPoseSheet() },
                    onReturnToBase = { cameraControls.returnToBaseCamera() },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // AI Photographer Quick Bar (animated slide in/out based on mode)
            AnimatedVisibility(
                visible = uiState.currentMode == CameraMode.AI_PHOTOGRAPHER,
                enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { 30 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { 30 }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { cameraControls.returnToBaseCamera() }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                        .testTag("exit_ai_photo_mode_chip")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Return to camera",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Back to Camera",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Mode Switcher (Photo | Filter | Pose Guide | AI Photo)
            ModeSwitcher(
                currentMode = uiState.currentMode,
                onModeSelected = { mode ->
                    aiPhotographerController.onManualCaptureTriggered()
                    cameraControls.setMode(mode)
                },
                modifier = Modifier.padding(bottom = 14.dp)
            )

            // Primary Shutter & Gallery Controls
            // Tapping the thumbnail launches viewing directly in the device's Gallery app
            BottomControls(
                isCapturing = uiState.isCapturing,
                onShutterClick = {
                    aiPhotographerController.onManualCaptureTriggered()
                    cameraControls.takePhoto(context)
                },
                onSwitchCamera = {
                    aiPhotographerController.onManualCaptureTriggered()
                    cameraControls.switchCamera(context)
                },
                lastCapturedUri = uiState.lastCapturedUri,
                onThumbnailClick = {
                    openInGalleryApp(context, uiState.lastCapturedUri)
                }
            )
        }

        // Shutter Screen Flash
        if (flashAlpha.value > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAlpha.value))
            )
        }

        // Modal Pose Selection & Custom Upload Sheet
        PoseSelectionSheet(
            isVisible = poseGuideState.isSheetVisible,
            poses = poseGuideState.poseLibrary,
            selectedPose = poseGuideState.selectedPose,
            onSelectPose = { pose -> poseGuideController.selectPose(pose) },
            onUploadImage = { uri -> poseGuideController.uploadCustomImage(context, uri) },
            onClose = { poseGuideController.closePoseSheet() }
        )

        // Project Info & Team Credits Glass Modal
        InfoModal(
            isVisible = isInfoModalVisible,
            onDismiss = { isInfoModalVisible = false }
        )

        // AI Photographer & Groq Qwen 3.6-27B Settings Dialog
        if (isAISettingsVisible) {
            AISettingsDialog(
                onDismissRequest = { isAISettingsVisible = false }
            )
        }
    }
}

/**
 * Opens the captured photo or the device's photo gallery app directly.
 */
private fun openInGalleryApp(context: Context, photoUri: Uri?) {
    if (photoUri != null) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(photoUri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            Log.w("CameraScreen", "Could not view photo in gallery via ACTION_VIEW", e)
        }
    }

    // Fallback: Open general gallery collection
    try {
        val galleryIntent = Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(galleryIntent)
    } catch (e: Exception) {
        try {
            val appGalleryIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_GALLERY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appGalleryIntent)
        } catch (e2: Exception) {
            Log.w("CameraScreen", "Could not launch gallery app", e2)
        }
    }
}

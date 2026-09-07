package com.example.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.FocusMeteringResult
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.camera.pose.PoseCompareResult
import com.example.camera.pose.PoseDetectionAnalyzer
import com.example.camera.pose.PoseSkeleton
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Controller handling camera operations: focus, zoom, exposure, capture, and flash.
 * Encapsulates CameraX lifecycle binding, real-time ML Kit face analysis, and exposes reactive state.
 */
class CameraControls(
    private val coroutineScope: CoroutineScope
) {
    var uiState by mutableStateOf(CameraUiState())
        private set

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var faceAnalyzer: FaceDetectionAnalyzer? = null
    private var poseAnalyzer: PoseDetectionAnalyzer? = null
    private var poseTargetSkeletonProvider: (() -> PoseSkeleton?)? = null
    private var onPoseEvaluatedCallback: ((PoseCompareResult) -> Unit)? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentLifecycleOwner: LifecycleOwner? = null
    private var lastFocusTimestamp: Long = 0L

    fun attachPoseGuide(
        targetSkeletonProvider: () -> PoseSkeleton?,
        onPoseEvaluated: (PoseCompareResult) -> Unit
    ) {
        this.poseTargetSkeletonProvider = targetSkeletonProvider
        this.onPoseEvaluatedCallback = onPoseEvaluated
    }

    fun bindCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        viewFinder: PreviewView
    ) {
        this.previewView = viewFinder
        this.currentLifecycleOwner = lifecycleOwner

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                rebindUseCases(context, lifecycleOwner, viewFinder)
            } catch (e: Exception) {
                Log.e("CameraControls", "Failed to bind camera use cases", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun rebindUseCases(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        viewFinder: PreviewView
    ) {
        this.previewView = viewFinder
        val provider = cameraProvider ?: return
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.INITIALIZED)) {
            return
        }
        if (uiState.isCapturing) {
            Log.w("CameraControls", "Rebind use cases deferred: capture currently in progress.")
            return
        }

        try {
            provider.unbindAll()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(uiState.lensFacing)
                .build()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = viewFinder.surfaceProvider
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(uiState.flashMode)
                .build()

            // Downscaled ImageAnalysis pipeline for ML Kit face detection
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(480, 640))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            faceAnalyzer?.close()
            poseAnalyzer?.close()

            val faceDetector = FaceDetectionAnalyzer(
                isFrontCamera = { uiState.lensFacing == CameraSelector.LENS_FACING_FRONT },
                isEnabled = {
                    (uiState.currentMode == CameraMode.FILTER && uiState.activeFilter != CameraFilter.NONE) ||
                    (uiState.isGridVisible && uiState.currentMode != CameraMode.POSE_GUIDE)
                },
                onFaceUpdated = { landmarks ->
                    uiState = uiState.copy(faceLandmarks = landmarks)
                }
            )

            val poseDetector = PoseDetectionAnalyzer(
                isFrontCamera = { uiState.lensFacing == CameraSelector.LENS_FACING_FRONT },
                isEnabled = { uiState.currentMode == CameraMode.POSE_GUIDE },
                targetSkeletonProvider = { poseTargetSkeletonProvider?.invoke() },
                onPoseEvaluated = { result ->
                    onPoseEvaluatedCallback?.invoke(result)
                }
            )

            // Strictly one frame processor active according to mode exclusivity rule
            val delegatingAnalyzer = ImageAnalysis.Analyzer { imageProxy ->
                when {
                    uiState.currentMode == CameraMode.POSE_GUIDE -> poseDetector.analyze(imageProxy)
                    uiState.currentMode == CameraMode.FILTER -> faceDetector.analyze(imageProxy)
                    uiState.isGridVisible -> faceDetector.analyze(imageProxy)
                    else -> imageProxy.close()
                }
            }
            analysis.setAnalyzer(analysisExecutor, delegatingAnalyzer)

            this.imageAnalysis = analysis
            this.faceAnalyzer = faceDetector
            this.poseAnalyzer = poseDetector

            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                analysis
            )

            // Extract camera exposure range and zoom bounds
            camera?.cameraInfo?.let { info ->
                val zoomState = info.zoomState.value
                val minZoom = zoomState?.minZoomRatio ?: 1.0f
                val maxZoom = zoomState?.maxZoomRatio ?: 5.0f
                val currentZoom = zoomState?.zoomRatio ?: 1.0f

                val exposureState = info.exposureState
                val minExp = exposureState.exposureCompensationRange.lower
                val maxExp = exposureState.exposureCompensationRange.upper

                uiState = uiState.copy(
                    minZoom = minZoom,
                    maxZoom = maxZoom.coerceAtMost(8.0f),
                    zoomRatio = currentZoom,
                    minExposure = minExp,
                    maxExposure = maxExp,
                    exposureIndex = exposureState.exposureCompensationIndex
                )
            }
        } catch (e: Exception) {
            Log.e("CameraControls", "Use case binding failed", e)
        }
    }

    fun setMode(mode: CameraMode) {
        if (mode == uiState.currentMode) return
        uiState = uiState.copy(
            currentMode = mode,
            // If switching to PHOTO (standard camera), clear active filter
            activeFilter = if (mode == CameraMode.PHOTO) CameraFilter.NONE else uiState.activeFilter,
            // Only keep face tracking if in FILTER mode or if grid is visible in non-pose modes
            faceLandmarks = when {
                mode == CameraMode.FILTER -> uiState.faceLandmarks
                uiState.isGridVisible && mode != CameraMode.POSE_GUIDE -> uiState.faceLandmarks
                else -> null
            }
        )
        if (mode != CameraMode.POSE_GUIDE) {
            poseAnalyzer?.reset()
        }
    }

    fun returnToBaseCamera() {
        setMode(CameraMode.PHOTO)
    }

    fun nextMode() {
        val modes = CameraMode.entries.toTypedArray()
        val currentIndex = modes.indexOf(uiState.currentMode)
        val nextIndex = (currentIndex + 1).coerceAtMost(modes.size - 1)
        setMode(modes[nextIndex])
    }

    fun previousMode() {
        val modes = CameraMode.entries.toTypedArray()
        val currentIndex = modes.indexOf(uiState.currentMode)
        val prevIndex = (currentIndex - 1).coerceAtLeast(0)
        setMode(modes[prevIndex])
    }

    fun setFilter(filter: CameraFilter) {
        uiState = uiState.copy(activeFilter = filter)
    }

    /**
     * Executes tap-to-focus and metering with readiness checks, gesture debouncing,
     * coordinate conversion via the active preview view's metering point factory,
     * and asynchronous completion observation.
     */
    fun tapToFocus(x: Float, y: Float) {
        val view = previewView ?: run {
            Log.w("CameraControls", "Focus skipped: PreviewView not bound")
            return
        }
        val cam = camera ?: run {
            Log.w("CameraControls", "Focus skipped: Camera not initialized")
            return
        }

        // Readiness check: Ensure PreviewView has non-zero layout dimensions
        if (view.width <= 0 || view.height <= 0) {
            Log.w("CameraControls", "Focus skipped: PreviewView layout not ready (${view.width}x${view.height})")
            return
        }

        // Debounce: Prevent rapid repeat taps (< 350ms) from causing lens hunting
        val now = System.currentTimeMillis()
        if (now - lastFocusTimestamp < 350L) {
            return
        }
        lastFocusTimestamp = now

        // Clamp coordinates to PreviewView boundaries to avoid out-of-range sensor mappings
        val clampedX = x.coerceIn(0f, view.width.toFloat())
        val clampedY = y.coerceIn(0f, view.height.toFloat())

        try {
            // Coordinate mapping: PreviewView.meteringPointFactory accurately transforms
            // View surface coordinates (accounting for FILL_CENTER / aspect ratio scale and offsets)
            // into normalized Camera2 sensor domain coordinates [0, 1].
            val factory = view.meteringPointFactory
            val point = factory.createPoint(clampedX, clampedY)
            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()

            val triggerId = now
            uiState = uiState.copy(
                focusPoint = Offset(clampedX, clampedY),
                focusTriggerId = triggerId,
                focusState = FocusState.FOCUSING,
                isExposureSliderVisible = true
            )

            // CameraControl.startFocusAndMetering returns a ListenableFuture<FocusMeteringResult>
            val focusFuture = cam.cameraControl.startFocusAndMetering(action)
            focusFuture.addListener({
                try {
                    val result = focusFuture.get()
                    if (uiState.focusTriggerId == triggerId) {
                        uiState = uiState.copy(
                            focusState = if (result.isFocusSuccessful) FocusState.LOCKED else FocusState.FAILED
                        )
                    }
                } catch (e: Exception) {
                    Log.w("CameraControls", "Focus future completed with error or cancellation", e)
                    if (uiState.focusTriggerId == triggerId) {
                        uiState = uiState.copy(focusState = FocusState.FAILED)
                    }
                }
            }, ContextCompat.getMainExecutor(view.context))
        } catch (e: Exception) {
            Log.w("CameraControls", "Tap to focus failed to start", e)
            uiState = uiState.copy(focusState = FocusState.FAILED)
        }
    }

    fun pinchZoom(scaleMultiplier: Float) {
        val cam = camera ?: return
        val current = uiState.zoomRatio
        val target = (current * scaleMultiplier).coerceIn(uiState.minZoom, uiState.maxZoom)

        if (target != current) {
            try {
                cam.cameraControl.setZoomRatio(target)
                uiState = uiState.copy(zoomRatio = target)
            } catch (e: Exception) {
                Log.w("CameraControls", "Zoom error", e)
            }
        }
    }

    fun setZoomRatio(targetRatio: Float) {
        val cam = camera ?: return
        val clamped = targetRatio.coerceIn(uiState.minZoom, uiState.maxZoom)
        if (Math.abs(clamped - uiState.zoomRatio) > 0.01f) {
            try {
                cam.cameraControl.setZoomRatio(clamped)
                uiState = uiState.copy(zoomRatio = clamped)
            } catch (e: Exception) {
                Log.w("CameraControls", "setZoomRatio error", e)
            }
        }
    }

    fun autoAdjustZoom(delta: Float) {
        val current = uiState.zoomRatio
        // Clamp auto-zoom step and cap maximum auto-zoom to 1.6x to preserve environmental breathing room
        val maxAutoZoomRatio = 1.6f
        val effectiveDelta = delta.coerceIn(-0.15f, 0.08f)
        val target = (current + effectiveDelta).coerceIn(uiState.minZoom, maxAutoZoomRatio)
        if (Math.abs(target - current) >= 0.04f) {
            setZoomRatio(target)
        }
    }

    fun setExposure(index: Int) {
        val cam = camera ?: return
        val clamped = index.coerceIn(uiState.minExposure, uiState.maxExposure)

        try {
            cam.cameraControl.setExposureCompensationIndex(clamped)
            uiState = uiState.copy(exposureIndex = clamped)
        } catch (e: Exception) {
            Log.w("CameraControls", "Exposure compensation error", e)
        }
    }

    fun autoAdjustExposure(stepDelta: Int) {
        val current = uiState.exposureIndex
        val target = (current + stepDelta).coerceIn(uiState.minExposure, uiState.maxExposure)
        if (target != current) {
            setExposure(target)
        }
    }

    fun getPreviewBitmap(): Bitmap? {
        return previewView?.bitmap
    }

    fun toggleFlash() {
        val nextMode = when (uiState.flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }

        imageCapture?.flashMode = nextMode
        uiState = uiState.copy(flashMode = nextMode)
    }

    fun toggleGrid() {
        val willBeVisible = !uiState.isGridVisible
        uiState = uiState.copy(
            isGridVisible = willBeVisible,
            faceLandmarks = if (!willBeVisible && uiState.currentMode != CameraMode.FILTER) null else uiState.faceLandmarks
        )
    }

    fun switchCamera(context: Context) {
        if (uiState.isCapturing) {
            Log.w("CameraControls", "Switch camera ignored: capture is currently in progress.")
            return
        }

        val newLens = if (uiState.lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }

        uiState = uiState.copy(
            lensFacing = newLens,
            exposureIndex = 0,
            faceLandmarks = null
        )

        val owner = currentLifecycleOwner ?: return
        val view = previewView ?: return
        rebindUseCases(context, owner, view)
    }

    fun takePhoto(context: Context) {
        val capture = imageCapture ?: return
        if (uiState.isCapturing) {
            Log.w("CameraControls", "Take photo ignored: capture already in progress.")
            return
        }
        val owner = currentLifecycleOwner ?: return
        if (!owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            Log.w("CameraControls", "Take photo ignored: lifecycle is not in started state.")
            return
        }
        if (camera == null) {
            Log.w("CameraControls", "Take photo ignored: camera is not bound.")
            return
        }

        uiState = uiState.copy(
            isCapturing = true,
            captureFlashTrigger = System.currentTimeMillis()
        )

        val executor = ContextCompat.getMainExecutor(context)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "IMG_$timeStamp.jpg"

        val currentFilter = uiState.activeFilter
        val currentLandmarks = uiState.faceLandmarks

        val outputOptions: ImageCapture.OutputFileOptions = try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BaseCamera")
                }
            }
            ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()
        } catch (e: Exception) {
            // Safe fallback to internal cache file
            val fallbackFile = File(context.cacheDir, fileName)
            ImageCapture.OutputFileOptions.Builder(fallbackFile).build()
        }

        capture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = outputFileResults.savedUri ?: run {
                        val fallbackFile = File(context.cacheDir, fileName)
                        if (fallbackFile.exists()) Uri.fromFile(fallbackFile) else null
                    }

                    if (savedUri != null && currentFilter != CameraFilter.NONE && currentLandmarks?.isFaceDetected == true) {
                        // Bake filter onto the captured photo asynchronously
                        coroutineScope.launch(Dispatchers.IO) {
                            bakeFilterOntoImage(context, savedUri, currentFilter, currentLandmarks)
                            withContext(Dispatchers.Main) {
                                uiState = uiState.copy(
                                    isCapturing = false,
                                    lastCapturedUri = savedUri
                                )
                            }
                        }
                    } else {
                        uiState = uiState.copy(
                            isCapturing = false,
                            lastCapturedUri = savedUri
                        )
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    if (exception.imageCaptureError == ImageCapture.ERROR_CAMERA_CLOSED ||
                        exception.message?.contains("Camera is closed", ignoreCase = true) == true
                    ) {
                        Log.w("CameraControls", "Photo capture cancelled: camera was rebound or closed (${exception.message})")
                    } else {
                        Log.e("CameraControls", "Photo capture failed: ${exception.message}", exception)
                    }
                    uiState = uiState.copy(isCapturing = false)
                }
            }
        )
    }

    private fun bakeFilterOntoImage(
        context: Context,
        uri: Uri,
        filter: CameraFilter,
        landmarks: FaceLandmarkData
    ) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return
            val decoded = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (decoded != null) {
                val mutable = decoded.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutable)
                FaceFilterRenderer.drawFilter(
                    canvas = canvas,
                    width = mutable.width.toFloat(),
                    height = mutable.height.toFloat(),
                    filter = filter,
                    landmarks = landmarks
                )

                val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                if (outputStream != null) {
                    mutable.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
                    outputStream.flush()
                    outputStream.close()
                }
                decoded.recycle()
                mutable.recycle()
            }
        } catch (e: Exception) {
            Log.e("CameraControls", "Failed baking filter onto photo", e)
        }
    }

    fun openPreview(uri: Uri?) {
        uiState = uiState.copy(previewingUri = uri)
    }

    fun dismissPreview() {
        uiState = uiState.copy(previewingUri = null)
    }

    /**
     * Provides read-only access to the active PreviewView for downscaled snapshot analysis.
     */
    fun getPreviewView(): PreviewView? = previewView

    /**
     * Safely unbinds all CameraX use cases and releases face analyzer resources.
     */
    fun unbind() {
        try {
            cameraProvider?.unbindAll()
            faceAnalyzer?.close()
            poseAnalyzer?.close()
            faceAnalyzer = null
            poseAnalyzer = null
            imageCapture = null
            imageAnalysis = null
            camera = null
            previewView = null
        } catch (e: Exception) {
            Log.w("CameraControls", "Error unbinding camera use cases", e)
        }
    }
}

/**
 * Remember helper function for camera controls in Compose.
 */
@Composable
fun rememberCameraControls(): CameraControls {
    val coroutineScope = rememberCoroutineScope()
    return remember { CameraControls(coroutineScope) }
}

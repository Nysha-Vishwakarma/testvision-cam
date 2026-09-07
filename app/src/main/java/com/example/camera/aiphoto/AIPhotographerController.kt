package com.example.camera.aiphoto

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.camera.CameraControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Controller for AI Photographer Mode.
 * Follows the same modular hook pattern as CameraControls and PoseGuideController.
 * Periodically captures preview snapshot, analyzes framing/lighting with the backend,
 * coordinates auto-zoom, exposure, and auto-capture with visual countdown cues.
 */
class AIPhotographerController(
    private val coroutineScope: CoroutineScope
) {
    var uiState by mutableStateOf(AIPhotographerUiState())
        private set

    private val apiClient = AIPhotographerApiClient()
    private var analysisJob: Job? = null
    private var countdownJob: Job? = null
    private var lastAutoCaptureTime: Long = 0L
    private var sessionId: String = UUID.randomUUID().toString()

    companion object {
        const val SNAPSHOT_INTERVAL_MS = 1000L
        const val AUTO_CAPTURE_COOLDOWN_MS = 4000L
    }

    /**
     * Starts periodic snapshot-to-backend analysis loop.
     * Guaranteed to stop existing jobs before starting anew.
     */
    fun startAnalysisLoop(cameraControls: CameraControls, context: Context) {
        stopAnalysisLoop()
        sessionId = UUID.randomUUID().toString()
        uiState = uiState.copy(
            isAnalyzing = false,
            isAutoCapturing = false,
            countdownRemainingSeconds = 0,
            errorMessage = null,
            framingFeedback = "Analyzing scene composition…",
            lightingFeedback = ""
        )

        analysisJob = coroutineScope.launch {
            while (isActive) {
                delay(SNAPSHOT_INTERVAL_MS)
                if (!isActive) break

                // Do not analyze while auto-capturing countdown is active
                if (uiState.isAutoCapturing) continue

                val bitmap = cameraControls.getPreviewBitmap()
                if (bitmap == null) {
                    continue
                }

                uiState = uiState.copy(isAnalyzing = true)
                val result = apiClient.analyzeFrame(context, bitmap, sessionId)

                if (result.isSuccess) {
                    val comp = result.getOrNull()
                    if (comp != null) {
                        uiState = uiState.copy(
                            score = comp.score,
                            framingFeedback = comp.framingFeedback,
                            lightingFeedback = comp.lightingFeedback,
                            suggestedZoomDelta = comp.suggestedZoomDelta,
                            suggestedMoveDirection = comp.suggestedMoveDirection,
                            readyToCapture = comp.readyToCapture,
                            previousInstructionFollowed = comp.previousInstructionFollowed,
                            correctionNote = comp.correctionNote,
                            guidanceStuck = comp.guidanceStuck,
                            subjectType = comp.subjectType,
                            backendMode = comp.backendMode,
                            isAnalyzing = false,
                            isBackendConnected = true,
                            lastAnalyzedTime = System.currentTimeMillis(),
                            errorMessage = null
                        )

                        // 1. Auto-adjust zoom if recommended by framing analysis
                        if (Math.abs(comp.suggestedZoomDelta) >= 0.05f) {
                            withContext(Dispatchers.Main) {
                                cameraControls.autoAdjustZoom(comp.suggestedZoomDelta)
                            }
                        }

                        // 2. Auto-adjust exposure if recommended by lighting analysis
                        val lightLower = comp.lightingFeedback.lowercase()
                        if (lightLower.contains("dark") || lightLower.contains("underexposed")) {
                            withContext(Dispatchers.Main) {
                                cameraControls.autoAdjustExposure(+1)
                            }
                        } else if (lightLower.contains("bright") || lightLower.contains("overexposed") || lightLower.contains("overexposure")) {
                            withContext(Dispatchers.Main) {
                                cameraControls.autoAdjustExposure(-1)
                            }
                        }

                        // 3. Trigger auto-capture if ready, steady, not stuck, and cooldown satisfied
                        val now = System.currentTimeMillis()
                        if (comp.readyToCapture &&
                            !comp.guidanceStuck &&
                            !uiState.isAutoCapturing &&
                            (now - lastAutoCaptureTime > AUTO_CAPTURE_COOLDOWN_MS)
                        ) {
                            triggerAutoCapture(cameraControls, context)
                        }
                    }
                } else {
                    Log.d("AIPhotographerController", "Backend unreachable or error: ${result.exceptionOrNull()?.message}")
                    uiState = uiState.copy(
                        isAnalyzing = false,
                        isBackendConnected = false,
                        errorMessage = "AI Assistant offline — manual controls active"
                    )
                }
            }
        }
    }

    /**
     * Performs a polished auto-capture sequence with a 1-second visual countdown cue
     * to prevent abrupt or jarring captures.
     */
    private fun triggerAutoCapture(cameraControls: CameraControls, context: Context) {
        countdownJob?.cancel()
        countdownJob = coroutineScope.launch {
            uiState = uiState.copy(
                isAutoCapturing = true,
                countdownRemainingSeconds = 1
            )
            delay(1000L)
            if (!isActive) return@launch

            withContext(Dispatchers.Main) {
                cameraControls.takePhoto(context)
            }
            lastAutoCaptureTime = System.currentTimeMillis()

            delay(600L)
            uiState = uiState.copy(
                isAutoCapturing = false,
                countdownRemainingSeconds = 0,
                readyToCapture = false
            )
        }
    }

    /**
     * Informs the controller that a manual capture occurred, resetting auto-capture timer.
     */
    fun onManualCaptureTriggered() {
        lastAutoCaptureTime = System.currentTimeMillis()
        countdownJob?.cancel()
        countdownJob = null
        uiState = uiState.copy(
            isAutoCapturing = false,
            countdownRemainingSeconds = 0,
            readyToCapture = false,
            guidanceStuck = false
        )
    }

    /**
     * Completely terminates the snapshot analysis loop, countdowns, and timers.
     */
    fun stopAnalysisLoop() {
        analysisJob?.cancel()
        analysisJob = null
        countdownJob?.cancel()
        countdownJob = null
        uiState = uiState.copy(
            isAnalyzing = false,
            isAutoCapturing = false,
            countdownRemainingSeconds = 0
        )
    }
}

/**
 * Composition-safe remember factory for AIPhotographerController.
 */
@Composable
fun rememberAIPhotographerController(
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): AIPhotographerController {
    return remember { AIPhotographerController(coroutineScope) }
}

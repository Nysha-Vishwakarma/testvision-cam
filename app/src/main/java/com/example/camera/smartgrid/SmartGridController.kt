package com.example.camera.smartgrid

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
 * Controller managing the periodic background pose telemetry for Smart Grid.
 * Adheres strictly to the architectural constraints:
 * - Completely detached from the real-time frame path (real-time runs on-device).
 * - Fires periodic snapshots every 3.5 seconds.
 * - Only active in base camera mode when grid lines are toggled ON.
 * - Stops immediately when grid is toggled off or mode changes.
 */
class SmartGridController(
    private val coroutineScope: CoroutineScope
) {
    private val apiClient = SmartGridApiClient()
    private var telemetryJob: Job? = null
    private var sessionId: String = UUID.randomUUID().toString()

    var latestTelemetryNote by mutableStateOf<String?>(null)
        private set

    companion object {
        const val PERIODIC_LOG_INTERVAL_MS = 3500L
    }

    /**
     * Starts periodic pose snapshot logging loop to backend.
     */
    fun startTelemetryLoop(cameraControls: CameraControls) {
        stopTelemetryLoop()
        sessionId = UUID.randomUUID().toString()

        telemetryJob = coroutineScope.launch {
            while (isActive) {
                delay(PERIODIC_LOG_INTERVAL_MS)
                if (!isActive) break

                val bitmap = cameraControls.getPreviewBitmap()
                if (bitmap == null) continue

                val result = apiClient.analyzeSmartGridPose(bitmap, sessionId)
                if (result.isSuccess) {
                    val pose = result.getOrNull()
                    if (pose != null && pose.compositionNote.isNotEmpty()) {
                        latestTelemetryNote = pose.compositionNote
                        Log.d("SmartGridController", "Logged pose note: ${pose.compositionNote}")
                    }
                }
            }
        }
    }

    /**
     * Stops the periodic telemetry loop cleanly.
     */
    fun stopTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = null
    }
}

@Composable
fun rememberSmartGridController(): SmartGridController {
    val coroutineScope = rememberCoroutineScope()
    return remember { SmartGridController(coroutineScope) }
}

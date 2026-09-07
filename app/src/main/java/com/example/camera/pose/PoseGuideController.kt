package com.example.camera.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI State for the Pose Guide mode.
 */
data class PoseGuideUiState(
    val selectedPose: PoseLibraryItem = BuiltInPoseLibrary.items[0],
    val targetSkeleton: PoseSkeleton = BuiltInPoseLibrary.items[0].skeleton,
    val noPoseDetected: Boolean = true,
    val matchScore: Int? = null,
    val isMatched: Boolean = false,
    val primaryFeedback: String = "No pose detected — step into frame",
    val limbCorrections: List<LimbCorrection> = emptyList(),
    val alignedLimbs: Set<String> = emptySet(),
    val isSheetVisible: Boolean = false,
    val isReconnecting: Boolean = false,
    val isEvaluating: Boolean = false,
    val poseLibrary: List<PoseLibraryItem> = BuiltInPoseLibrary.items,
    val uploadError: String? = null
)

/**
 * Encapsulated controller for Pose Guide mode.
 * Equivalent to usePoseGuide hook, maintaining strict separation of concerns from CameraControls.
 */
class PoseGuideController(
    private val coroutineScope: CoroutineScope
) {
    var uiState by mutableStateOf(PoseGuideUiState())
        private set

    private val apiClient = PoseGuideApiClient()

    init {
        // Attempt initial sync with backend pose library in background
        coroutineScope.launch {
            val result = apiClient.fetchPoseLibrary()
            if (result.isSuccess) {
                val library = result.getOrNull()
                if (!library.isNullOrEmpty()) {
                    Log.d("PoseGuideController", "Fetched ${library.size} distinct poses from backend library")
                    val currentId = uiState.selectedPose.id
                    val matched = library.find { it.id == currentId } ?: library[0]
                    uiState = uiState.copy(
                        poseLibrary = library,
                        selectedPose = matched,
                        targetSkeleton = matched.skeleton
                    )
                }
            }
        }
    }

    fun openPoseSheet() {
        uiState = uiState.copy(isSheetVisible = true)
    }

    fun closePoseSheet() {
        uiState = uiState.copy(isSheetVisible = false, uploadError = null)
    }

    fun selectPose(pose: PoseLibraryItem) {
        Log.d("PoseGuideController", "selectPose called: id=${pose.id}, name='${pose.name}', skeletonId=${pose.skeleton.id}, keypoints=${pose.skeleton.keypoints.size}")
        uiState = uiState.copy(
            selectedPose = pose,
            targetSkeleton = pose.skeleton,
            noPoseDetected = true,
            matchScore = null,
            isMatched = false,
            primaryFeedback = "No pose detected — step into frame",
            alignedLimbs = emptySet(),
            limbCorrections = emptyList(),
            isSheetVisible = false
        )
    }

    /**
     * Upload custom photo from gallery or Pinterest and extract skeleton.
     */
    fun uploadCustomImage(context: Context, uri: Uri) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (bitmap == null) return@launch

                // Try backend extraction
                val apiResult = apiClient.extractPoseFromImage(bitmap, name = "Custom Pose")
                withContext(Dispatchers.Main) {
                    if (apiResult.isSuccess) {
                        val skeleton = apiResult.getOrThrow()
                        val customItem = PoseLibraryItem(
                            id = skeleton.id,
                            name = skeleton.name,
                            category = "Custom",
                            description = "User uploaded pose guide",
                            difficulty = "Custom",
                            thumbnailUrl = "",
                            skeleton = skeleton
                        )
                        uiState = uiState.copy(
                            poseLibrary = listOf(customItem) + uiState.poseLibrary,
                            selectedPose = customItem,
                            targetSkeleton = skeleton,
                            noPoseDetected = true,
                            matchScore = null,
                            isMatched = false,
                            primaryFeedback = "No pose detected — step into frame",
                            alignedLimbs = emptySet(),
                            limbCorrections = emptyList(),
                            isSheetVisible = false
                        )
                    } else {
                        uiState = uiState.copy(
                            uploadError = "No pose detected in uploaded photo. Please try a clearer full-body photo."
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("PoseGuideController", "Upload custom image failed", e)
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(uploadError = "Could not process image: ${e.message}")
                }
            }
        }
    }

    /**
     * Receives real-time on-device pose evaluation results directly from PoseDetectionAnalyzer.
     * Updates UI state instantaneously with zero network overhead.
     */
    fun onLivePoseEvaluated(result: PoseCompareResult) {
        uiState = uiState.copy(
            noPoseDetected = result.noPoseDetected,
            matchScore = result.matchScore,
            isMatched = result.isMatched,
            primaryFeedback = result.primaryFeedback,
            limbCorrections = result.corrections,
            alignedLimbs = result.alignedLimbs.toSet(),
            isEvaluating = false
        )
    }

    /**
     * Resets the active evaluation state when entering or exiting Pose Guide mode.
     */
    fun resetEvaluation() {
        uiState = uiState.copy(
            noPoseDetected = true,
            matchScore = null,
            isMatched = false,
            primaryFeedback = "No pose detected — step into frame",
            limbCorrections = emptyList(),
            alignedLimbs = emptySet(),
            isEvaluating = false
        )
    }
}

/**
 * Composable remember hook for PoseGuideController.
 */
@Composable
fun rememberPoseGuideController(): PoseGuideController {
    val coroutineScope = rememberCoroutineScope()
    return remember { PoseGuideController(coroutineScope) }
}

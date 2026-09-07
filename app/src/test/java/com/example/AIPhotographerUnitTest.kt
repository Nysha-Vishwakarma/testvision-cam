package com.example

import com.example.camera.CameraMode
import com.example.camera.ZoomPillPlacement
import com.example.camera.getZoomPillPlacement
import com.example.camera.aiphoto.AICompositionResult
import com.example.camera.aiphoto.AIPhotographerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AIPhotographerUnitTest {

    @Test
    fun defaultUiState_hasSafeInitialValues() {
        val state = AIPhotographerUiState()
        assertEquals(0, state.score)
        assertEquals("HOLD_STEADY", state.suggestedMoveDirection)
        assertFalse(state.readyToCapture)
        assertFalse(state.isAnalyzing)
        assertTrue(state.isBackendConnected)
        assertFalse(state.isAutoCapturing)
        assertEquals(0, state.countdownRemainingSeconds)
    }

    @Test
    fun compositionResult_parsesAndExposesMetrics() {
        val result = AICompositionResult(
            score = 88,
            framingFeedback = "Subject nicely centered on rule of thirds",
            lightingFeedback = "Lighting is soft and balanced",
            suggestedZoomDelta = 0.2f,
            suggestedMoveDirection = "HOLD_STEADY",
            readyToCapture = true
        )

        assertEquals(88, result.score)
        assertTrue(result.readyToCapture)
        assertEquals(0.2f, result.suggestedZoomDelta, 0.001f)
        assertEquals("HOLD_STEADY", result.suggestedMoveDirection)
        assertTrue(result.framingFeedback.contains("rule of thirds"))
    }

    @Test
    fun stateUpdate_appliesAutoCapturingCountdown() {
        val initial = AIPhotographerUiState()
        val capturing = initial.copy(
            isAutoCapturing = true,
            countdownRemainingSeconds = 1,
            readyToCapture = true,
            score = 92
        )

        assertTrue(capturing.isAutoCapturing)
        assertEquals(1, capturing.countdownRemainingSeconds)
        assertTrue(capturing.readyToCapture)
        assertEquals(92, capturing.score)
    }

    @Test
    fun directionalNudge_handlesAllStandardDirections() {
        val validDirections = setOf(
            "HOLD_STEADY",
            "MOVE_UP",
            "MOVE_DOWN",
            "MOVE_LEFT",
            "MOVE_RIGHT",
            "STEP_BACK",
            "STEP_CLOSER"
        )

        for (dir in validDirections) {
            val state = AIPhotographerUiState(suggestedMoveDirection = dir)
            assertEquals(dir, state.suggestedMoveDirection)
            assertNotNull(state.suggestedMoveDirection)
        }
    }

    @Test
    fun zoomBehavior_faceSubjectLackingBreathingRoom_recommendsStepBackAndDampenedZoomOut() {
        // When a portrait/face subject occupies >42% of the frame or is cramped against edges,
        // the engine suggests STEP_BACK with a gentle zoom delta (-0.10f) instead of aggressive jumps.
        val faceResult = AICompositionResult(
            score = 55,
            framingFeedback = "Subject lacks breathing room — step back or zoom out for a natural frame.",
            lightingFeedback = "Lighting is well-balanced.",
            suggestedZoomDelta = -0.10f,
            suggestedMoveDirection = "STEP_BACK",
            subjectType = "person",
            readyToCapture = false
        )

        assertEquals("STEP_BACK", faceResult.suggestedMoveDirection)
        assertEquals(-0.10f, faceResult.suggestedZoomDelta, 0.001f)
        assertTrue(faceResult.framingFeedback.contains("breathing room"))
        assertEquals("person", faceResult.subjectType)
        assertFalse(faceResult.readyToCapture)
    }

    @Test
    fun zoomBehavior_fullObjectSubjectWithAmpleBreathingRoom_holdsSteadyWithZeroZoom() {
        // When an object/body subject has healthy 15-20% margin around all edges,
        // zoom delta remains exactly 0.0f, avoiding aggressive auto-zoom.
        val objectResult = AICompositionResult(
            score = 92,
            framingFeedback = "Subject is well positioned with comfortable breathing space. Hold steady.",
            lightingFeedback = "Lighting is well-balanced.",
            suggestedZoomDelta = 0.0f,
            suggestedMoveDirection = "HOLD_STEADY",
            subjectType = "object",
            readyToCapture = true
        )

        assertEquals("HOLD_STEADY", objectResult.suggestedMoveDirection)
        assertEquals(0.0f, objectResult.suggestedZoomDelta, 0.001f)
        assertTrue(objectResult.framingFeedback.contains("breathing space"))
        assertEquals("object", objectResult.subjectType)
        assertTrue(objectResult.readyToCapture)
    }

    @Test
    fun zoomBehavior_distantSubject_recommendsGentleZoomIn() {
        // Distant subject recommends gentle +0.08f zoom in instead of jarring jumps.
        val distantResult = AICompositionResult(
            score = 60,
            framingFeedback = "Subject is too far — step closer or zoom in slightly.",
            lightingFeedback = "Lighting is well-balanced.",
            suggestedZoomDelta = 0.08f,
            suggestedMoveDirection = "STEP_CLOSER",
            readyToCapture = false
        )

        assertEquals("STEP_CLOSER", distantResult.suggestedMoveDirection)
        assertEquals(0.08f, distantResult.suggestedZoomDelta, 0.001f)
        assertTrue(distantResult.suggestedZoomDelta <= 0.10f)
    }

    @Test
    fun analyzingState_preservesUiIntegrity() {
        val state = AIPhotographerUiState(
            isAnalyzing = true,
            score = 0,
            framingFeedback = "Analyzing scene composition…"
        )

        assertTrue(state.isAnalyzing)
        assertEquals(0, state.score)
        assertEquals("Analyzing scene composition…", state.framingFeedback)
    }

    @Test
    fun zoomPillPlacement_anchorsToTopRightInAIPhotographerModeOnly() {
        // AI Photographer mode must anchor the zoom pill to the top-right corner
        // to avoid overlapping the centered composition analysis pill or colliding with top-center controls.
        assertEquals(
            ZoomPillPlacement.TOP_RIGHT,
            getZoomPillPlacement(CameraMode.AI_PHOTOGRAPHER)
        )

        // All other modes must retain their original default top-center position without regression
        assertEquals(
            ZoomPillPlacement.TOP_CENTER,
            getZoomPillPlacement(CameraMode.PHOTO)
        )
        assertEquals(
            ZoomPillPlacement.TOP_CENTER,
            getZoomPillPlacement(CameraMode.FILTER)
        )
        assertEquals(
            ZoomPillPlacement.TOP_CENTER,
            getZoomPillPlacement(CameraMode.POSE_GUIDE)
        )
    }
}

package com.example.camera.aiphoto

/**
 * Result returned from FastAPI /api/v1/analyze-frame endpoint,
 * including closed-loop movement verification and Groq vision scene attributes.
 */
data class AICompositionResult(
    val score: Int,
    val framingFeedback: String,
    val lightingFeedback: String,
    val suggestedZoomDelta: Float,
    val suggestedMoveDirection: String,
    val readyToCapture: Boolean,
    val previousInstructionFollowed: Boolean = true,
    val correctionNote: String? = null,
    val guidanceStuck: Boolean = false,
    val subjectType: String = "general",
    val backendMode: String = "groq_vision"
)

/**
 * UI State for AI Photographer Mode.
 */
data class AIPhotographerUiState(
    val score: Int = 0,
    val framingFeedback: String = "Analyzing scene composition…",
    val lightingFeedback: String = "",
    val suggestedZoomDelta: Float = 0f,
    val suggestedMoveDirection: String = "HOLD_STEADY",
    val readyToCapture: Boolean = false,
    val previousInstructionFollowed: Boolean = true,
    val correctionNote: String? = null,
    val guidanceStuck: Boolean = false,
    val subjectType: String = "general",
    val backendMode: String = "groq_vision",
    val isAnalyzing: Boolean = false,
    val isBackendConnected: Boolean = true,
    val isAutoCapturing: Boolean = false,
    val countdownRemainingSeconds: Int = 0,
    val lastAnalyzedTime: Long = 0L,
    val errorMessage: String? = null
)

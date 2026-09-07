package com.example.camera.aiphoto

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Server-side configuration details returned by the Python FastAPI backend.
 */
data class ServerConfigInfo(
    val status: String,
    val visionModel: String,
    val isServerKeyConfigured: Boolean,
    val keyPreview: String
)

/**
 * Client for communicating with the Python FastAPI AI Photographer backend.
 * All Groq API key handling is strictly server-side.
 */
class AIPhotographerApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Sends downscaled snapshot to /api/v1/analyze-frame.
     * Delegates all vision analysis and Groq execution to the backend using server-side keys.
     */
    suspend fun analyzeFrame(
        context: Context,
        bitmap: Bitmap,
        sessionId: String
    ): Result<AICompositionResult> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = AIPhotographerConfig.getServerUrl(context)
            val groqModel = AIPhotographerConfig.getGroqModel(context)

            // Downscale to lightweight image (e.g. 360x480) for rapid transfer (< 40KB)
            val stream = ByteArrayOutputStream()
            val targetWidth = 360
            val targetHeight = (bitmap.height.toFloat() / bitmap.width.toFloat() * targetWidth).toInt().coerceAtLeast(360)
            val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, stream)
            val imageBytes = stream.toByteArray()
            if (scaled != bitmap) {
                scaled.recycle()
            }

            val bodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "snapshot",
                    "frame.jpg",
                    imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .addFormDataPart("session_id", sessionId)

            if (groqModel.isNotBlank()) {
                bodyBuilder.addFormDataPart("groq_model", groqModel)
            }

            val requestBuilder = Request.Builder()
                .url("$baseUrl/api/v1/analyze-frame")
                .post(bodyBuilder.build())

            if (groqModel.isNotBlank()) {
                requestBuilder.addHeader("X-Groq-Model", groqModel)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
                val json = JSONObject(body)

                val result = AICompositionResult(
                    score = json.optInt("score", 0),
                    framingFeedback = json.optString("framing_feedback", "Framing analyzed"),
                    lightingFeedback = json.optString("lighting_feedback", "Lighting analyzed"),
                    suggestedZoomDelta = json.optDouble("suggested_zoom_delta", 0.0).toFloat(),
                    suggestedMoveDirection = json.optString("suggested_move_direction", "HOLD_STEADY"),
                    readyToCapture = json.optBoolean("ready_to_capture", false),
                    previousInstructionFollowed = json.optBoolean("previous_instruction_followed", true),
                    correctionNote = if (json.has("correction_note") && !json.isNull("correction_note")) json.optString("correction_note") else null,
                    guidanceStuck = json.optBoolean("guidance_stuck", false),
                    subjectType = json.optString("subject_type", "general"),
                    backendMode = json.optString("backend_mode", "qwen/qwen3.6-27b")
                )
                Result.success(result)
            }
        } catch (e: Exception) {
            Log.d("AIPhotographerApiClient", "Frame analysis call failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Fetches current server-side configuration from the backend, verifying that
     * the server-side Groq API key is active and querying the configured vision model.
     */
    suspend fun fetchServerConfig(serverUrl: String): Result<ServerConfigInfo> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = serverUrl.trim().removeSuffix("/")
            val request = Request.Builder()
                .url("$cleanUrl/api/v1/config")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Backend returned HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val status = json.optString("status", "ok")
                val model = json.optString("vision_model", "qwen/qwen3.6-27b")
                val isKeyConfigured = json.optBoolean("groq_api_key_configured", false)
                val keyPreview = json.optString("groq_api_key_preview", "")

                Result.success(
                    ServerConfigInfo(
                        status = status,
                        visionModel = model,
                        isServerKeyConfigured = isKeyConfigured,
                        keyPreview = keyPreview
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

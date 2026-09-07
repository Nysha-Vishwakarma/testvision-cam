package com.example.camera.smartgrid

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
 * Result data from the backend deep head pose analysis (solvePnP + MediaPipe).
 */
data class SmartGridPoseResult(
    val faceDetected: Boolean = false,
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val roll: Float = 0f,
    val faceCenterX: Float = 0.5f,
    val faceCenterY: Float = 0.5f,
    val isLevel: Boolean = true,
    val nearIntersection: Boolean = false,
    val compositionNote: String = ""
)

/**
 * Client for communicating with the Python FastAPI Smart Grid endpoint.
 * Features fast network timeout, optimized image payload (<40KB), and graceful error recovery.
 */
class SmartGridApiClient(
    private val baseUrl: String = "https://vision-cam.onrender.com"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    /**
     * Sends downscaled snapshot to /api/v1/smart-grid-pose for deep solvePnP pose telemetry.
     */
    suspend fun analyzeSmartGridPose(
        bitmap: Bitmap,
        sessionId: String
    ): Result<SmartGridPoseResult> = withContext(Dispatchers.IO) {
        try {
            val stream = ByteArrayOutputStream()
            val targetWidth = 360
            val targetHeight = (bitmap.height.toFloat() / bitmap.width.toFloat() * targetWidth).toInt().coerceAtLeast(360)
            val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, stream)
            val imageBytes = stream.toByteArray()
            if (scaled != bitmap) {
                scaled.recycle()
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "snapshot",
                    "sg_frame.jpg",
                    imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .addFormDataPart("session_id", sessionId)
                .build()

            val request = Request.Builder()
                .url("$baseUrl/api/v1/smart-grid-pose")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
                val json = JSONObject(body)

                val faceCenterArr = json.optJSONArray("face_center")
                val fcX = if (faceCenterArr != null && faceCenterArr.length() > 0) faceCenterArr.optDouble(0, 0.5).toFloat() else 0.5f
                val fcY = if (faceCenterArr != null && faceCenterArr.length() > 1) faceCenterArr.optDouble(1, 0.5).toFloat() else 0.5f

                val result = SmartGridPoseResult(
                    faceDetected = json.optBoolean("face_detected", false),
                    pitch = json.optDouble("pitch", 0.0).toFloat(),
                    yaw = json.optDouble("yaw", 0.0).toFloat(),
                    roll = json.optDouble("roll", 0.0).toFloat(),
                    faceCenterX = fcX,
                    faceCenterY = fcY,
                    isLevel = json.optBoolean("is_level", true),
                    nearIntersection = json.optBoolean("near_intersection", false),
                    compositionNote = json.optString("composition_note", "")
                )
                Result.success(result)
            }
        } catch (e: Exception) {
            Log.d("SmartGridApiClient", "Smart Grid backend log skipped: ${e.message}")
            Result.failure(e)
        }
    }
}

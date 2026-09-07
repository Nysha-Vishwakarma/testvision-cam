package com.example.camera.pose

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.example.camera.aiphoto.AIPhotographerConfig
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Client for communicating with the Python FastAPI Pose Guide backend.
 * Features built-in timeout, robust error handling, and graceful offline fallback.
 */
class PoseGuideApiClient(
    private val baseUrl: String = AIPhotographerConfig.DEFAULT_SERVER_URL
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Endpoint 3: Fetch curated pose library from FastAPI backend.
     * Falls back to BuiltInPoseLibrary on connection failure.
     */
    suspend fun fetchPoseLibrary(): Result<List<PoseLibraryItem>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/v1/pose/library")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                val bodyString = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
                val jsonArray = JSONArray(bodyString)
                val items = mutableListOf<PoseLibraryItem>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val skelObj = obj.getJSONObject("skeleton")
                    val kpArray = skelObj.getJSONArray("keypoints")
                    val keypoints = mutableListOf<PoseKeypoint>()

                    for (j in 0 until kpArray.length()) {
                        val kp = kpArray.getJSONObject(j)
                        keypoints.add(
                            PoseKeypoint(
                                name = kp.getString("name"),
                                x = kp.getDouble("x").toFloat(),
                                y = kp.getDouble("y").toFloat(),
                                z = kp.optDouble("z", 0.0).toFloat(),
                                visibility = kp.optDouble("visibility", 1.0).toFloat()
                            )
                        )
                    }

                    val anglesObj = skelObj.optJSONObject("joint_angles")
                    val angles = mutableMapOf<String, Float>()
                    anglesObj?.keys()?.forEach { key ->
                        angles[key] = anglesObj.getDouble(key).toFloat()
                    }

                    val skeleton = PoseSkeleton(
                        id = skelObj.getString("id"),
                        name = skelObj.getString("name"),
                        keypoints = keypoints,
                        jointAngles = angles
                    )

                    items.add(
                        PoseLibraryItem(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            category = obj.getString("category"),
                            description = obj.getString("description"),
                            difficulty = obj.getString("difficulty"),
                            thumbnailUrl = obj.getString("thumbnail_url"),
                            skeleton = skeleton
                        )
                    )
                }
                Result.success(items)
            }
        } catch (e: Exception) {
            Log.d("PoseGuideApiClient", "Backend unavailable, using built-in library: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Endpoint 2: Compare camera snapshot with target skeleton JSON.
     */
    suspend fun comparePose(
        target: PoseSkeleton,
        snapshotBitmap: Bitmap
    ): Result<PoseCompareResult> = withContext(Dispatchers.IO) {
        try {
            // Downscale to lightweight image for fast transfer (< 60KB)
            val stream = ByteArrayOutputStream()
            val scaled = Bitmap.createScaledBitmap(snapshotBitmap, 256, 340, true)
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, stream)
            val imageBytes = stream.toByteArray()
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            val targetJson = JSONObject().apply {
                put("id", target.id)
                put("name", target.name)
                val kpArr = JSONArray()
                target.keypoints.forEach { kp ->
                    kpArr.put(JSONObject().apply {
                        put("name", kp.name)
                        put("x", kp.x.toDouble())
                        put("y", kp.y.toDouble())
                        put("z", kp.z.toDouble())
                        put("visibility", kp.visibility.toDouble())
                    })
                }
                put("keypoints", kpArr)
                val anglesObj = JSONObject()
                target.jointAngles.forEach { (k, v) -> anglesObj.put(k, v.toDouble()) }
                put("joint_angles", anglesObj)
            }

            val formBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("target_skeleton", targetJson.toString())
                .addFormDataPart("snapshot_base64", base64Image)
                .build()

            val request = Request.Builder()
                .url("$baseUrl/api/v1/pose/compare")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
                val resObj = JSONObject(body)

                val correctionsArr = resObj.getJSONArray("limb_corrections")
                val corrections = mutableListOf<LimbCorrection>()
                for (i in 0 until correctionsArr.length()) {
                    val c = correctionsArr.getJSONObject(i)
                    corrections.add(
                        LimbCorrection(
                            limb = c.getString("limb"),
                            instruction = c.getString("instruction"),
                            deltaDegrees = c.getDouble("delta_degrees").toFloat(),
                            status = c.getString("status")
                        )
                    )
                }

                val alignedArr = resObj.getJSONArray("aligned_limbs")
                val aligned = mutableListOf<String>()
                for (i in 0 until alignedArr.length()) {
                    aligned.add(alignedArr.getString(i))
                }

                val noPose = resObj.optBoolean("no_pose_detected", false)
                val matchScore = if (resObj.isNull("match_score") || noPose) null else resObj.optInt("match_score")

                val result = PoseCompareResult(
                    noPoseDetected = noPose,
                    matchScore = matchScore,
                    isMatched = resObj.optBoolean("is_matched", false),
                    status = resObj.optString("status", if (noPose) "NO_POSE_DETECTED" else "UNKNOWN"),
                    primaryFeedback = resObj.optString("primary_feedback", if (noPose) "No pose detected — step into frame" else ""),
                    corrections = corrections,
                    alignedLimbs = aligned
                )
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Endpoint 1: Extract normalized skeleton from uploaded image.
     */
    suspend fun extractPoseFromImage(bitmap: Bitmap, name: String = "Custom Pose"): Result<PoseSkeleton> = withContext(Dispatchers.IO) {
        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val imageBytes = stream.toByteArray()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "upload.jpg",
                    imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .addFormDataPart("pose_name", name)
                .build()

            val request = Request.Builder()
                .url("$baseUrl/api/v1/pose/extract")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
                val skelObj = JSONObject(body)
                val kpArray = skelObj.getJSONArray("keypoints")
                val keypoints = mutableListOf<PoseKeypoint>()

                for (j in 0 until kpArray.length()) {
                    val kp = kpArray.getJSONObject(j)
                    keypoints.add(
                        PoseKeypoint(
                            name = kp.getString("name"),
                            x = kp.getDouble("x").toFloat(),
                            y = kp.getDouble("y").toFloat(),
                            z = kp.optDouble("z", 0.0).toFloat(),
                            visibility = kp.optDouble("visibility", 1.0).toFloat()
                        )
                    )
                }

                val anglesObj = skelObj.optJSONObject("joint_angles")
                val angles = mutableMapOf<String, Float>()
                anglesObj?.keys()?.forEach { key ->
                    angles[key] = anglesObj.getDouble(key).toFloat()
                }

                val skeleton = PoseSkeleton(
                    id = skelObj.getString("id"),
                    name = skelObj.getString("name"),
                    keypoints = keypoints,
                    jointAngles = angles
                )
                Result.success(skeleton)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

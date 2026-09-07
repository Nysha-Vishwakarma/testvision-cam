package com.example.camera.aiphoto

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent configuration store for AI Photographer and Groq Qwen 3.6-27B Vision engine.
 */
object AIPhotographerConfig {
    private const val PREFS_NAME = "ai_photographer_config"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_GROQ_MODEL = "groq_model"

    const val DEFAULT_SERVER_URL = "https://vision-cam.onrender.com"
    const val DEFAULT_MODEL = "qwen/qwen3.6-27b"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getServerUrl(context: Context): String {
        val prefs = getPrefs(context)
        // Ensure any legacy client-side API key is purged from device storage
        if (prefs.contains("groq_api_key")) {
            prefs.edit().remove("groq_api_key").apply()
        }
        val saved = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        if (saved.contains("10.0.2.2") || saved.contains("10.0.0") || saved.contains("ai-photographer-backend.onrender.com") || saved.isBlank()) {
            setServerUrl(context, DEFAULT_SERVER_URL)
            return DEFAULT_SERVER_URL
        }
        return saved
    }

    fun setServerUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_SERVER_URL, url.trim().removeSuffix("/")).apply()
    }

    fun getGroqModel(context: Context): String {
        return getPrefs(context).getString(KEY_GROQ_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }

    fun setGroqModel(context: Context, model: String) {
        getPrefs(context).edit().putString(KEY_GROQ_MODEL, model.trim()).apply()
    }
}

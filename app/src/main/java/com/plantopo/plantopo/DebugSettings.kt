package com.plantopo.plantopo

import android.content.Context
import android.content.SharedPreferences

class DebugSettings private constructor(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCustomBaseUrl(): String? {
        return prefs.getString(KEY_CUSTOM_BASE_URL, null)
    }

    fun setCustomBaseUrl(url: String) {
        prefs.edit()
            .putString(KEY_CUSTOM_BASE_URL, url)
            .apply()
    }

    fun clearCustomBaseUrl() {
        prefs.edit()
            .remove(KEY_CUSTOM_BASE_URL)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "debug_settings"
        private const val KEY_CUSTOM_BASE_URL = "custom_base_url"

        @Volatile
        private var instance: DebugSettings? = null

        fun getInstance(context: Context): DebugSettings {
            return instance ?: synchronized(this) {
                instance ?: DebugSettings(context).also { instance = it }
            }
        }
    }
}

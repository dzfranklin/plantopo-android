package com.plantopo.plantopo

import android.content.Context

object Config {
    private var debugSettings: DebugSettings? = null

    val BASE_URL: String
        get() = debugSettings?.getCustomBaseUrl() ?: BuildConfig.BASE_URL

    fun initialize(context: Context) {
        debugSettings = DebugSettings.getInstance(context)
    }
}
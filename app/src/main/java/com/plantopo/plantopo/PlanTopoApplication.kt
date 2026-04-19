package com.plantopo.plantopo

import android.app.Application
import timber.log.Timber

class PlanTopoApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.plant(DebugLogBuffer.BufferingTree())
        }
    }
}

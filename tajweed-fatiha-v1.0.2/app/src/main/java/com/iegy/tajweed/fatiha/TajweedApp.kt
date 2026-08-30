package com.iegy.tajweed.fatiha

import android.app.Application
import android.content.Context

class TajweedApp : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }

    companion object {
        @Volatile
        private lateinit var appContext: Context

        fun context(): Context {
            check(::appContext.isInitialized) { "TajweedApp is not initialized" }
            return appContext
        }
    }
}

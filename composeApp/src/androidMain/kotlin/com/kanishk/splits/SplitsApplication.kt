package com.kanishk.splits

import android.app.Application
import com.kanishk.splits.data.appContext

class SplitsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }
}

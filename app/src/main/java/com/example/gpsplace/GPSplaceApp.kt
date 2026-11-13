package com.example.gpsplace

import android.app.Application
import com.example.gpsplace.notify.NotificationChannels

class GPSplaceApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.ensure(this)
    }
}

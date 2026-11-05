package com.motrixlite.downloader

import android.app.Application
import com.motrixlite.downloader.service.NotificationHelper

class App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper(this).createChannels()
    }
}

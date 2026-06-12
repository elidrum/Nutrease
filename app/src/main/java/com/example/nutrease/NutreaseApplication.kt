package com.example.nutrease

import android.app.Application
import com.example.nutrease.data.notification.ReminderNotificationBuilder
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NutreaseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ReminderNotificationBuilder.ensureChannel(this)
    }
}

package com.dadsvictory

import android.app.Application
import com.dadsvictory.notifications.Notifications

class DadsVictoryApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Channels must exist before the first notification is posted, and creating
        // them is idempotent, so the safest place is application start.
        Notifications.createChannels(this)
    }
}

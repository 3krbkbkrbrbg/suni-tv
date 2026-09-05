package com.famelack.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.famelack.app.data.FamelackRepository
import com.famelack.app.data.FavoritesStore

class FamelackApp : Application() {

    val repository: FamelackRepository by lazy { FamelackRepository(this) }
    val favoritesStore: FavoritesStore by lazy { FavoritesStore(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_PLAYBACK,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
    }

    companion object {
        const val CHANNEL_PLAYBACK = "playback"
        lateinit var instance: FamelackApp
            private set
    }
}

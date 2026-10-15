package com.coursework.modernlocationtracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

// Foreground service responsible for background location tracking
// (Android Developers - Services overview)
class LocationTrackingService : Service() {

    companion object {
        // Unique identifier for the notification channel
        private const val CHANNEL_ID = "location_tracking_channel"

        // Unique identifier for the foreground service notification
        private const val NOTIFICATION_ID = 1
    }

    // Create a notification channel for devices running Android 8.0 or later
    // (Android Developers - Create and manage notification channels)
    private fun createNotificationChannel() {

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location Tracking",
            NotificationManager.IMPORTANCE_LOW
        )

        // Obtain the system notification manager and register the channel
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    // Build the notification that informs the user location tracking is active
    // (Android Developers - Create a notification)
    private fun createTrackingNotification(): Notification {

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Modern Location Tracker")
            .setContentText("Background location tracking is active")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    // Start the foreground service and display its persistent notification
    // (Android Developers - Foreground services overview)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // Register the notification channel before displaying a notification
        createNotificationChannel()

        // Promote the service to the foreground with a visible notification
        startForeground(NOTIFICATION_ID, createTrackingNotification())

        // Do not automatically restart the service if Android terminates it
        return START_NOT_STICKY
    }

    // This application does not use a bound service
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
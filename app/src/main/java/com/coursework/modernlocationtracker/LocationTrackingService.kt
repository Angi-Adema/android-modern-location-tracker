package com.coursework.modernlocationtracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import android.util.Log
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat

// Foreground service responsible for background location tracking
// (Android Developers - Services overview)
class LocationTrackingService : Service() {

    companion object {
        // Unique identifier for the notification channel
        private const val CHANNEL_ID = "location_tracking_channel"

        // Unique identifier for the foreground service notification
        private const val NOTIFICATION_ID = 1
    }

    // Location client used by the foreground service to receive updates
    // (Google for Developers - FusedLocationProviderClient)
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Configure location updates for the foreground service
    // Balanced power accuracy helps reduce battery consumption
    // (Google for Developers - LocationRequest.Builder and Priority)
    private val trackingLocationRequest = LocationRequest.Builder(
        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        5000L
    )
        // Allow location updates at intervals as short as two seconds
        .setMinUpdateIntervalMillis(2000L)
        .build()

    // Receive location updates independently of MainActivity
    // (Google for Developers - LocationCallback and LocationResult)
    private val locationCallback = object : LocationCallback() {

        // Called when the Fused Location Provider delivers a location update
        override fun onLocationResult(locationResult: LocationResult) {

            // Retrieve the most recent location from the update
            val location = locationResult.lastLocation

            if (location != null) {

                // Record background tracking coordinates in Logcat
                // This provides evidence that tracking continues outside MainActivity
                Log.d(
                    "LocationTrackerService",
                    "Background Tracking Latitude: ${location.latitude}, Longitude: ${location.longitude}"
                )
            }
        }
    }

    // Initialize the Fused Location Provider when the service is created
    // (Android Developers - Services overview and Google for Developers - FusedLocationProviderClient)
    override fun onCreate() {
        super.onCreate()

        // Obtain the location client using the service Context
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
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

        // Begin receiving location updates after the service enters the foreground
        startLocationUpdates()

        // Do not automatically restart the service if Android terminates it
        return START_NOT_STICKY
    }

    // Request continuous location updates while the foreground service is running
    // (Google for Developers - FusedLocationProviderClient)
    private fun startLocationUpdates() {

        // Verify that the user has granted location permission
        val hasFinePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarsePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // Do not request location updates without permission
        if (!hasFinePermission && !hasCoarsePermission) {
            Log.e("LocationTrackerService", "Location permission not granted")
            return
        }

        // Register the location callback to receive continuous updates
        fusedLocationClient.requestLocationUpdates(
            trackingLocationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
            // Confirm that Google Play services accepted the location update request
            // (Google for Developers - FusedLocationProviderClient)
            .addOnSuccessListener {
                Log.d(
                    "LocationTrackerService",
                    "Location updates registered successfully"
                )
            }
            .addOnFailureListener { exception ->
                Log.e(
                    "LocationTrackerService",
                    "Failed to request background location updates",
                    exception
                )
            }
    }

    // Release location updates when the foreground service is destroyed
    // (Android Developers - Services overview;
    // Google for Developers - FusedLocationProviderClient)
    override fun onDestroy() {

        // Unregister the location callback to stop receiving location updates
        // and prevent unnecessary battery consumption
        fusedLocationClient.removeLocationUpdates(locationCallback)

        // Complete the normal service destruction process
        super.onDestroy()
    }

    // This application does not use a bound service
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
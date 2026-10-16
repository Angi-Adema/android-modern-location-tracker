package com.coursework.modernlocationtracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

// Receive geofence transition events when the device enters or exits a monitored geographic area
// (Android Developers - Create and monitor geofences)
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        // Convert the received Intent into a geofencing event
        // (Google for Developers - GeofencingEvent)
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        // Stop processing if the event cannot be read
        if (geofencingEvent == null) {
            Log.e("GeofenceTracker", "Geofencing event is null")
            return
        }

        // Check whether Google Play services reported an error
        if (geofencingEvent.hasError()) {
            Log.e(
                "GeofenceTracker",
                "Geofencing error code: ${geofencingEvent.errorCode}"
            )
            return
        }

        // Identify whether the device entered or exited the geofence
        when (geofencingEvent.geofenceTransition) {

            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.d("GeofenceTracker", "Device entered the geofence")
            }

            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.d("GeofenceTracker", "Device exited the geofence")
            }

            else -> {
                Log.d("GeofenceTracker", "Unknown geofence transition")
            }
        }
    }
}
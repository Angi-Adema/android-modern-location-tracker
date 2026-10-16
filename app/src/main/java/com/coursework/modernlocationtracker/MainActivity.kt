package com.coursework.modernlocationtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.coursework.modernlocationtracker.ui.theme.ModernLocationTrackerTheme
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.rememberCameraPositionState
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.FusedLocationProviderClient
import android.location.Location
import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.tasks.CancellationTokenSource
import android.content.Intent
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import android.app.PendingIntent

class MainActivity : ComponentActivity() {

    // Declare a Fused Location Provider client that will be initialized in onCreate()
    // (Android Developers - Get the last known location)
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Client used to register and manage geographic boundaries
    // (Google for Developers - GeofencingClient)
    private lateinit var geofencingClient: GeofencingClient

    // Define a circular geofence around the emulator's test location
    // (Android Developers - Create and monitor geofences)
    private val testGeofence = Geofence.Builder()
        .setRequestId("aurora_geofence")
        .setCircularRegion(
            39.7057417,
            -104.7587717,
            150f
        )
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        .setTransitionTypes(
            Geofence.GEOFENCE_TRANSITION_ENTER or
                    Geofence.GEOFENCE_TRANSITION_EXIT
        )
        .build()

    // Configure a request to monitor the defined geofence
    // (Android Developers - Create and monitor geofences)
    private val geofencingRequest = GeofencingRequest.Builder()
        // Trigger an entry event if the device is already inside the geofence
        .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)

        // Add the Aurora, CO geofence test to the monitoring request
        .addGeofence(testGeofence)
        .build()

    // Create a PendingIntent to deliver geofence events to the BroadcastReceiver
    // (Android Developers - Create and monitor geofences)
    private val geofencePendingIntent: PendingIntent by lazy {

        // Identify the receiver that handles geofence entry and exit events
        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)

        // Allow Google Play services to deliver geofence events to the receiver
        PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    // Store the device's retrieved coordinates as observable Compose state
    // When this value changes, composables reading it can recompose
    // (Android Developers - State and Jetpack Compose)
    private var deviceLocation by mutableStateOf<LatLng?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the location client using the current Activity as the Context
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize the geofencing client using the Activity context
        // (Android Developers - Create and monitor geofences)
        geofencingClient = LocationServices.getGeofencingClient(this)

        // Check whether the user previously granted precise or approximate location permission when the application starts
        // (Android Developers - Request location permissions)
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // Automatically retrieve the current device location when permission has already been granted, allowing the map to center on the device
        // without requiring the user to request permission again
        // (Google for Developers - FusedLocationProviderClient)
        if (hasFineLocationPermission || hasCoarseLocationPermission) {
            getCurrentDeviceLocation()
        }

        enableEdgeToEdge()
        setContent {
            ModernLocationTrackerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    // Use Box to show various composables in the same area, display the Google Map and location permission controls together
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {

                        // Pass the device's location state to MapScreen so the map can respond to coordinate changes (Android Developers - State Hoisting)
                        MapScreen(
                            modifier = Modifier.fillMaxSize(),
                            deviceLocation = deviceLocation
                        )

                        // Display location permission controls and tracking buttons (Android Developers - State hoisting)
                        LocationPermissionScreen(
                            onPermissionGranted = {
                                // Retrieve the device's current location after permission is granted
                                getCurrentDeviceLocation()
                            },
                            onStartTracking = {
                                // Start the foreground service while the Activity is visible
                                // so location updates can continue when the app is in the background
                                // (Android Developers - Launch a foreground service)
                                val serviceIntent = Intent(
                                    this@MainActivity,
                                    LocationTrackingService::class.java
                                )
                                ContextCompat.startForegroundService(
                                    this@MainActivity,
                                    serviceIntent
                                )

                                // Register the geofence when location tracking begins
                                // (Android Developers - Create and monitor geofences)
                                registerGeofence()
                            },
                            onStopTracking = {
                                // Stop the foreground service and release its continuous location updates
                                // (Android Developers - Services overview)
                                val serviceIntent = Intent(
                                    this@MainActivity,
                                    LocationTrackingService::class.java
                                )
                                stopService(serviceIntent)

                                // Remove the registered geofence to stop monitoring entry and exit events
                                // (Google for Developers - GeofencingClient)
                                removeGeofence()
                            }
                        )
                    }
                }
            }
        }
    }
    // Request the device's current location after verifying the location permissions
    private fun getCurrentDeviceLocation() {
        // Verify whether FINE or COARSE location permission was granted
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // Do not attempt to retrieve location information if neither permission is granted
        if (!hasFineLocationPermission && !hasCoarseLocationPermission) {
            return
        }

        // Retrieve the last known location from the Fused Location Provider
        // (Android Developers - Get the last known location)
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {

                    // Log the retrieved coordinates
                    Log.d(
                        "LocationTracker",
                        "Last known Latitude: ${location.latitude}, Longitude: ${location.longitude}"
                    )

                    // Convert the last known location into Google Maps coordinates
                    // Update Compose state so MapScreen can center the camera on the device
                    // (Google Maps - LatLng, Android Developers - State and Jetpack Compose)
                    deviceLocation = LatLng(
                        location.latitude,
                        location.longitude
                    )

                } else {
                    Log.d(
                        "LocationTracker",
                        "No last known location is available"
                    )
                }
            }
            .addOnFailureListener { exception ->
                Log.e(
                    "LocationTracker",
                    "Failed to retrieve last known location",
                    exception
                )
            }

        // Configure a current-location request that can use a recently cached location if one is available, reducing the need for a new location fix
        // (Google for Developers - CurrentLocationRequest.Builder)
        val currentLocationRequest = CurrentLocationRequest.Builder()
            .setMaxUpdateAgeMillis(30000L)
            .build()

        // Create a cancellation token source for the current-location request (Google for Developers - CancellationTokenSource (Kotlin))
        // The token allows the request to be canceled if necessary
        val cancellationTokenSource = CancellationTokenSource()

        // Request a current location using the configured request and cancellation token
        // The result is returned asynchronously through addOnSuccessListener
        // (Google for Developers - FusedLocationProviderClient)
        fusedLocationClient.getCurrentLocation(currentLocationRequest, cancellationTokenSource.token)
            .addOnSuccessListener { location: Location? ->

                if (location != null) {
                    // Display the retrieved current location's latitude and longitude in Logcat (Android Developers - Location Class)
                    // Log.d writes a debug message using a tag to identify the application (Android Developers - Log)
                    Log.d(
                        "LocationTracker",
                        "Latitude: ${location.latitude}, Longitude: ${location.longitude}"
                    )

                    // Convert the retrieved Android Location coordinates into a Google Maps LatLng object
                    // Update the observable state so MapScreen can respond to the new coordinates
                    // (Android Developers - Location, Google Maps - LatLng, Android Developers - State and Jetpack Compose)
                    deviceLocation = LatLng(
                        location.latitude,
                        location.longitude
                    )
                }
                else {
                    // Log a message when the current-location request returns no location
                    Log.d(
                        "LocationTracker",
                        "No current location is available"
                    )
                }

            }

            .addOnFailureListener { exception ->
                // Log an error if the Fused Location Provider request fails
                Log.e(
                    "LocationTracker",
                    "Failed to retrieve the current location",
                    exception
                )
            }
    }

    // Register the Aurora, CO test geofence with Google Play services
    // (Android Developers - Create and monitor geofences)
    private fun registerGeofence() {

        // Geofencing requires precise location permission
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // Do not register the geofence without precise location permission
        if (!hasFineLocationPermission) {
            Log.e("GeofenceTracker", "Precise location permission is required")
            return
        }

        // Register the geofence and specify where transition events are delivered
        // (Google for Developers - GeofencingClient)
        geofencingClient.addGeofences(
            geofencingRequest,
            geofencePendingIntent
        )
            .addOnSuccessListener {
                Log.d("GeofenceTracker", "Geofence registered successfully")
            }
            .addOnFailureListener { exception ->
                Log.e(
                    "GeofenceTracker",
                    "Failed to register geofence",
                    exception
                )
            }
    }

    // Remove the registered geofence when the user stops tracking
    // This prevents continued geofence monitoring after tracking is stopped
    // (Google for Developers - GeofencingClient)
    private fun removeGeofence() {

        // Request removal of the geofence associated with our PendingIntent
        // (Google for Developers - GeofencingClient.removeGeofences)
        geofencingClient.removeGeofences(geofencePendingIntent)
            .addOnSuccessListener {
                // Confirm that geofence monitoring has stopped
                Log.d(
                    "GeofenceTracker",
                    "Geofence removed successfully"
                )
            }
            .addOnFailureListener { exception ->
                // Log an error if Google Play services cannot remove the geofence
                Log.e(
                    "GeofenceTracker",
                    "Failed to remove geofence",
                    exception
                )
            }
    }
}

// MapScreen implementation adapted from the Google Maps - Compose documentation
@Composable
fun MapScreen(modifier: Modifier = Modifier, deviceLocation: LatLng? = null) {
    // Initialize the map camera using the Singapore coordinates from the Google Maps Compose example until the device's current location is available
    // (Google Maps Compose - Controlling a map's camera)
    val singapore = LatLng(1.35, 103.87)

    // Control the map camera using CameraPositionState (Google Maps Compose - Controlling a map's camera)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(singapore, 10f)
    }

    // Respond when the device's location becomes available or changes (Android Developers - LaunchedEffect)
    LaunchedEffect(deviceLocation) {

        // Only update the camera when valid coordinates are available
        if (deviceLocation != null) {

            // Move the map camera to the device's coordinates at zoom level 15
            cameraPositionState.position =
                CameraPosition.fromLatLngZoom(deviceLocation, 15f)
        }
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState
    )
}

// Request foreground location permissions using Android's Activity Result API
// (Kemal Codes Jetpack Compose Tutorial, Android Developers - Compose and other libraries, Jetpack Compose -  state hoisting)
@Composable
fun LocationPermissionScreen(
    modifier: Modifier = Modifier,
    onPermissionGranted: () -> Unit = {},
    onStartTracking: () -> Unit = {},
    onStopTracking: () -> Unit = {}) {

    // Get the current Android Context to obtain access to application resources and system services
    val context = LocalContext.current

    // Create a Compose state variable storing whether the app has permission to access the device location
    // remember retains the state across recompositions, while mutableStateOf creates observable state that allows Compose to update the UI as the state changes
    // Checks must be conducted to verify whether approximate (COARSE) or precise (FINE) location permission has been granted
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Register a launcher capable of requesting multiple permissions
    // RequestMultiplePermissions can request both approximate and precise location permissions in one request
    // The callback receives a map containing each requested permission and a boolean indicating whether the user granted or denied the permission
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->

        // Update the permission state using the user response
        // When COARSE or FINE permissions are granted location access is available
        // Compose will recompose the UI elements associated with hasLocationPermission when updating the state
        hasLocationPermission =
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true

        // Notify the calling composable when location permission has been granted
        // The callback will allow MainActivity to initiate location retrieval
        if (hasLocationPermission) {
            onPermissionGranted()
        }
    }

    // Column is a Compose layout that arranges child elements vertically. The button is placed above the status message
    Column(modifier = modifier) {

        // Button for the user to request location permissions
        Button(
            onClick = {

                // Launch Android's permission request dialog for both COARSE and FINE location permission
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
            }
        ) {
            Text("Enable Location")
        }

        // Print a message regarding the current permission state showing confirmation permission is granted
        // or notify that permission must be granted for location access
        Text(
            if (hasLocationPermission)
                "Location permission granted"
            else
                "Location permission is needed to track your position"
        )

        // Allow the user to start continuous location tracking
        Button(
            onClick = onStartTracking,
            enabled = hasLocationPermission
        ) {
            Text("Start Tracking")
        }

        // Allow the user to stop continuous location tracking
        Button(
            onClick = onStopTracking,
            enabled = hasLocationPermission
        ) {
            Text("Stop Tracking")
        }
    }
}
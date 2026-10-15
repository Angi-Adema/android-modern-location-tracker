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
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import android.content.Intent

class MainActivity : ComponentActivity() {

    // Declare a Fused Location Provider client that will be initialized in onCreate() (Android Developers Get the last known location)
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Store the device's retrieved coordinates as observable Compose state
    // When this value changes, composables reading it can recompose (Android Developers State and Jetpack Compose)
    private var deviceLocation by mutableStateOf<LatLng?>(null)

    // Configure continuous location tracking with balanced power consumption
    // Priority.PRIORITY_BALANCED_POWER_ACCURACY balances location accuracy
    // with battery efficiency by avoiding unnecessary high-accuracy requests
    // (Google for Developers - LocationRequest.Builder, Priority)
    private val trackingLocationRequest = LocationRequest.Builder(
        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        5000L
    )
        .setMinUpdateIntervalMillis(2000L)
        .build()

    // Receive location updates while continuous tracking is active
    // (Google for Developers - LocationCallback)
    private val locationCallback = object : LocationCallback() {

        // Called when the Fused Location Provider delivers location updates
        override fun onLocationResult(locationResult: LocationResult) {

            // Retrieve the most recent location from the result (Google Developers - LocationResult)
            val location = locationResult.lastLocation

            if (location != null) {

                // Update Compose state with the latest coordinates
                // MapScreen will respond by updating the map camera
                deviceLocation = LatLng(
                    location.latitude,
                    location.longitude
                )

                // Display the coordinates in Logcat for testing
                Log.d(
                    "LocationTracker",
                    "Tracking Latitude: ${location.latitude}, Longitude: ${location.longitude}"
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the location client using the current Activity as the Context
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

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
                                // Start the foreground service while the Activity is visible so location updates can continue when the app is running in the background
                                // (Android Developers - Launch a foreground service)
                                val serviceIntent = Intent(this@MainActivity, LocationTrackingService::class.java)
                                ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
                            },
                            onStopTracking = {
                                // Stop the foreground service and release its location updates
                                // (Android Developers - Services overview)
                                val serviceIntent = Intent(this@MainActivity, LocationTrackingService::class.java)
                                stopService(serviceIntent)
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

        // Configure a current-location request that does not accept cached locations (Google Developers - CurrentLocationRequest.Builder)
        val currentLocationRequest = CurrentLocationRequest.Builder()
            .setMaxUpdateAgeMillis(0L)
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
                    // Display the retrieved current location's latitude and longitude in Logcat (Android Developers Location Class)
                    // Log.d writes a debug message using a tag to identify the application (Android Developers Log)
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

    // Start receiving continuous location updates using the Fused Location Provider
    // LocationRequest defines the update frequency and location accuracy settings
    // (Google for Developers - FusedLocationProviderClient;
    // Android Developers - Request location updates)
    private fun startLocationTracking() {

        // Verify that the application has location permission
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // Do not start tracking without location permission
        if (!hasFineLocationPermission && !hasCoarseLocationPermission) {
            return
        }

        // Request continuous location updates using the configured request
        // Deliver the results to locationCallback on the main thread
        fusedLocationClient.requestLocationUpdates(
            trackingLocationRequest,
            locationCallback,
            mainLooper
        )

        // Record that continuous tracking has been requested
        Log.d("LocationTracker", "Continuous location tracking requested")
    }

    // Stop continuous location updates when tracking is no longer needed
    // (Google for Developers - FusedLocationProviderClient)
    private fun stopLocationTracking() {

        // Unregister the location callback to stop receiving location updates
        fusedLocationClient.removeLocationUpdates(locationCallback)
            .addOnSuccessListener {
                // Confirm that the location update removal succeeded
                Log.d("LocationTracker", "Continuous location tracking stopped")
            }
            .addOnFailureListener { exception ->
                // Log an error if location updates could not be removed
                Log.e(
                    "LocationTracker",
                    "Failed to stop location tracking",
                    exception
                )
            }
    }

    // Release location updates when MainActivity is destroyed
    // (Android Developers - Activity lifecycle)
    override fun onDestroy() {

        // Remove the registered location callback to prevent unnecessary
        // location updates after the Activity is destroyed
        fusedLocationClient.removeLocationUpdates(locationCallback)

        // Complete the normal Activity destruction process
        super.onDestroy()
    }
}

// MapScreen implementation adapted from the Google Maps Compose documentation
@Composable
fun MapScreen(modifier: Modifier = Modifier, deviceLocation: LatLng? = null) {
    // Initial map location from Google's Maps Compose example
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
// (Kemal Codes Jetpack Compose Tutorial, Android Developers Compose and other libraries, Jetpack Compose state hoisting)
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
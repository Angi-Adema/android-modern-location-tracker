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

class MainActivity : ComponentActivity() {

    // Declare a Fused Location Provider client that will be initialized in onCreate() (Android Developers Get the last known location)
    private lateinit var fusedLocationClient: FusedLocationProviderClient

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

                        // MapScreen appears first, display the interactive Google Map as the background behind other elements
                        MapScreen(
                            modifier = Modifier.fillMaxSize()
                        )

                        // Display the permission controls and receive notification when permission is granted
                        LocationPermissionScreen(
                            onPermissionGranted = {
                                // Location permission has been granted
                                // The Activity will retrieve the device's location here in a later step
                            }
                        )
                    }
                }
            }
        }
    }
    // Retrieve the device's last known location after verifying location permissions
    private fun getLastKnownLocation() {
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

    }
}

// MapScreen implementation adapted from the Google Maps Compose documentation
@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    // Initial map location from Google's Maps Compose example
    val singapore = LatLng(1.35, 103.87)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(singapore, 10f)
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState
    )
}

// Request foreground location permissions using Android's Activity Result API
// (Kemal Codes Jetpack Compose Tutorial, Android Developers Compose and other libraries, Jetpack Compose state hoisting)
@Composable
fun LocationPermissionScreen(modifier: Modifier = Modifier, onPermissionGranted: () -> Unit = {}) {

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
    }
}
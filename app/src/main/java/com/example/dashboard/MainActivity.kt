package com.example.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.navigine.idl.java.GlobalPoint
import com.navigine.idl.java.Location
import com.navigine.idl.java.LocationInfo
import com.navigine.idl.java.LocationListListener
import com.navigine.idl.java.LocationListManager
import com.navigine.idl.java.LocationListener
import com.navigine.idl.java.LocationManager
import com.navigine.idl.java.NavigineSdk
import com.navigine.idl.java.Sublocation
import java.io.IOException
import java.util.Locale
import kotlin.math.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var canaryStatusIndicator: View
    private lateinit var canaryStatusText: TextView
    private lateinit var locationCallback: com.google.android.gms.location.LocationCallback
    private lateinit var locationRequest: com.google.android.gms.location.LocationRequest
    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationPermissionRequestCode = 1
    private val recordAudioPermissionRequestCode = 2
    companion object {
        private const val TAG = "NavigineSDK"  // Define once per class
    }

    // UI elements
    private lateinit var searchView: SearchView
    private lateinit var homeButton: LinearLayout
    private lateinit var settingsButton: LinearLayout
    private lateinit var profileButton: LinearLayout
    private lateinit var locationNameText: TextView
    private lateinit var locationInfoText: TextView
    private lateinit var micButton: ImageView  // Add microphone button

    //navigine
    private var userLatitude: Double = 0.0
    private var userLongitude: Double = 0.0
    private val TAG = "LocationMatcher"
    private val navigationSdk = NavigineSdk.getInstance()
    private val locationListManager by lazy { mNavigineSdk.getLocationListManager() }
    private val locationManager by lazy { mNavigineSdk.getLocationManager() }
    private lateinit var mNavigineSdk: NavigineSdk



    // Speech recognition launcher
    private val speechRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val spokenText: ArrayList<String>? = result.data?.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )
            spokenText?.get(0)?.let { text ->
                searchView.setQuery(text, false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        // Use standard setContentView instead of databinding
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        canaryStatusIndicator = findViewById(R.id.canaryStatusIndicator)
        canaryStatusText = findViewById(R.id.canaryStatusText)
        searchView = findViewById(R.id.searchView)
        homeButton = findViewById(R.id.homeButton)
        settingsButton = findViewById(R.id.settingsButton)
        profileButton = findViewById(R.id.profileButton)
        locationNameText = findViewById(R.id.locationNameText)
        locationInfoText = findViewById(R.id.locationInfoText)
        micButton = findViewById(R.id.micButton)  // Initialize mic button

        // Initialize the map fragment
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
        // ✅ Initialize Navigine AFTER getting current location
        // Initialize Navigine SDK
        initializeNavigine()

        // Then initialize managers and setup listeners



        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        // Create LocationRequest using the Builder
        // Correct way to create LocationRequest with the new Builder pattern
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateDistanceMeters(10f)  // Optional: Only update if the user has moved 10 meters
            .setMaxUpdateDelayMillis(5000)  // Fastest interval for updates (2 seconds)
            .build()


// Setup LocationCallback to receive location updates
        locationCallback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                for (location in locationResult.locations) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)

                    // Move the camera to the new location
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))

                    // Optionally, update the UI with the address from the location
                    getAddressFromLocation(location.latitude, location.longitude)
                }
            }
        }


        // Setup search view
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // Handle search here
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })

        // Setup speech recognition for microphone button
        micButton.setOnClickListener {
            // Check for audio recording permission
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Request permission
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    recordAudioPermissionRequestCode
                )
            } else {
                // Permission already granted, start speech recognition
                startSpeechRecognition()
            }
        }

        // Setup navigation buttons
        homeButton.setOnClickListener {
            // Handle home navigation
        }
        settingsButton.setOnClickListener {
            // Handle settings navigation
        }
        profileButton.setOnClickListener {
            // Handle profile navigation
        }
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
        }

        try {
            speechRecognitionLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Speech recognition not supported on this device",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        // Check for location permissions and get user location
        checkLocationPermissionAndGetLocation()
    }

    private fun checkLocationPermissionAndGetLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                locationPermissionRequestCode
            )
        } else {
            // Permission already granted
            getUserLocation()         // Get last known location
            startLocationUpdates()    // ✅ Start real-time updates
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )
    }

    private fun initializeNavigine() {
        try {
            // 1. Initialize SDK (keeping your existing code)
            NavigineSdkManager.initialize(this)

            // 2. Get instance - correct way based on SDK API
            mNavigineSdk = NavigineSdk.getInstance() // No arguments needed

            Log.d(TAG, "Navigine SDK initialized successfully")

            // 3. Setup listeners (managers will initialize when first accessed)
            setupLocationListeners()


        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Navigine SDK: ${e.message}")
        }
    }


    private var isNearNavigineLocation = false

    private fun getUserLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    // 1. Store coordinates
                    userLatitude = it.latitude
                    userLongitude = it.longitude
                    val currentLatLng = LatLng(it.latitude, it.longitude)

                    // Move camera to user location
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                    isNearNavigineLocation=isUserNearAnyLocation(
                        userLat = currentLatLng.latitude,
                        userLng = currentLatLng.longitude
                    )
                    // Get address from coordinates
                    getAddressFromLocation(it.latitude, it.longitude)
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    // Define a class to store location data with coordinates
    private data class LocationData(
        val locationId: Int,
        val coordinatePoints: MutableList<CoordinatePoint> = mutableListOf()
    )

    // Simple class to store coordinate points
    private data class CoordinatePoint(val latitude: Double, val longitude: Double)

    // Map to store location data keyed by location ID
    private val locationDataMap = mutableMapOf<Int, LocationData>()
    private val navigineLocations = mutableListOf<LocationInfo>() // Persistent storage

    // Setup function to initialize location listeners
    fun setupLocationListeners() {
        // Add listener for location list updates
        locationListManager.addLocationListListener(object : LocationListListener() {
            override fun onLocationListLoaded(locations: HashMap<Int, LocationInfo>) {
                navigineLocations.clear()
                navigineLocations.addAll(locations.values)
                Log.d(TAG, "${locations.size} locations cached")

                // For newly added locations, set up listeners
                locations.values.forEach { locationInfo ->
                    if (!locationDataMap.containsKey(locationInfo.getId())) {
                        // Create entry for this location
                        locationDataMap[locationInfo.getId()] = LocationData(locationInfo.getId())

                        // Request location details
                        loadLocationDetails(locationInfo.getId())
                    }
                }
            }

            override fun onLocationListFailed(error: Error) {
                Log.e(TAG, "Failed to load locations: ${error.message}")
            }
        })

        // Add listener for individual location updates
        locationManager.addLocationListener(object : LocationListener() {
            override fun onLocationLoaded(location: Location) {
                // Location was loaded, extract coordinate information
                val locationId = location.getId() // Assuming Location has getId() method
                val locationData = locationDataMap[locationId] ?: LocationData(locationId)

                // Clear previous coordinates for this location
                locationData.coordinatePoints.clear()

                // Get sublocations and their origin points
                val sublocations = location.getSublocations()
                sublocations.forEach { sublocation ->
                    val originPoint = sublocation.getOriginPoint()
                    locationData.coordinatePoints.add(
                        CoordinatePoint(originPoint.latitude, originPoint.longitude)
                    )
                }

                // Save updated location data
                locationDataMap[locationId] = locationData
                Log.d(
                    TAG,
                    "Location $locationId loaded with ${locationData.coordinatePoints.size} coordinate points"
                )
            }

            override fun onLocationUploaded(p0: Int) {
                // Handle the upload event if needed
                // You can leave this empty if you don't need this callback
            }

            override fun onLocationFailed(p0: Int, error: Error) {
                Log.e(TAG, "Failed to load location (code $p0): ${error.message}")
            }
        })
    }

    // Function to trigger loading of location details
    private fun loadLocationDetails(locationId: Int) {
        // Use the LocationManager to set the location ID, which should trigger the listener
        locationManager.setLocationId(locationId)
    }

    // Now our proximity check function can use the stored location data
    fun isUserNearAnyLocation(userLat: Double, userLng: Double): Boolean {
        if (navigineLocations.isEmpty()) {
            Log.w(TAG, "No locations available for matching")
            return false
        }

        return navigineLocations.any { locationInfo: LocationInfo ->
            val locationId = locationInfo.getId()
            val locationData = locationDataMap[locationId]

            if (locationData != null && locationData.coordinatePoints.isNotEmpty()) {
                // Check if user is near any coordinate point in this location
                locationData.coordinatePoints.any { point ->
                    isPointNearby(userLat, userLng, point.latitude, point.longitude)
                }
            } else {
                // Either location data not loaded yet or no coordinate points
                false
            }
        }
    }

    private fun isPointNearby(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Boolean {
        return abs(lat1 - lat2) < 0.0018 &&
                abs(lng1 - lng2) < 0.0018 &&
                calculateHaversineDistance(lat1, lng1, lat2, lng2) <= 200
    }


    // Haversine distance calculation
    private fun calculateHaversineDistance(lat1: Double, lon1: Double,
                                           lat2: Double, lon2: Double): Double {
        val R = 6371000 // Earth radius in meters
        val φ1 = lat1 * Math.PI / 180
        val φ2 = lat2 * Math.PI / 180
        val Δφ = (lat2 - lat1) * Math.PI / 180
        val Δλ = (lon2 - lon1) * Math.PI / 180

        val a = sin(Δφ / 2) * sin(Δφ / 2) +
                cos(φ1) * cos(φ2) *
                sin(Δλ / 2) * sin(Δλ / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c
    }

    private fun getAddressFromLocation(latitude: Double, longitude: Double) {
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            // Handle API level differences for Android 13 (Tiramisu)
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                // For Android 13 and above
                geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                    processAddresses(addresses)
                }
            } else {
                // For Android 12 and below
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (addresses != null) {
                    processAddresses(addresses)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }



    private fun processAddresses(addresses: List<Address>) {
        if (addresses.isNotEmpty()) {
            val address = addresses[0]
            val locationName = address.featureName ?: address.locality ?: "Unknown"

            runOnUiThread {
                locationNameText.text = locationName
                locationInfoText.text = if (isNearNavigineLocation) {
                    "Within Navigine Location"
                } else {
                    "Regular Location"
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            locationPermissionRequestCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getUserLocation()
                }
            }
            recordAudioPermissionRequestCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startSpeechRecognition()
                } else {
                    Toast.makeText(
                        this,
                        "Permission denied for voice search",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}

/*private fun isUserLocationWithinNavigineLocation(locationInfo: LocationInfo): Boolean {
    // 1. Extract the first valid point from sublocations
    val locationPoint = locationInfo.sublocations
        ?.flatMap { it.points } // Flatten all points from all sublocations
        ?.firstOrNull { it.latitude != 0.0 || it.longitude != 0.0 } // Skip Null Island defaults
        ?: run {
            Log.w("LocationCheck", "No valid coordinates found for location ${locationInfo.id}")
            return false // Fail if no points exist or all are (0.0, 0.0)
        }

    // 2. Calculate distance (assuming userLatitude/userLongitude are defined elsewhere)
    val distance = calculateDistance(
        userLatitude,
        userLongitude,
        locationPoint.latitude,
        locationPoint.longitude
    )

    // 3. Return true if within threshold (e.g., 100 meters)
    return distance <= 100.0
}

// Example distance calculation (Haversine formula)
private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371000 // meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadius * c
}*/
package ol.ko.mapsample

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.graphics.Color
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest

import kotlinx.coroutines.launch

import ol.ko.mapsample.databinding.ActivityMainBinding

import org.osmdroid.api.IGeoPoint
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ClickableIconOverlay
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "OLKO"
        private const val noLocationAccessClickableIconId = 42
        private const val locationTurnedOffClickableIconId = 43

        val startingCenterPoint = GeoPoint(48.1510, 16.3342)
        val pseudoGeoPoint = GeoPoint(48.1498, 16.3352)
    }

    private lateinit var mapView: MapView
    private val viewModel by viewModels<MapViewModel>()
    private val locationProviderReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                viewModel.updateLocationStatus(if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                    LocationStatus.LocationTurnedOn
                else
                    LocationStatus.LocationTurnedOff
                )
            }
        }
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                // Precise location access granted
                viewModel.updateLocationStatus(LocationStatus.LocationAccessGranted)
            }
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                // Only approximate location access granted
                viewModel.updateLocationStatus(LocationStatus.LocationAccessGranted)
            }
            else -> {
                // No location access granted.
                viewModel.updateLocationStatus(LocationStatus.NoLocationAccessGranted)
            }
        }
    }

    private val resolveLocationRequest = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        Log.d(TAG, "onActivityResult() resultCode: ${result.resultCode}")
        // RESULT_OK -1 (OK), RESULT_CANCELED 0 (NO THANKS)
        // sometimes after clicking OK, this second check is executed too early,
        // and the check result is failure even if actually the location is eventually turned on,
        // hence the delay -> TODO recheck
        Thread.sleep(500)
        checkDeviceLocationSettings(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Configuration.getInstance().load(applicationContext, getPreferences(Context.MODE_PRIVATE))
        // userAgentValue will be set and the configuration saved by the load() itself when calling for the first time

        mapView = binding.map

        requestLocationPermission()
        registerReceiver(locationProviderReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    Log.d(TAG, "location status ${uiState.locationStatus}")
                    when(uiState.locationStatus) {
                        LocationStatus.Undefined -> {}
                        LocationStatus.NoLocationAccessGranted ->
                            handleLocationAccessIcon(false)
                        LocationStatus.LocationAccessGranted -> {
                            handleLocationAccessIcon(true)
                            checkDeviceLocationSettings()
                        }
                        LocationStatus.LocationTurnedOff, LocationStatus.LocationTurnedOn ->
                            handleLocationActiveIcon(uiState.locationStatus == LocationStatus.LocationTurnedOn)
                    }
                }
            }
        }

        initMap()
    }

    override fun onDestroy() {
        unregisterReceiver(locationProviderReceiver)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    private fun requestLocationPermission() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    private fun checkDeviceLocationSettings(resolve: Boolean = true) {
        Log.d(TAG, "checking location turned on/off")
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val locationSettingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest).build()
        val settingsClient = LocationServices.getSettingsClient(this)
        val locationSettingsResponseTask = settingsClient.checkLocationSettings(locationSettingsRequest)
        locationSettingsResponseTask.addOnCompleteListener { locationSettingsResponse ->
            Log.d(TAG, "OnCompleteListener() locationSettingsResponse.isSuccessful: ${locationSettingsResponse.isSuccessful}")
            if (locationSettingsResponse.isSuccessful) {
                viewModel.updateLocationStatus(LocationStatus.LocationTurnedOn) // TODO anything else?
            }
        }
        locationSettingsResponseTask.addOnFailureListener { exception ->
            Log.d(TAG, "OnFailureListener() exception is ResolvableApiException: ${exception is ResolvableApiException} resolve: $resolve")
            if (exception is ResolvableApiException && resolve) {
                try {
                    resolveLocationRequest.launch(IntentSenderRequest.Builder(exception.resolution).build())
                } catch (sendIntentException: IntentSender.SendIntentException) {
                    Log.d(TAG, "Error getting location settings resolution: ${sendIntentException.message}")
                }
            } else {
                viewModel.updateLocationStatus(LocationStatus.LocationTurnedOff)
            }
        }
    }

    private fun initMap() {
        mapView.controller.setZoom(19.0)
        mapView.controller.setCenter(startingCenterPoint)

        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
        mapView.overlays.add(locationOverlay)

        mapView.overlays.add(ScaleBarOverlay(mapView))

        // phone's orientation
//        val compassOverlay = CompassOverlay(this, mapView)
//        compassOverlay.enableCompass()
//        mapView.overlays.add(compassOverlay)

        // no indication that the current view is rotated
//        val rotationGestureOverlay = RotationGestureOverlay(mapView)
//        rotationGestureOverlay.isEnabled = true
//        mapView.overlays.add(rotationGestureOverlay)

        val copyrightOverlay = CopyrightOverlay(this)
        mapView.overlays.add(copyrightOverlay)

        mapView.setMultiTouchControls(true)
    }

    private fun handleLocationAccessIcon(granted: Boolean) {
        val foundIdx = mapView.overlays.indexOfFirst { (it as? ClickableIconOverlay<*>)?.id == noLocationAccessClickableIconId }
        if (granted) {
            if (foundIdx != -1) {
                mapView.overlays.removeAt(foundIdx)
            }
        } else if (foundIdx == -1) {
            Log.d(TAG, "showing no gps permission icon")
            val icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_baseline_gps_not_fixed_24, theme)?.apply {
                setTint(Color.RED)
            }
            val dummyData = 43
            val noLocationAccessIcon = object : ClickableIconOverlay<Int>(dummyData) {
                override fun onMarkerClicked(
                    mapView: MapView?, markerId: Int,
                    makerPosition: IGeoPoint?, markerData: Int? ): Boolean {
                    Log.d(TAG, "no location permission icon clicked")
                    requestLocationPermission()
                    return true
                }
            }.set(noLocationAccessClickableIconId, pseudoGeoPoint, icon, dummyData)
            mapView.overlays.add(noLocationAccessIcon)
        }
    }

    private fun handleLocationActiveIcon(turnedOn: Boolean) {
        val foundIdx = mapView.overlays.indexOfFirst { (it as? ClickableIconOverlay<*>)?.id == locationTurnedOffClickableIconId }
        if (turnedOn) {
            if (foundIdx != -1) {
                mapView.overlays.removeAt(foundIdx)
            }
        } else if (foundIdx == -1) {
            Log.d(TAG, "showing no-gps icon")
            val icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_baseline_gps_off_24, theme)
            val dummyData = 43
            val noLocationIcon = object : ClickableIconOverlay<Int>(dummyData) {
                override fun onMarkerClicked(
                    mapView: MapView?, markerId: Int,
                    makerPosition: IGeoPoint?, markerData: Int?): Boolean {
                    Log.d(TAG, "location turned off icon clicked")
                    checkDeviceLocationSettings()
                    return true
                }
            }.set(locationTurnedOffClickableIconId, pseudoGeoPoint, icon, dummyData)
            mapView.overlays.add(noLocationIcon)
        }
        mapView.invalidate() // TODO
    }
}
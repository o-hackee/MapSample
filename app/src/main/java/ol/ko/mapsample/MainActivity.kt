package ol.ko.mapsample

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
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

import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
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
        // hence the delay
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
                            handleLocationAccessIcon(granted = false)
                        LocationStatus.LocationAccessGranted -> {
                            handleLocationAccessIcon(granted = true)
                            checkDeviceLocationSettings()
                        }
                        LocationStatus.LocationTurnedOff, LocationStatus.LocationTurnedOn ->
                            handleLocationActiveIcon(turnedOn = uiState.locationStatus == LocationStatus.LocationTurnedOn)
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
        val scaleBarOverlay = ScaleBarOverlay(mapView)
        val copyrightOverlay = CopyrightOverlay(this)
        mapView.overlays.addAll(listOf(
            locationOverlay,
            scaleBarOverlay, copyrightOverlay))

        mapView.setMultiTouchControls(true)
    }

    private fun handleLocationAccessIcon(granted: Boolean) {
        val icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_baseline_gps_not_fixed_24, theme)?.apply {
            setTint(Color.RED)
        } ?: ShapeDrawable()
        handleMyClickableIcon(noLocationAccessClickableIconId, icon, !granted) { requestLocationPermission() }
    }

    private fun handleLocationActiveIcon(turnedOn: Boolean) {
        val icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_baseline_gps_off_24, theme) ?: ShapeDrawable()
        handleMyClickableIcon(locationTurnedOffClickableIconId, icon, !turnedOn) { checkDeviceLocationSettings() }
    }

    private fun handleMyClickableIcon(id: Int, iconDrawable: Drawable, enable: Boolean, onIconClicked: () -> Unit) {
        val foundIdx = mapView.overlays.indexOfFirst { (it as? MyClickableIconOverlay)?.id == id }
        if (enable) {
            if (foundIdx == -1) {
                Log.d(TAG, "showing icon(id=$id)")
                val clickableIconOverlay = MyClickableIconOverlay(id, iconDrawable) { _, iconId ->
                    Log.d(TAG, "icon(id=$iconId) clicked")
                    onIconClicked()
                    true
                }
                mapView.overlays.add(clickableIconOverlay)
            }
        } else {
            if (foundIdx != -1) {
                mapView.overlays.removeAt(foundIdx)
            }
        }
        mapView.invalidate() // TODO
    }
}
package ol.ko.mapsample

import android.Manifest
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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

    lateinit var mapView: MapView
    private val viewModel by viewModels<MapViewModel>()

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                // Precise location access granted
                viewModel.locationPermissionResult(true)
            }
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                // Only approximate location access granted
                viewModel.locationPermissionResult(true)
            } else -> {
                // No location access granted.
                viewModel.locationPermissionResult(false)
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Configuration.getInstance().load(applicationContext, getPreferences(Context.MODE_PRIVATE))
        // userAgentValue will be set and the configuration saved by the load() itself when calling for the first time

        mapView = binding.map

        requestLocationPermission()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    uiState.locationAccessGranted?.let {
                        Log.d("OLKO", "location access ${if (it) "granted" else "denied"}")
                        handleLocationAccessIcon(it)
                    }
                }
            }
        }

        initMap()
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

    private fun initMap() {
        mapView.controller.setZoom(19.0)
        mapView.controller.setCenter(GeoPoint(48.1510, 16.3342))

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
        val noLocationClickableIconId = 42
        val foundIdx = mapView.overlays.indexOfFirst { (it as? ClickableIconOverlay<*>)?.id == noLocationClickableIconId }
        if (granted) {
            if (foundIdx != -1) {
                mapView.overlays.removeAt(foundIdx)
            }
        } else if (foundIdx == -1) {
            val icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_baseline_gps_not_fixed_24, theme)?.apply {
                setTint(Color.RED)
            }
            val dummyData = 43
            val noLocationIcon = object : ClickableIconOverlay<Int>(dummyData) {
                override fun onMarkerClicked(
                    mapView: MapView?,
                    markerId: Int,
                    makerPosition: IGeoPoint?,
                    markerData: Int?
                ): Boolean {
                    Log.d("OLKO", "icon clicked")
                    requestLocationPermission()
                    return true
                }
            }.set(noLocationClickableIconId, GeoPoint(48.1501, 16.3342), icon, dummyData)
            mapView.overlays.add(noLocationIcon)
        }
    }

}
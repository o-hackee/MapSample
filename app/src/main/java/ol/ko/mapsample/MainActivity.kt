package ol.ko.mapsample

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import ol.ko.mapsample.databinding.ActivityMainBinding
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

class MainActivity : AppCompatActivity() {

    lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Configuration.getInstance().load(applicationContext, getPreferences(Context.MODE_PRIVATE))
        // userAgentValue will be set and the configuration saved by the load() itself when calling for the first time

        mapView = binding.map
        mapView.controller.setZoom(19.0)
        mapView.controller.setCenter(GeoPoint(48.1510, 16.3342))
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }
}
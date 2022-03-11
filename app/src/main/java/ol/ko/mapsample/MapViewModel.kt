package ol.ko.mapsample

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LocationStatus {
    Undefined,
    NoLocationAccessGranted,
    LocationAccessGranted, // poor option, let it be for now
    LocationTurnedOff,
    LocationTurnedOn
}

data class MapUiState(
    val locationStatus: LocationStatus = LocationStatus.Undefined
)

class MapViewModel: ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    fun updateLocationStatus(locationStatus: LocationStatus) {
        _uiState.update {
            it.copy(locationStatus = locationStatus)
        }
    }
}
package ol.ko.mapsample

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MapUiState(
    val locationAccessGranted: Boolean? = null,
    val locationTurnedOn: Boolean = false
)

class MapViewModel: ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    fun locationPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(locationAccessGranted = granted)
        }
    }
}
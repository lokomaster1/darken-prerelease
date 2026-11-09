package cz.darken.app.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.darken.app.data.PreferencesRepository
import cz.darken.app.overlay.OverlayStateStore
import kotlinx.coroutines.launch

/** Screen session state survives activity recreation; the service owns runtime state. */
internal class MainViewModel : ViewModel() {
    val dimLevel = mutableIntStateOf(PreferencesRepository.FALLBACK_DEFAULT)
    var autoStartSuppressed = false
    private var observedStopGeneration = OverlayStateStore.state.value.stopGeneration

    init {
        viewModelScope.launch {
            OverlayStateStore.state.collect { state ->
                if (state.stopGeneration != observedStopGeneration) {
                    autoStartSuppressed = true
                    observedStopGeneration = state.stopGeneration
                }
                state.dimLevel?.let { level ->
                    dimLevel.intValue = level
                    autoStartSuppressed = false
                }
            }
        }
    }
}

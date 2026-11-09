package cz.darken.app.overlay

import cz.darken.app.model.DimLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local runtime state. Preferences remain separate from the running overlay. */
internal object OverlayStateStore {
    data class State(
        val starting: Boolean = false,
        val dimLevel: Int? = null,
        val stopGeneration: Long = 0,
    ) {
        val isRunning: Boolean get() = starting || dimLevel != null
    }

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    // All transitions are performed on the main thread by the launcher or service.
    fun starting() {
        mutableState.value = mutableState.value.copy(starting = true)
    }

    fun active(level: Int) {
        mutableState.value = mutableState.value.copy(starting = false, dimLevel = DimLevel.clamp(level))
    }

    fun stopped() {
        val previous = mutableState.value
        mutableState.value = State(stopGeneration = previous.stopGeneration + 1)
    }
}

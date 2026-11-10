package cz.darken.app

import cz.darken.app.model.DimLevel
import cz.darken.app.model.OverlayTint
import cz.darken.app.overlay.OverlayStateStore
import cz.darken.app.ui.normalizeHexDigits
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OverlayRegressionTest {
    @Before fun reset() { OverlayStateStore.stopped() }

    @Test fun pendingStartIsRunningBeforeWindowExists() {
        OverlayStateStore.starting()
        assertTrue(OverlayStateStore.state.value.isRunning)
        assertNull(OverlayStateStore.state.value.dimLevel)
    }

    @Test fun stopDuringStartupClearsPendingState() {
        val generation = OverlayStateStore.state.value.stopGeneration
        OverlayStateStore.starting()
        OverlayStateStore.stopped()
        assertFalse(OverlayStateStore.state.value.isRunning)
        assertEquals(generation + 1, OverlayStateStore.state.value.stopGeneration)
    }

    @Test fun stopAndRestartKeepsStopHistoryForScreenObservers() {
        OverlayStateStore.active(40)
        OverlayStateStore.stopped()
        val generation = OverlayStateStore.state.value.stopGeneration
        OverlayStateStore.starting()
        OverlayStateStore.active(65)
        assertEquals(65, OverlayStateStore.state.value.dimLevel)
        assertEquals(generation, OverlayStateStore.state.value.stopGeneration)
        assertFalse(OverlayStateStore.state.value.starting)
    }

    @Test fun externalLevelsStayWithinSupportedRange() {
        OverlayStateStore.active(-1)
        assertEquals(DimLevel.MIN, OverlayStateStore.state.value.dimLevel)
        OverlayStateStore.active(120)
        assertEquals(DimLevel.MAX, OverlayStateStore.state.value.dimLevel)
    }

    @Test fun hexEditorKeepsSixRgbDigitsAndAddsOpaqueAlpha() {
        val digits = normalizeHexDigits("#aB12ef00!")
        assertEquals("AB12EF", digits)
        assertEquals(0xFFAB12EF.toInt(), OverlayTint.parseHexColor(digits))
        assertNull(OverlayTint.parseHexColor("12345"))
        assertNull(OverlayTint.parseHexColor("GG0000"))
    }

    @Test fun dimmingBoundariesKeepExistingMapping() {
        assertEquals(0f, OverlayTint.alphaForDimLevel(-10), 0f)
        assertEquals(0.8f, OverlayTint.alphaForDimLevel(99), 0f)
        assertEquals(0.8f, OverlayTint.alphaForDimLevel(110), 0f)
    }
}

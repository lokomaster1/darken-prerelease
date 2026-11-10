package cz.darken.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import cz.darken.app.model.DimLevel
import kotlin.math.roundToInt
import cz.darken.app.data.PreferencesRepository

/**
 * Keeps Material3 geometry and ticks, clipping a second default track above the warning boundary.
 * Thickness is increased only via vertical scale (~40 %), not by replacing track/thumb slots.
 */
private const val ThicknessScale = 1.4f

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DimIntensitySlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showHighDimmingRange: Boolean = true,
) {
    val high = showHighDimmingRange && value > DimLevel.WARNING_THRESHOLD
    val warningColor = Color(0xFFEF5350)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val colors = SliderDefaults.colors(
        thumbColor = if (high) warningColor else DarkenPalette.Gold,
        activeTrackColor = DarkenPalette.Gold,
        inactiveTrackColor = DarkenPalette.NavyTrack,
        disabledThumbColor = DarkenPalette.GoldDim,
        disabledActiveTrackColor = DarkenPalette.GoldDim,
        disabledInactiveTrackColor = DarkenPalette.NavyTrack,
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 0.dp else 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Slider(
            value = value.toFloat(),
            onValueChange = {
                val level = it.roundToInt()
                onValueChange(if (showHighDimmingRange) DimLevel.snapSliderLevel(level) else DimLevel.clamp(level))
            },
            valueRange = PreferencesRepository.MIN_DIM.toFloat()..PreferencesRepository.MAX_DIM.toFloat(),
            steps = PreferencesRepository.MAX_DIM - 1,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleY = if (compact) 1f else ThicknessScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                },
            colors = colors,
            track = { state ->
                Box {
                    SliderDefaults.Track(sliderState = state, colors = colors, enabled = enabled)
                    if (high) {
                        SliderDefaults.Track(
                            sliderState = state,
                            colors = colors.copy(activeTrackColor = warningColor),
                            enabled = enabled,
                            modifier = Modifier.drawWithContent {
                                val fraction = DimLevel.WARNING_THRESHOLD.toFloat() / DimLevel.MAX
                                val boundary = size.width * if (isRtl) 1f - fraction else fraction
                                clipRect(
                                    left = if (isRtl) 0f else boundary,
                                    right = if (isRtl) boundary else size.width,
                                ) { this@drawWithContent.drawContent() }
                            },
                            // Material3 otherwise colors the end dot with the active track color.
                            drawStopIndicator = { offset ->
                                drawCircle(
                                    color = if (enabled) DarkenPalette.Gold else DarkenPalette.GoldDim,
                                    radius = 2.dp.toPx(),
                                    center = offset,
                                )
                            },
                        )
                    }
                }
            },
        )
    }
}

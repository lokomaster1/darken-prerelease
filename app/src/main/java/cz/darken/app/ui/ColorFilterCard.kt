package cz.darken.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cz.darken.app.R
import cz.darken.app.model.OverlayTint
import cz.darken.app.ui.components.DarkenCard
import kotlin.math.cos
import kotlin.math.sin

private val customSwatchColors = listOf(
    Color(0xFF8B3030), Color(0xFFA34330), Color(0xFFB85030),
    Color(0xFFD4A04A), Color(0xFFE8A838), Color(0xFFC9A227),
    Color(0xFF7A653C), Color(0xFF5C4033), Color(0xFF67454F),
    Color(0xFF533759), Color(0xFF3D2B52), Color(0xFF34344C),
    Color(0xFF2A3A4A), Color(0xFF29454B), Color(0xFF22504B),
    Color(0xFF1E3A2F), Color(0xFF4A5743), Color(0xFF6B2020),
)

@Composable
fun ColorFilterCard(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    tintPreset: String,
    resolvedTintArgb: Int,
    savedTintPresets: Map<String, Int>,
    enabled: Boolean,
    onPresetSelected: (String) -> Unit,
    onCustomClick: () -> Unit,
) {
    val expandInteraction = remember { MutableInteractionSource() }
    DarkenCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = enabled,
                    interactionSource = expandInteraction,
                    indication = null,
                ) {
                    onExpandedChange(!expanded)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.filter_color_title),
                style = if (expanded) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodySmall
                },
                color = if (expanded) DarkenPalette.TextPrimary else DarkenPalette.TextMuted,
                modifier = Modifier.weight(1f),
            )
            if (!expanded) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(resolvedTintArgb)),
                )
                Spacer(modifier = Modifier.size(12.dp))
            }
            Switch(
                checked = expanded,
                onCheckedChange = null,
                enabled = enabled,
                interactionSource = expandInteraction,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = DarkenPalette.TextPrimary,
                    checkedTrackColor = DarkenPalette.Gold,
                    uncheckedThumbColor = DarkenPalette.TextMuted,
                    uncheckedTrackColor = DarkenPalette.NavyTrack,
                    uncheckedBorderColor = DarkenPalette.NavyTrack,
                ),
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                val defaultLabels = listOf(
                    R.string.tint_gray,
                    R.string.palette_graphite,
                    R.string.palette_burgundy,
                )
                OverlayTint.presetSlots.forEachIndexed { index, preset ->
                    val savedArgb = savedTintPresets[preset]
                    val labelRes = if (savedArgb == null) {
                        defaultLabels[index]
                    } else {
                        filterColorPalette[savedArgb]
                    }
                    PresetColorOption(
                        argb = savedArgb ?: OverlayTint.resolveArgb(preset, resolvedTintArgb),
                        label = if (labelRes != null) stringResource(labelRes) else (index + 1).toString(),
                        selected = tintPreset == preset,
                        enabled = enabled,
                        onClick = { onPresetSelected(preset) },
                        modifier = Modifier.weight(1f),
                    )
                }
                PresetColorOption(
                    argb = resolvedTintArgb,
                    label = stringResource(R.string.tint_custom),
                    selected = false,
                    enabled = enabled,
                    onClick = onCustomClick,
                    modifier = Modifier.weight(1f),
                    isCustom = true,
                )
            }
        }
    }
}

@Composable
private fun PresetColorOption(
    argb: Int,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    isCustom: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isCustom) {
            SegmentedColorSwatch(
                onClick = { if (enabled) onClick() },
                modifier = Modifier.size(44.dp),
            )
        } else {
            ColorSwatch(
                argb = argb,
                selected = selected,
                onClick = { if (enabled) onClick() },
                modifier = Modifier.size(44.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) DarkenPalette.Gold else DarkenPalette.TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SegmentedColorSwatch(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .clip(CircleShape)
            .border(1.dp, DarkenPalette.NavyTrack, CircleShape)
            .clickable(onClick = onClick),
    ) {
        // An opaque base keeps antialiasing seams between sectors fully filled.
        drawCircle(customSwatchColors.first())
        val sweep = 360f / customSwatchColors.size
        customSwatchColors.forEachIndexed { index, color ->
            drawArc(
                color = color,
                startAngle = -90f + index * sweep,
                sweepAngle = sweep,
                useCenter = true,
            )
        }
        val radius = size.minDimension / 2f
        customSwatchColors.indices.forEach { index ->
            val angle = Math.toRadians((-90f + index * sweep).toDouble())
            drawLine(
                color = DarkenPalette.NavyDeep,
                start = center,
                end = center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * radius,
                strokeWidth = 0.5.dp.toPx(),
            )
        }
    }
}

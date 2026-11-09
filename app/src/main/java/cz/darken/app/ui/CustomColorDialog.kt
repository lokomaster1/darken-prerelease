package cz.darken.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import cz.darken.app.R
import cz.darken.app.data.PreferencesRepository
import cz.darken.app.model.OverlayTint
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CustomColorDialog(
    initialArgb: Int,
    initialIntensity: Int,
    savedTintPresets: Map<String, Int>,
    onPreviewChange: (Int, Int) -> Unit,
    onSavePreset: (Int, Int) -> Unit,
    onResetPresets: () -> Unit,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val editor = remember(initialArgb, initialIntensity) { CustomColorEditorState(initialArgb, initialIntensity) }
    val scope = rememberCoroutineScope()
    val glowTransition = rememberInfiniteTransition(label = "presetGlow")
    val glowAlpha by glowTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "presetGlowAlpha",
    )
    var hideKeyboardIfVisible by remember { mutableStateOf<(() -> Boolean)?>(null) }

    val parsedArgb = editor.parseSelection()
    val canSavePreset = editor.colorEdited && parsedArgb != null
    val hexError = editor.hexDigits.isNotEmpty() && parsedArgb == null

    val previewBorderColor = parsedArgb?.let { Color(it) } ?: DarkenPalette.Gold

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = DarkenPalette.TextPrimary,
        unfocusedTextColor = DarkenPalette.TextPrimary,
        disabledTextColor = DarkenPalette.TextMuted,
        focusedLabelColor = DarkenPalette.TextMuted,
        unfocusedLabelColor = DarkenPalette.TextMuted,
        cursorColor = DarkenPalette.Gold,
        focusedBorderColor = previewBorderColor,
        unfocusedBorderColor = if (parsedArgb != null) previewBorderColor else DarkenPalette.NavyTrack,
        errorBorderColor = Color(OverlayTint.RedArgb),
        errorLabelColor = DarkenPalette.TextPrimary,
        errorTextColor = DarkenPalette.TextPrimary,
    )

    LaunchedEffect(editor.pendingSliderSelection) {
        val argb = editor.pendingSliderSelection ?: return@LaunchedEffect
        editor.pendingSliderSelection = null
        editor.editColor(argb, sliderHue = editor.hueDegrees)
    }

    LaunchedEffect(editor.selectedArgb, editor.previewIntensity, editor.previewEnabled) {
        onPreviewChange(editor.selectedArgb, if (editor.previewEnabled) editor.previewIntensity else 0)
    }

    BasicAlertDialog(
        onDismissRequest = {
            if (hideKeyboardIfVisible?.invoke() != true) onDismiss()
        },
        properties = DialogProperties(decorFitsSystemWindows = false),
        modifier = Modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (waitForUpOrCancellation() != null) hideKeyboardIfVisible?.invoke()
                }
            }
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val density = LocalDensity.current
        val ime = WindowInsets.ime
        val keyboardVisible = ime.getBottom(density) > 0
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val scrollState = rememberScrollState()

        fun hideKeyboard() {
            focusManager.clearFocus()
            keyboardController?.hide()
        }

        SideEffect {
            hideKeyboardIfVisible = {
                val visible = ime.getBottom(density) > 0
                if (visible) hideKeyboard()
                visible
            }
        }

        LaunchedEffect(keyboardVisible, scrollState.maxValue) {
            if (keyboardVisible) scrollState.scrollTo(scrollState.maxValue)
        }

        Surface(
            shape = AlertDialogDefaults.shape,
            color = DarkenPalette.NavyCard,
            contentColor = DarkenPalette.TextMuted,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = if (keyboardVisible) 8.dp else 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.custom_color_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = DarkenPalette.TextPrimary,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        filterColorPalette.forEach { (argb, labelRes) ->
                            val label = stringResource(labelRes)
                            ColorSwatch(
                                argb = argb,
                                selected = !editor.showHueSlider && argb == parsedArgb,
                                onClick = {
                                    val deselect = editor.colorEdited && !editor.showHueSlider && editor.parseSelection() == argb
                                    editor.showHueSlider = false
                                    editor.pendingSliderSelection = null
                                    if (deselect) {
                                        editor.hexDigits = ""
                                        editor.colorEdited = false
                                        editor.selectionSaved = false
                                        editor.selectedArgb = initialArgb
                                        editor.previewEnabled = false
                                    } else {
                                        editor.editColor(argb)
                                    }
                                },
                                modifier = Modifier.size(36.dp).semantics { contentDescription = label },
                            )
                        }
                        RainbowSwatch(
                            modifier = Modifier.size(36.dp),
                            selected = editor.showHueSlider,
                            onClick = {
                                editor.showHueSlider = true
                                val hsv = argbToHsv(editor.selectedArgb)
                                if (hsv[1] < 0.1f) {
                                    editor.editColor(overlayTintArgbFromHue(hsv[0]))
                                } else {
                                    editor.editColor(editor.selectedArgb)
                                }
                            },
                        )
                    }
                    if (editor.showHueSlider) {
                        HueSlider(
                            hue = editor.hueDegrees,
                            previewColor = Color(editor.selectedArgb),
                            onHueChange = { editor.previewHue(it) },
                            onInteractionEnd = { editor.scheduleSliderSelection() },
                        )
                    }
                    ColorPreviewIntensitySlider(
                        intensity = editor.previewIntensity,
                        onIntensityChange = {
                            editor.previewIntensity = it
                            if (editor.parseSelection() != null) editor.previewEnabled = true
                        },
                    )
                    OutlinedTextField(
                        value = editor.hexDigits,
                        onValueChange = { value ->
                            if (!editor.acceptHexInput) return@OutlinedTextField
                            val normalized = normalizeHexDigits(value)
                            if (normalized == editor.hexDigits) return@OutlinedTextField
                            editor.selectionSaved = false
                            editor.colorEdited = true
                            editor.hexDigits = normalized
                            val parsed = editor.parseSelection()
                            if (parsed != null) {
                                editor.editColor(parsed)
                            }
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.custom_color_hex_label),
                                color = DarkenPalette.TextMuted,
                            )
                        },
                        placeholder = {
                            Text(
                                text = "RRGGBB",
                                color = DarkenPalette.TextMuted,
                            )
                        },
                        prefix = {
                            Text(
                                text = "#",
                                color = DarkenPalette.TextPrimary,
                            )
                        },
                        singleLine = true,
                        isError = hexError,
                        modifier = Modifier.fillMaxWidth().onFocusChanged {
                            if (it.isFocused) editor.acceptHexInput = true
                        },
                        colors = textFieldColors,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { hideKeyboard() }),
                    )
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(
                            if (canSavePreset) {
                                R.string.custom_color_save_to_slot
                            } else {
                                R.string.custom_color_presets
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OverlayTint.presetSlots.forEachIndexed { index, preset ->
                            val label = stringResource(
                                if (canSavePreset) R.string.custom_color_save_slot else R.string.custom_color_select_slot,
                                index + 1,
                            )
                            val presetArgb = savedTintPresets[preset]
                                ?: OverlayTint.resolveArgb(preset, initialArgb)
                            val indicatorColor = Color(presetArgb)
                            val highlight = remember { Animatable(0f) }
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .drawBehind {
                                        if (canSavePreset) {
                                            drawCircle(
                                                color = DarkenPalette.Gold.copy(alpha = glowAlpha * 0.35f),
                                                radius = 20.dp.toPx(),
                                                style = Stroke(width = 12.dp.toPx()),
                                            )
                                            drawCircle(
                                                color = DarkenPalette.Gold.copy(alpha = glowAlpha),
                                                radius = 20.dp.toPx(),
                                                style = Stroke(width = 5.dp.toPx()),
                                            )
                                        }
                                    }
                                    .clip(CircleShape)
                                    .semantics { contentDescription = label }
                                    .clickable(role = Role.Button) {
                                        val newArgb = editor.parseSelection()
                                        // Ignore late IME edits until the user focuses the hex field again.
                                        editor.acceptHexInput = false
                                        hideKeyboard()
                                        if (editor.colorEdited && newArgb != null) {
                                            editor.hexDigits = ""
                                            editor.previewEnabled = false
                                            editor.previewIntensity = editor.previewIntensity.coerceIn(
                                                PreferencesRepository.MIN_DIM,
                                                PreferencesRepository.MAX_SAVED_PREVIEW_DIM,
                                            )
                                            onSavePreset(index, newArgb)
                                        } else {
                                            editor.updateLocalColor(presetArgb)
                                            editor.showHueSlider = false
                                        }
                                        editor.colorEdited = false
                                        editor.pendingSliderSelection = null
                                        editor.selectionSaved = true
                                        scope.launch {
                                            highlight.snapTo(1f)
                                            highlight.animateTo(0f, tween(durationMillis = 200))
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .drawBehind {
                                            val ringWidth = 6.24.dp.toPx()
                                            val radius = size.minDimension / 2f
                                            drawCircle(
                                                color = indicatorColor,
                                                radius = radius - ringWidth / 2f,
                                                style = Stroke(width = ringWidth),
                                            )
                                            if (indicatorColor.luminance() < 0.12f) {
                                                val outlineWidth = 1.dp.toPx()
                                                drawCircle(
                                                    color = DarkenPalette.TextMuted.copy(alpha = 0.65f),
                                                    radius = radius - outlineWidth / 2f,
                                                    style = Stroke(width = outlineWidth),
                                                )
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = (index + 1).toString(),
                                        color = lerp(DarkenPalette.Gold, Color.White, highlight.value * 0.35f),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = onResetPresets) {
                            Text(stringResource(R.string.custom_color_reset_slots), color = DarkenPalette.Gold)
                        }
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.settings_cancel), color = DarkenPalette.TextPrimary)
                    }
                    TextButton(
                        onClick = {
                            val argb = editor.parseSelection()
                            if (argb != null && !editor.selectionSaved) editor.editColor(argb)
                            onConfirm(editor.previewIntensity)
                        },
                        enabled = editor.hexDigits.isEmpty() || parsedArgb != null,
                    ) {
                        Text(stringResource(R.string.settings_ok), color = DarkenPalette.Gold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPreviewIntensitySlider(
    intensity: Int,
    onIntensityChange: (Int) -> Unit,
) {
    val label = stringResource(R.string.custom_color_preview_intensity, intensity)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        DimIntensitySlider(
            value = intensity,
            onValueChange = onIntensityChange,
            enabled = true,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            compact = true,
        )
    }
}

@Composable
fun ColorSwatch(
    argb: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(argb))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) DarkenPalette.Gold else DarkenPalette.NavyTrack,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {}
}

@Composable
internal fun RainbowSwatch(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.sweepGradient(
                    colors = listOf(
                        Color.Red,
                        Color.Yellow,
                        Color.Green,
                        Color.Cyan,
                        Color.Blue,
                        Color.Magenta,
                        Color.Red,
                    ),
                ),
            )
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) DarkenPalette.Gold else DarkenPalette.NavyTrack,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

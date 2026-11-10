package cz.darken.app.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.TimePickerSelectionMode
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cz.darken.app.R
import cz.darken.app.timer.TimerAction
import cz.darken.app.timer.OverlayTimer
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import java.util.Date

@Composable
fun TimerDialog(
    timer: OverlayTimer,
    startupTimer: OverlayTimer,
    exactAlarmGranted: Boolean,
    overlayGranted: Boolean,
    onEnabledChange: (TimerAction, Boolean) -> Unit,
    onTimeChange: (TimerAction, Int, Int) -> Unit,
    onRepeatChange: (TimerAction, Boolean) -> Unit,
    onRequestPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    var editingAction by rememberSaveable { mutableStateOf<TimerAction?>(null) }
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val maxHeight = with(LocalDensity.current) { (windowHeight * 0.9f).toDp() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .heightIn(max = maxHeight),
            shape = RoundedCornerShape(16.dp),
            color = DarkenPalette.NavyCard,
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(R.drawable.ic_timer), null, tint = DarkenPalette.Gold, modifier = Modifier.size(26.dp))
                    Text(stringResource(R.string.timer_title), style = MaterialTheme.typography.titleMedium, color = DarkenPalette.TextPrimary)
                }
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TimerSection(
                        timer, TimerAction.STOP, exactAlarmGranted, overlayGranted, false,
                        onEnabledChange, onRepeatChange, { editingAction = TimerAction.STOP },
                        onRequestPermission, onRequestOverlayPermission,
                    )
                    HorizontalDivider(color = DarkenPalette.NavyTrack)
                    TimerSection(
                        startupTimer, TimerAction.START, exactAlarmGranted, overlayGranted,
                        startupTimer.conflictsWith(timer), onEnabledChange, onRepeatChange,
                        { editingAction = TimerAction.START }, onRequestPermission, onRequestOverlayPermission,
                    )
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.timer_close), color = DarkenPalette.Gold)
                }
            }
        }
    }
    editingAction?.let { action ->
        val selected = if (action == TimerAction.STOP) timer else startupTimer
        key(action, selected.hour, selected.minute) {
            ScheduledTimePicker(selected.hour, selected.minute, action, onDismiss = { editingAction = null }) { hour, minute ->
                onTimeChange(action, hour, minute)
                editingAction = null
            }
        }
    }
}

@Composable
private fun TimerSection(
    timer: OverlayTimer,
    action: TimerAction,
    exactAlarmGranted: Boolean,
    overlayGranted: Boolean,
    conflicting: Boolean,
    onEnabledChange: (TimerAction, Boolean) -> Unit,
    onRepeatChange: (TimerAction, Boolean) -> Unit,
    onEditTime: () -> Unit,
    onRequestPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
) {
    val starting = action == TimerAction.START
    // Expansion is screen state, independent of whether a persistent timer is enabled.
    var expanded by rememberSaveable(action) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "timerSectionChevron",
    )
    val expansionLabel = stringResource(if (expanded) R.string.timer_settings_collapse else R.string.timer_settings_expand)
    val expansionState = stringResource(if (expanded) R.string.timer_expanded else R.string.timer_collapsed)
    val context = LocalContext.current
    val time = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, timer.hour)
        set(Calendar.MINUTE, timer.minute)
    }.time
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics { stateDescription = expansionState }
                .clickable(role = Role.Button, onClickLabel = expansionLabel) { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (starting) R.string.timer_start_section else R.string.timer_stop_section),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = DarkenPalette.TextPrimary,
            )
            Box(
                modifier = Modifier.size(36.dp).background(DarkenPalette.NavyTrack, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    tint = DarkenPalette.Gold,
                    modifier = Modifier.size(20.dp).rotate(chevronRotation),
                )
            }
        }
        // Only this section owns the height transition; do not animate the dialog size.
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Top,
            ),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Top,
            ),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TimerSwitch(stringResource(if (starting) R.string.timer_start_enable else R.string.timer_enable),
                    timer.enabled, true, { onEnabledChange(action, it) })
                TimerSwitch(stringResource(R.string.timer_repeat), timer.repeatDaily, true,
                    { onRepeatChange(action, it) })
                Text(stringResource(if (starting) R.string.timer_start_time else R.string.timer_time),
                    color = DarkenPalette.TextMuted, style = MaterialTheme.typography.bodyMedium)
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onEditTime),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DarkenPalette.Gold),
                    color = DarkenPalette.NavyCard,
                ) {
                    Text(
                        DateFormat.getTimeFormat(context).format(time),
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.headlineMedium,
                        color = DarkenPalette.Gold,
                    )
                }
                Text(stringResource(R.string.timer_time_hint), color = DarkenPalette.TextMuted,
                    style = MaterialTheme.typography.bodySmall)
                if (timer.enabled && !exactAlarmGranted) {
                    Text(stringResource(if (starting) R.string.timer_start_permission_hint else R.string.timer_permission_hint),
                        color = DarkenPalette.TextMuted, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onRequestPermission) {
                        Text(stringResource(R.string.timer_permission_button), color = DarkenPalette.Gold)
                    }
                }
                if (timer.enabled && starting && !overlayGranted) {
                    Text(stringResource(R.string.timer_start_overlay_hint), color = DarkenPalette.TextMuted,
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onRequestOverlayPermission) {
                        Text(stringResource(R.string.permission_overlay_button), color = DarkenPalette.Gold)
                    }
                }
            }
        }
        // An enabled timer keeps its status visible even with its settings collapsed.
        if (timer.enabled) {
            Surface(shape = RoundedCornerShape(8.dp), color = DarkenPalette.NavyTrack) {
                Text(
                    text = when {
                        conflicting -> stringResource(R.string.timer_conflict)
                        !exactAlarmGranted -> stringResource(R.string.timer_waiting_permission)
                        starting && !overlayGranted -> stringResource(R.string.timer_start_overlay_hint)
                        else -> nextTimerLabel(timer, starting)
                    },
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkenPalette.TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun TimerSwitch(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) DarkenPalette.TextPrimary else DarkenPalette.TextMuted,
        )
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DarkenPalette.TextPrimary,
                checkedTrackColor = DarkenPalette.Gold,
                uncheckedThumbColor = DarkenPalette.TextMuted,
                uncheckedTrackColor = DarkenPalette.NavyTrack,
            ),
        )
    }
}

@Composable
private fun nextTimerLabel(timer: OverlayTimer, starting: Boolean): String {
    val context = LocalContext.current
    if (timer.nextTriggerAt <= 0) return stringResource(R.string.timer_waiting_permission)
    val date = Date(timer.nextTriggerAt)
    val day = Instant.ofEpochMilli(timer.nextTriggerAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = java.time.LocalDate.now()
    val time = DateFormat.getTimeFormat(context).format(date)
    return when (day) {
        today -> stringResource(if (starting) R.string.timer_start_next_today else R.string.timer_next_today, time)
        today.plusDays(1) -> stringResource(if (starting) R.string.timer_start_next_tomorrow else R.string.timer_next_tomorrow, time)
        else -> stringResource(if (starting) R.string.timer_start_next_date else R.string.timer_next_date, DateFormat.getMediumDateFormat(context).format(date), time)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduledTimePicker(initialHour: Int, initialMinute: Int, action: TimerAction, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour, initialMinute, DateFormat.is24HourFormat(LocalContext.current))
    var keyboard by rememberSaveable { mutableStateOf(false) }
    val editTimeLabel = stringResource(R.string.timer_time_hint)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkenPalette.NavyCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (action == TimerAction.START) R.string.timer_start_time else R.string.timer_time),
                    modifier = Modifier.weight(1f), color = DarkenPalette.TextPrimary)
                if (keyboard) {
                    IconButton(onClick = { keyboard = false }) {
                        Icon(painterResource(R.drawable.ic_timer), stringResource(R.string.timer_clock_input), tint = DarkenPalette.Gold)
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (keyboard) {
                    TimeInput(state)
                } else {
                    TimePicker(
                        state,
                        layoutType = TimePickerLayoutType.Vertical,
                        modifier = Modifier.semantics {
                            customActions = listOf(CustomAccessibilityAction(editTimeLabel) { keyboard = true; true })
                        }.pointerInput(state) {
                            // Material 3 1.3.2 uses an 80 dp header, with two 96 dp selectors
                            // and a 24 dp separator. Intercept only these displayed digits;
                            // the standard dial and AM/PM control keep their own gestures.
                            val selectorWidth = 216.dp.toPx()
                            val headerHeight = 80.dp.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown(pass = PointerEventPass.Initial)
                                val left = if (state.is24hour) (size.width - selectorWidth) / 2 else 0f
                                if (down.position.y < headerHeight &&
                                    down.position.x >= left && down.position.x < left + selectorWidth
                                ) {
                                    down.consume()
                                    val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                                    if (up != null) {
                                        up.consume()
                                        state.selection = if (down.position.x - left < selectorWidth / 2)
                                            TimePickerSelectionMode.Hour else TimePickerSelectionMode.Minute
                                        keyboard = true
                                    }
                                }
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(stringResource(R.string.timer_confirm), color = DarkenPalette.Gold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.timer_cancel), color = DarkenPalette.TextMuted) }
        },
    )
}

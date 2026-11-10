package cz.darken.app.ui

import android.content.ContextWrapper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cz.darken.app.R
import cz.darken.app.data.PreferencesRepository
import cz.darken.app.model.DimLevel
import cz.darken.app.overlay.OverlayService
import kotlinx.coroutines.launch

/** Retained on rotation, reset with a new activity session. */
internal class HighDimmingWarningSession : ViewModel() {
    var accepted by mutableStateOf(false)
}

/** Ask before applying stronger dimming; suspend the existing overlay while the dialog is visible. */
@Composable
fun rememberHighDimmingChange(value: Int, onChange: (Int) -> Unit): (Int) -> Unit {
    val uiContext = LocalContext.current
    val context = uiContext.applicationContext
    val preferences = remember(context) { PreferencesRepository(context) }
    val hidden by preferences.highDimmingWarningHidden.collectAsStateWithLifecycle<Boolean?>(null)
    val owner = remember(uiContext) {
        generateSequence(uiContext) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<ViewModelStoreOwner>()
            .first()
    }
    val session = remember(owner) { ViewModelProvider(owner)[HighDimmingWarningSession::class.java] }
    val scope = rememberCoroutineScope()
    var pending by rememberSaveable { mutableStateOf<Int?>(null) }

    fun accept(hide: Boolean) {
        val level = pending ?: return
        pending = null
        session.accepted = true
        if (hide) scope.launch { preferences.setHighDimmingWarningHidden(true) }
        onChange(level)
    }

    // Also warn when a new session opens with a saved or externally selected high level.
    LaunchedEffect(value, hidden, session.accepted, pending) {
        if (hidden == true && pending != null) {
            accept(hide = false)
        } else if (value > DimLevel.WARNING_THRESHOLD && hidden == false && !session.accepted && pending == null) {
            pending = value
            onChange(DimLevel.WARNING_THRESHOLD)
        }
    }

    if (pending != null && hidden == false) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, _ ->
                OverlayService.setHighDimmingWarningVisible(
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
                )
            }
            lifecycle.addObserver(observer)
            OverlayService.setHighDimmingWarningVisible(
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
            )
            onDispose {
                lifecycle.removeObserver(observer)
                OverlayService.setHighDimmingWarningVisible(false)
            }
        }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.high_dimming_title)) },
            text = { Text(stringResource(R.string.high_dimming_message)) },
            confirmButton = {
                TextButton(onClick = { accept(hide = false) }) {
                    Text(stringResource(R.string.high_dimming_continue), color = DarkenPalette.Gold)
                }
            },
            dismissButton = {
                TextButton(onClick = { accept(hide = true) }) {
                    Text(stringResource(R.string.high_dimming_hide), color = DarkenPalette.Gold)
                }
            },
        )
    }

    return { requested ->
        if (requested > DimLevel.WARNING_THRESHOLD &&
            hidden != true && !session.accepted
        ) {
            pending = requested
            onChange(DimLevel.WARNING_THRESHOLD)
        } else {
            onChange(requested)
        }
    }
}

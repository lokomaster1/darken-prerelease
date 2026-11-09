package cz.darken.app

import android.content.Intent
import androidx.core.net.toUri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import cz.darken.app.data.PreferencesRepository
import cz.darken.app.locale.LocaleHelper
import cz.darken.app.model.OverlayTint
import cz.darken.app.overlay.ColorPreviewOverlay
import cz.darken.app.overlay.OverlayLauncher
import cz.darken.app.overlay.OverlayService
import cz.darken.app.overlay.OverlayStateStore
import cz.darken.app.tile.QsTilePlacement
import cz.darken.app.ui.CustomColorDialog
import cz.darken.app.ui.DarkenTheme
import cz.darken.app.ui.MainScreen
import cz.darken.app.ui.MainViewModel
import cz.darken.app.ui.PrivacyPolicyDialog
import cz.darken.app.ui.QsTileAddedDialog
import cz.darken.app.ui.SettingsDialog
import cz.darken.app.ui.SystemPermissionsDialog
import cz.darken.app.util.PermissionIntents
import cz.darken.app.widget.WidgetPlacement
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var preferences: PreferencesRepository
    private lateinit var colorPreview: ColorPreviewOverlay

    private val viewModel by lazy { ViewModelProvider(this)[MainViewModel::class.java] }
    private val dimLevelState get() = viewModel.dimLevel
    private var showPermissionsDialog = mutableStateOf(false)
    private var showSettingsDialog = mutableStateOf(false)
    private var showCustomColorDialog = mutableStateOf(false)
    private var showPrivacyPolicy = mutableStateOf(false)
    private var showQsTileAddedDialog = mutableStateOf(false)
    private var permissionRefreshKey = mutableIntStateOf(0)
    private var overlayDisabledByUser: Boolean
        get() = viewModel.autoStartSuppressed
        set(value) { viewModel.autoStartSuppressed = value }
    private var previewInitialArgb = OverlayTint.GrayArgb
    private var previewInitialIntensity = PreferencesRepository.DEFAULT_PREVIEW_DIM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferences = PreferencesRepository(applicationContext)
        colorPreview = ColorPreviewOverlay(this)

        if (savedInstanceState?.getBoolean(KEY_OVERLAY_DISABLED_BY_USER, false) == true) {
            overlayDisabledByUser = true
        }
        showSettingsDialog.value = savedInstanceState?.getBoolean(KEY_SETTINGS_DIALOG, false) ?: false

        if (intent?.getBooleanExtra(EXTRA_OPEN_PERMISSIONS, false) == true) {
            showPermissionsDialog.value = true
        }

        setContent {
            val defaultDim by preferences.defaultDimLevel.collectAsStateWithLifecycle(
                initialValue = PreferencesRepository.FALLBACK_DEFAULT,
            )
            val hasSavedDefault by preferences.hasSavedDefaultDim.collectAsStateWithLifecycle(
                initialValue = false,
            )
            val appLanguage by preferences.appLanguage.collectAsStateWithLifecycle(
                initialValue = PreferencesRepository.LANG_SYSTEM,
            )
            val autoStartOnLaunch by preferences.autoStartOnLaunch.collectAsStateWithLifecycle(
                initialValue = false,
            )
            val notificationMode by preferences.notificationMode.collectAsStateWithLifecycle(
                initialValue = PreferencesRepository.NOTIF_MINIMAL,
            )
            val notificationWarningHidden by preferences.notificationWarningHidden.collectAsStateWithLifecycle(
                initialValue = true,
            )
            val tintPreset by preferences.overlayTintPreset.collectAsStateWithLifecycle(
                initialValue = OverlayTint.PRESET_GRAY,
            )
            val resolvedTintArgb by preferences.resolvedOverlayTintArgb.collectAsStateWithLifecycle(
                initialValue = OverlayTint.GrayArgb,
            )
            val savedTintPresets by preferences.savedOverlayTintPresets.collectAsStateWithLifecycle(
                initialValue = emptyMap(),
            )
            val privacyAcknowledged by preferences.privacyAcknowledged.collectAsStateWithLifecycle(
                initialValue = false,
            )
            var dimLevel by dimLevelState
            var snackbarMessage by remember { mutableStateOf<String?>(null) }
            val overlayState by OverlayStateStore.state.collectAsStateWithLifecycle()
            val overlayOn = overlayState.isRunning
            val permissionsOpen by showPermissionsDialog
            val settingsOpen by showSettingsDialog
            val refreshKey by permissionRefreshKey
            val colorExpanded by preferences.colorFilterExpanded.collectAsStateWithLifecycle(
                initialValue = false,
            )


            if (permissionsOpen) {
                @Suppress("UNUSED_VARIABLE")
                val triggerRefresh = refreshKey
                SystemPermissionsDialog(
                    overlayGranted = canDrawOverlays(),
                    notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled(),
                    batteryUnrestricted = batteryUnrestricted(),
                    onOverlayClick = {
                        openSystemSettings { PermissionIntents.openOverlaySettings(this) }
                    },
                    onNotificationsClick = {
                        openSystemSettings { PermissionIntents.openNotificationSettings(this) }
                    },
                    onBatteryClick = {
                        openSystemSettings { PermissionIntents.openBatteryOptimizationSettings(this) }
                    },
                    onAppDetailsClick = {
                        openSystemSettings { PermissionIntents.openAppDetailsSettings(this) }
                    },
                    onDismiss = { showPermissionsDialog.value = false },
                )
            }

            if (showCustomColorDialog.value) {
                CustomColorDialog(
                    initialArgb = previewInitialArgb,
                    initialIntensity = previewInitialIntensity,
                    savedTintPresets = savedTintPresets,
                    onPreviewChange = { argb, intensity ->
                        if (showCustomColorDialog.value) colorPreview.update(argb, intensity)
                    },
                    onSavePreset = { index, argb ->
                        colorPreview.update(argb, 0)
                        lifecycleScope.launch {
                            preferences.saveOverlayTintPreset(OverlayTint.presetSlots[index], argb)
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.custom_color_slot_saved, index + 1),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onResetPresets = {
                        lifecycleScope.launch {
                            preferences.resetOverlayTintPresets()
                            Toast.makeText(
                                this@MainActivity,
                                R.string.custom_color_slots_reset,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onConfirm = { intensity ->
                        closeColorPreview()
                        lifecycleScope.launch { preferences.setColorPreviewIntensity(intensity) }
                    },
                    onDismiss = { closeColorPreview() },
                )
            }

            val showPrivacy = showPrivacyPolicy.value || !privacyAcknowledged

            if (showPrivacy) {
                PrivacyPolicyDialog(
                    firstRun = !privacyAcknowledged,
                    onAccept = {
                        if (!privacyAcknowledged) {
                            lifecycleScope.launch {
                                preferences.setPrivacyAcknowledged(true)
                            }
                        }
                        showPrivacyPolicy.value = false
                    },
                    onDismiss = { showPrivacyPolicy.value = false },
                )
            }

            if (showQsTileAddedDialog.value) {
                QsTileAddedDialog(onDismiss = { showQsTileAddedDialog.value = false })
            }

            if (settingsOpen) {
                val interactiveNotificationsEnabled = remember(refreshKey) {
                    PermissionIntents.areInteractiveNotificationsEnabled(this@MainActivity)
                }
                val widgetPinningSupported = remember(refreshKey) {
                    WidgetPlacement.isSupported(this@MainActivity)
                }
                SettingsDialog(
                    language = appLanguage,
                    autoStartOnLaunch = autoStartOnLaunch,
                    notificationMode = notificationMode,
                    showNotificationWarning = !interactiveNotificationsEnabled && !notificationWarningHidden,
                    onOpenNotificationSettings = {
                        openSystemSettings { PermissionIntents.openInteractiveNotificationSettings(this@MainActivity) }
                    },
                    onHideNotificationWarning = {
                        lifecycleScope.launch { preferences.hideNotificationWarning() }
                    },
                    onLanguageChange = { applyLanguage(it) },
                    onAutoStartChange = { applyAutoStart(it) },
                    onNotificationModeChange = {
                        permissionRefreshKey.intValue++
                        applyNotificationMode(it)
                    },
                    onDismiss = { showSettingsDialog.value = false },
                    onOpenPrivacy = { showPrivacyPolicy.value = true },
                    onOpenGithub = { openGithub() },
                    onOpenContact = { openContactEmail() },
                    widgetPinningSupported = widgetPinningSupported,
                    onAddWidget = {
                        when (WidgetPlacement.requestAddWidget(this@MainActivity)) {
                            WidgetPlacement.Result.Requested -> Unit
                            WidgetPlacement.Result.Unsupported,
                            WidgetPlacement.Result.Error,
                            -> {
                                permissionRefreshKey.intValue++
                                Toast.makeText(
                                    this@MainActivity,
                                    R.string.settings_widget_hint_manual,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                    onAddQsTile = {
                        QsTilePlacement.requestAddTile(this@MainActivity) { result ->
                            when (result) {
                                QsTilePlacement.Result.Added,
                                QsTilePlacement.Result.AlreadyAdded,
                                -> showQsTileAddedDialog.value = true
                                QsTilePlacement.Result.NotAdded,
                                QsTilePlacement.Result.Unsupported,
                                QsTilePlacement.Result.Error,
                                -> Unit
                            }
                        }
                    },
                )
            }

            DarkenTheme {
                MainScreen(
                    dimLevel = dimLevel,
                    defaultDimLevel = defaultDim,
                    hasSavedDefault = hasSavedDefault,
                    overlayActive = overlayOn,
                    canDrawOverlays = canDrawOverlays(),
                    batteryUnrestricted = batteryUnrestricted(),
                    onDimLevelChange = { newLevel ->
                        dimLevelState.intValue = newLevel
                        if (overlayOn) {
                            OverlayService.update(this, newLevel)
                        }
                    },
                    onToggleOverlay = { enable ->
                        if (!enable) {
                            overlayDisabledByUser = true
                            OverlayLauncher.stop(this)
                        } else {
                            overlayDisabledByUser = false
                            startOverlay(dimLevelState.intValue) { snackbarMessage = it }
                        }
                    },
                    onSaveDefault = {
                        lifecycleScope.launch {
                            preferences.setDefaultDimLevel(dimLevel)
                            snackbarMessage = getString(R.string.default_saved, dimLevel)
                        }
                    },
                    onOpenPermissions = { showPermissionsDialog.value = true },
                    onOpenSettings = { showSettingsDialog.value = true },
                    onExitApp = { exitAppFully() },
                    colorFilterExpanded = colorExpanded,
                    onColorFilterExpandedChange = { expanded ->
                        lifecycleScope.launch { preferences.setColorFilterExpanded(expanded) }
                    },
                    tintPreset = tintPreset,
                    resolvedTintArgb = resolvedTintArgb,
                    savedTintPresets = savedTintPresets,
                    onTintPresetSelected = { preset ->
                        lifecycleScope.launch {
                            preferences.setOverlayTintPreset(preset)
                        }
                    },
                    onCustomTintClick = {
                        if (!canDrawOverlays()) {
                            showPermissionsDialog.value = true
                        } else {
                            lifecycleScope.launch {
                                val intensity = preferences.colorPreviewIntensity.first()
                                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                    previewInitialArgb = resolvedTintArgb
                                    previewInitialIntensity = intensity
                                    if (colorPreview.show(resolvedTintArgb, 0)) {
                                        showCustomColorDialog.value = true
                                    }
                                }
                            }
                        }
                    },
                    snackbarMessage = snackbarMessage,
                    onSnackbarShown = { snackbarMessage = null },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_OVERLAY_DISABLED_BY_USER, overlayDisabledByUser)
        outState.putBoolean(KEY_SETTINGS_DIALOG, showSettingsDialog.value)
    }

    override fun onStart() {
        super.onStart()
        MainActivityHolder.register(this)
    }

    private fun closeColorPreview() {
        showCustomColorDialog.value = false
        colorPreview.close()
    }

    override fun onPause() {
        closeColorPreview()
        super.onPause()
    }

    override fun onDestroy() {
        closeColorPreview()
        super.onDestroy()
    }

    override fun onStop() {
        MainActivityHolder.unregister(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        val activeDimLevel = OverlayStateStore.state.value.dimLevel
        if (activeDimLevel != null) {
            dimLevelState.intValue = activeDimLevel
        }
        permissionRefreshKey.intValue++
        if (intent?.getBooleanExtra(EXTRA_OPEN_PERMISSIONS, false) == true) {
            showPermissionsDialog.value = true
            intent?.removeExtra(EXTRA_OPEN_PERMISSIONS)
        }
        maybeAutoStartOverlay()
    }

    fun exitAppFully() {
        OverlayLauncher.stop(this)
        finishAndRemoveTask()
    }

    private fun applyLanguage(language: String) {
        lifecycleScope.launch {
            val previousLanguage = preferences.appLanguage.first()
            if (previousLanguage == language) return@launch
            preferences.setAppLanguage(language)
            LocaleHelper.apply(language)
        }
    }

    private fun applyAutoStart(enabled: Boolean) {
        lifecycleScope.launch {
            preferences.setAutoStartOnLaunch(enabled)
        }
    }

    private fun applyNotificationMode(mode: String) {
        lifecycleScope.launch {
            val previousMode = preferences.notificationMode.first()
            if (previousMode == mode) return@launch
            preferences.setNotificationMode(mode)
        }
    }

    private fun maybeAutoStartOverlay() {
        if (overlayDisabledByUser) return
        lifecycleScope.launch {
            if (!preferences.autoStartOnLaunchEnabled()) return@launch
            if (overlayDisabledByUser || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
            if (!OverlayLauncher.canStart(this@MainActivity)) return@launch
            if (OverlayLauncher.isOverlayRunning()) return@launch
            val level = preferences.resolveDefaultDimLevel()
            if (overlayDisabledByUser || OverlayLauncher.isOverlayRunning() ||
                !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            ) return@launch
            dimLevelState.intValue = level
            startOverlay(level) { /* silent auto-start */ }
        }
    }

    private fun startOverlay(level: Int, onMessage: (String) -> Unit) {
        val clamped = level.coerceIn(PreferencesRepository.MIN_DIM, PreferencesRepository.MAX_DIM)
        dimLevelState.intValue = clamped

        if (!canDrawOverlays()) {
            showPermissionsDialog.value = true
            return
        }
        if (!batteryUnrestricted()) {
            onMessage(getString(R.string.permission_battery_body))
        }
        OverlayLauncher.start(this, clamped)
    }

    private fun openSystemSettings(open: () -> Boolean) {
        if (!open()) Toast.makeText(this, R.string.settings_open_failed, Toast.LENGTH_LONG).show()
    }

    private fun openGithub() {
        val intent = Intent(Intent.ACTION_VIEW, getString(R.string.github_url).toUri())
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openContactEmail() {
        val intent = Intent(
            Intent.ACTION_SENDTO,
            "mailto:${getString(R.string.contact_email)}".toUri(),
        )
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun canDrawOverlays(): Boolean = PermissionIntents.canDrawOverlays(this)

    private fun batteryUnrestricted(): Boolean =
        PermissionIntents.isIgnoringBatteryOptimizations(this)

    companion object {
        const val EXTRA_OPEN_PERMISSIONS = "cz.darken.app.extra.OPEN_PERMISSIONS"

        private const val KEY_OVERLAY_DISABLED_BY_USER = "overlay_disabled_by_user"
        private const val KEY_SETTINGS_DIALOG = "settings_dialog"
    }
}

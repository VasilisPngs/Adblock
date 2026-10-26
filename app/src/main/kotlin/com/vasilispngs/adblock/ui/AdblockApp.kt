package com.vasilispngs.adblock.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.VpnService
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import androidx.paging.compose.collectAsLazyPagingItems
import com.vasilispngs.adblock.R
import com.vasilispngs.adblock.data.Appearance
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

private val TvOverscanHorizontal = 48.dp
private val TvOverscanVertical = 27.dp
private const val FADE_OUT_MILLIS = 90
private const val FADE_IN_MILLIS = 210
private const val FADE_THROUGH_ENTER_SCALE = 0.8f
private const val FADE_THROUGH_EXIT_SCALE = 1.05f
private const val PREDICTIVE_BACK_EXIT_SCALE = 0.9f
private const val PREDICTIVE_BACK_ENTER_SCALE = 1.1f
private const val PREDICTIVE_BACK_FADE_THRESHOLD = 0.35f
private val PredictiveBackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
private val PredictiveBackExitFade = Easing { (PredictiveBackEasing.transform(it) / PREDICTIVE_BACK_FADE_THRESHOLD).coerceAtMost(1f) }
private val PredictiveBackEnterFade = Easing {
    ((PredictiveBackEasing.transform(it) - PREDICTIVE_BACK_FADE_THRESHOLD) / (1f - PREDICTIVE_BACK_FADE_THRESHOLD)).coerceAtLeast(0f)
}

@Serializable
internal sealed interface Destination : NavKey {
    @Serializable
    data object Home : Destination

    @Serializable
    data object Protection : Destination

    @Serializable
    data object Settings : Destination
}

private val Destinations = listOf(Destination.Home, Destination.Protection, Destination.Settings)

@get:StringRes
private val Destination.labelRes: Int
    get() = when (this) {
        Destination.Home -> R.string.home
        Destination.Protection -> R.string.protection
        Destination.Settings -> R.string.settings
    }

@DrawableRes
private fun Destination.icon(selected: Boolean): Int = when (this) {
    Destination.Home -> if (selected) R.drawable.ic_home_filled else R.drawable.ic_home
    Destination.Protection -> if (selected) R.drawable.ic_shield_filled else R.drawable.ic_shield
    Destination.Settings -> if (selected) R.drawable.ic_settings_filled else R.drawable.ic_settings
}

@Composable
fun AdblockApp(
    homeViewModel: HomeViewModel,
    protectionViewModel: ProtectionViewModel,
    settingsViewModel: SettingsViewModel,
    appearance: Appearance,
    modifier: Modifier = Modifier
) {
    val home by homeViewModel.uiState.collectAsStateWithLifecycle()
    val log = homeViewModel.log.collectAsLazyPagingItems()
    val protection by protectionViewModel.uiState.collectAsStateWithLifecycle()
    val logEnabled by settingsViewModel.logEnabled.collectAsStateWithLifecycle()
    val selfUpdate by settingsViewModel.selfUpdate.collectAsStateWithLifecycle()
    val updateConfirmation by settingsViewModel.updateConfirmation.collectAsStateWithLifecycle()
    ReportDrawnWhen { home.loaded }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val activity = LocalActivity.current
    val connect = {
        homeViewModel.connect()
        requestUnrestrictedBattery(context)
    }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) connect() else homeViewModel.cancel()
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        homeViewModel.turnOn()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { homeViewModel.refresh() }

    if (home.readyToConnect) {
        SideEffect(Unit) {
            homeViewModel.connectRequestHandled()
            VpnService.prepare(context)?.let(consent::launch) ?: connect()
        }
    }

    updateConfirmation?.let { confirmation ->
        SideEffect(confirmation) {
            settingsViewModel.updateConfirmationShown(confirmation)
            val _ = runCatching { context.startActivity(confirmation) }
        }
    }

    protection.message?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message.text(resources))
            protectionViewModel.messageShown()
        }
    }

    AdblockNavigation(
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        home = { snackbar ->
            HomeScreen(
                uiState = home,
                log = log,
                snackbarHostState = snackbar,
                onEnabledChange = { enabled ->
                    when {
                        !enabled -> homeViewModel.turnOff()
                        askForNotifications(context, activity) -> notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else -> homeViewModel.turnOn()
                    }
                },
                onPrivateDns = { openSettings(context, Settings.ACTION_WIRELESS_SETTINGS) },
                onVpnSettings = { openSettings(context, Settings.ACTION_VPN_SETTINGS) },
                onFilterChange = homeViewModel::setFilter,
                onSearchChange = homeViewModel::setSearch,
                onRule = homeViewModel::setRule,
                onRemoveRule = homeViewModel::removeRule
            )
        },
        protection = { snackbar ->
            ProtectionScreen(
                uiState = protection,
                snackbarHostState = snackbar,
                onAddSource = protectionViewModel::addSource,
                onRemoveSource = protectionViewModel::removeSource,
                onAddRule = protectionViewModel::addRule,
                onRemoveRule = protectionViewModel::removeRule,
                onResolverChange = protectionViewModel::setResolver
            )
        },
        settings = { snackbar ->
            SettingsScreen(
                appearance = appearance,
                logEnabled = logEnabled,
                selfUpdate = selfUpdate,
                snackbarHostState = snackbar,
                onThemeChange = settingsViewModel::setThemeMode,
                onDynamicColorChange = settingsViewModel::setDynamicColor,
                onLogEnabledChange = settingsViewModel::setLogEnabled,
                onUpdateApp = settingsViewModel::updateApp
            )
        }
    )
}

@Composable
internal fun AdblockNavigation(
    snackbarHostState: SnackbarHostState,
    home: @Composable (SnackbarHostState?) -> Unit,
    protection: @Composable (SnackbarHostState?) -> Unit,
    settings: @Composable (SnackbarHostState?) -> Unit,
    modifier: Modifier = Modifier
) {
    val backStack = rememberSerializable(serializer = serializer()) { NavBackStack<Destination>(Destination.Home) }
    val current = backStack.last()
    var backNavigation by remember { mutableStateOf(false) }
    val select: (Destination) -> Unit = { destination ->
        if (destination != current) {
            backNavigation = false
            backStack.retainAll { it == Destination.Home }
            if (destination != Destination.Home) backStack.add(destination)
        }
    }
    val navigationSuiteType = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
    val bottomBar = navigationSuiteType == NavigationSuiteType.ShortNavigationBarCompact ||
        navigationSuiteType == NavigationSuiteType.ShortNavigationBarMedium
    val window = LocalActivity.current?.window
    val navigationBarScrim = !bottomBar && WindowInsets.navigationBars.getBottom(LocalDensity.current) > 0
    SideEffect(window, navigationBarScrim) { window?.isNavigationBarContrastEnforced = navigationBarScrim }

    val context = LocalContext.current
    val television = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }

    NavigationSuiteScaffold(
        navigationItems = {
            Destinations.forEach { destination ->
                val selected = destination == current
                NavigationSuiteItem(
                    selected = selected,
                    onClick = { select(destination) },
                    icon = { Icon(painter = painterResource(destination.icon(selected)), contentDescription = null) },
                    label = { Text(text = stringResource(destination.labelRes)) },
                    navigationSuiteType = navigationSuiteType
                )
            }
        },
        modifier = if (television) modifier.padding(horizontal = TvOverscanHorizontal, vertical = TvOverscanVertical) else modifier,
        navigationSuiteType = navigationSuiteType
    ) {
        val motion = MaterialTheme.motionScheme
        NavDisplay(
            backStack = backStack,
            onBack = {
                backNavigation = true
                backStack.removeLastOrNull()
            },
            transitionSpec = { fadeThrough() },
            popTransitionSpec = { if (backNavigation) backTransition(motion) else fadeThrough() },
            predictivePopTransitionSpec = { predictiveBackTransition() }
        ) { destination ->
            NavEntry(destination) { shown ->
                val snackbar = snackbarHostState.takeIf { shown == current }
                when (shown) {
                    Destination.Home -> home(snackbar)
                    Destination.Protection -> protection(snackbar)
                    Destination.Settings -> settings(snackbar)
                }
            }
        }
    }
}

private fun fadeThrough(): ContentTransform =
    (fadeIn(tween(durationMillis = FADE_IN_MILLIS, delayMillis = FADE_OUT_MILLIS, easing = LinearEasing)) +
        scaleIn(initialScale = FADE_THROUGH_ENTER_SCALE))
        .togetherWith(fadeOut(tween(durationMillis = FADE_OUT_MILLIS, easing = LinearEasing)) + scaleOut(targetScale = FADE_THROUGH_EXIT_SCALE))

private fun backTransition(motion: MotionScheme): ContentTransform =
    (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), PREDICTIVE_BACK_ENTER_SCALE))
        .togetherWith(fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.defaultSpatialSpec(), PREDICTIVE_BACK_EXIT_SCALE))

private fun predictiveBackTransition(): ContentTransform =
    (fadeIn(tween(easing = PredictiveBackEnterFade)) + scaleIn(tween(easing = PredictiveBackEasing), PREDICTIVE_BACK_ENTER_SCALE))
        .togetherWith(fadeOut(tween(easing = PredictiveBackExitFade)) + scaleOut(tween(easing = PredictiveBackEasing), PREDICTIVE_BACK_EXIT_SCALE))

internal fun ProtectionMessage.text(resources: Resources): String = resources.getString(
    when (this) {
        ProtectionMessage.Saved -> R.string.resolver_saved
        ProtectionMessage.ResolverUnreachable -> R.string.resolver_unreachable
    }
)

private fun askForNotifications(context: Context, activity: Activity?): Boolean =
    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
        activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == false

@SuppressLint("BatteryLife")
private fun requestUnrestrictedBattery(context: Context) {
    if (context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)) return
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}

private fun openSettings(context: Context, action: String) {
    try {
        context.startActivity(Intent(action))
    } catch (_: ActivityNotFoundException) {
    }
}

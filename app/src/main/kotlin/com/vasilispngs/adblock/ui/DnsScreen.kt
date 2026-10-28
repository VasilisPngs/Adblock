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
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.isCrossWindowBlurEnabled
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.vasilispngs.adblock.R
import com.vasilispngs.adblock.data.SelfUpdate
import com.vasilispngs.adblock.data.ThemeMode
import com.vasilispngs.adblock.vpn.DohResult
import com.vasilispngs.adblock.vpn.TunnelState
import com.vasilispngs.adblock.vpn.dohUrl
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.math.roundToInt
import kotlin.math.sign

private val CompactMargin = 16.dp
private val ContentMargin = 16.dp
private val WideMargin = 24.dp
private val MaxListWidth = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp
private val SmallSpacing = 8.dp
private val HeaderSpacing = 20.dp
private val OptionHeight = 56.dp
private val TvOverscanHorizontal = 48.dp
private val TvOverscanVertical = 27.dp
private val ForwardSlide = 96.dp
private const val PREDICTIVE_BACK_EXIT_SCALE = 0.9f
private const val PREDICTIVE_BACK_ENTER_SCALE = 1.1f
private const val PREDICTIVE_BACK_FADE_THRESHOLD = 0.35f
private val PredictiveBackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
private val PredictiveBackExitFade = Easing { (PredictiveBackEasing.transform(it) / PREDICTIVE_BACK_FADE_THRESHOLD).coerceAtMost(1f) }
private val PredictiveBackEnterFade = Easing {
    ((PredictiveBackEasing.transform(it) - PREDICTIVE_BACK_FADE_THRESHOLD) / (1f - PREDICTIVE_BACK_FADE_THRESHOLD)).coerceAtLeast(0f)
}
private val DialogBlurRadius = 10.dp
private const val BLURRED_SCRIM_ALPHA = 0.2f
private const val SCRIM_ALPHA = 0.32f

@Serializable
private sealed interface Screen : NavKey {
    @Serializable
    data object Home : Screen

    @Serializable
    data object Settings : Screen
}

@get:StringRes
private val Screen.titleRes: Int
    get() = when (this) {
        Screen.Home -> R.string.home
        Screen.Settings -> R.string.settings
    }

@Composable
fun DnsScreen(
    viewModel: DnsViewModel,
    themeMode: ThemeMode,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selfUpdate by viewModel.selfUpdate.collectAsStateWithLifecycle()
    val updateConfirmation by viewModel.updateConfirmation.collectAsStateWithLifecycle()
    ReportDrawnWhen { uiState.loaded }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val connect = {
        viewModel.connect()
        requestUnrestrictedBattery(context)
    }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) connect() else viewModel.cancel()
    }
    val activity = LocalActivity.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.turnOn()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    if (uiState.readyToConnect) {
        SideEffect(Unit) {
            viewModel.connectRequestHandled()
            VpnService.prepare(context)?.let(consent::launch) ?: connect()
        }
    }

    updateConfirmation?.let { confirmation ->
        SideEffect(confirmation) {
            viewModel.updateConfirmationShown(confirmation)
            val _ = runCatching { context.startActivity(confirmation) }
        }
    }

    uiState.failure?.let { failure ->
        LaunchedEffect(failure) {
            snackbarHostState.showSnackbar(failure.text(resources))
            viewModel.failureShown()
        }
    }

    if (uiState.loaded) {
        DnsScreen(
            uiState = uiState,
            snackbarHostState = snackbarHostState,
            onEnabledChange = { enabled ->
                when {
                    !enabled -> viewModel.turnOff()
                    askForNotifications(context, activity) -> notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> viewModel.turnOn()
                }
            },
            onUrlChange = viewModel::setUrl,
            onPrivateDns = { openSettings(context, Settings.ACTION_WIRELESS_SETTINGS) },
            onVpnSettings = { openSettings(context, Settings.ACTION_VPN_SETTINGS) },
            themeMode = themeMode,
            onThemeChange = viewModel::setThemeMode,
            selfUpdate = selfUpdate,
            onUpdateApp = viewModel::updateApp,
            modifier = modifier
        )
    }
}

@Composable
internal fun DnsScreen(
    uiState: DnsUiState,
    snackbarHostState: SnackbarHostState,
    onEnabledChange: (Boolean) -> Unit,
    onUrlChange: (String) -> Unit,
    onPrivateDns: () -> Unit,
    onVpnSettings: () -> Unit,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    selfUpdate: SelfUpdate,
    onUpdateApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backStack = rememberSerializable(serializer = serializer()) { NavBackStack<Screen>(Screen.Home) }
    val current = backStack.last()
    var editing by rememberSaveable { mutableStateOf(false) }
    var themeDialog by rememberSaveable { mutableStateOf(false) }
    val homeListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()
    val homeBarState = rememberTopAppBarState()
    val settingsBarState = rememberTopAppBarState()
    val homeFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val inputModeManager = LocalInputModeManager.current

    LaunchedEffect(Unit) {
        if (inputModeManager.inputMode == InputMode.Keyboard) homeFocus.requestFocus()
    }

    val openSettings: () -> Unit = {
        if (current != Screen.Settings) backStack.add(Screen.Settings)
    }
    val goBack: () -> Unit = { backStack.removeLastOrNull() }
    val windowInfo = currentWindowAdaptiveInfoV2()
    val margin = if (windowInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
        WideMargin
    } else {
        CompactMargin
    }
    val window = LocalActivity.current?.window
    val navigationBarScrim = WindowInsets.navigationBars.getBottom(LocalDensity.current) > 0
    SideEffect(window, navigationBarScrim) { window?.isNavigationBarContrastEnforced = navigationBarScrim }

    val context = LocalContext.current
    val television = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
    val overscan = if (television) {
        WindowInsets(TvOverscanHorizontal, TvOverscanVertical, TvOverscanHorizontal, TvOverscanVertical)
    } else {
        WindowInsets(0)
    }

    val motion = MaterialTheme.motionScheme
    val slide = with(LocalDensity.current) { ForwardSlide.roundToPx() }
    val content: @Composable (Screen) -> Unit = { screen ->
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(if (screen == Screen.Home) homeBarState else settingsBarState)
        val contentInsets = ScaffoldDefaults.contentWindowInsets.add(overscan.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
        var consumedInsets by remember { mutableStateOf(WindowInsets(0)) }
        BoxWithConstraints(modifier = Modifier.onConsumedWindowInsetsChanged { consumedInsets = it }) {
            val layoutDirection = LocalLayoutDirection.current
            val insets = contentInsets.exclude(consumedInsets).asPaddingValues()
            val safeWidth = maxWidth - insets.calculateStartPadding(layoutDirection) - insets.calculateEndPadding(layoutDirection)
            val sideMargin = ((safeWidth - MaxListWidth) / 2).coerceAtLeast(margin)
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(text = stringResource(screen.titleRes)) },
                        navigationIcon = {
                            if (screen == Screen.Settings) {
                                BarAction(
                                    icon = R.drawable.ic_arrow_back,
                                    label = stringResource(R.string.back),
                                    onClick = goBack,
                                    modifier = Modifier.focusProperties { down = settingsFocus }
                                )
                            }
                        },
                        actions = {
                            if (screen == Screen.Home) {
                                BarAction(
                                    icon = R.drawable.ic_settings,
                                    label = stringResource(R.string.settings),
                                    onClick = openSettings,
                                    modifier = Modifier.focusProperties { down = homeFocus }
                                )
                            }
                        },
                        windowInsets = TopAppBarDefaults.windowInsets.add(overscan.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                        contentPadding = PaddingValues(horizontal = sideMargin - CompactMargin),
                        scrollBehavior = scrollBehavior
                    )
                },
                snackbarHost = { if (screen == current) SnackbarHost(hostState = snackbarHostState) },
                contentWindowInsets = contentInsets
            ) { padding ->
                val contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(layoutDirection) + sideMargin,
                    top = padding.calculateTopPadding(),
                    end = padding.calculateEndPadding(layoutDirection) + sideMargin,
                    bottom = padding.calculateBottomPadding() + margin
                )
                when (screen) {
                    Screen.Home -> HomeView(
                        listState = homeListState,
                        nestedScroll = scrollBehavior.nestedScrollConnection,
                        focus = homeFocus,
                        contentPadding = contentPadding,
                        uiState = uiState,
                        onEnabledChange = { enabled -> if (enabled && uiState.url.isEmpty()) editing = true else onEnabledChange(enabled) },
                        onUrl = { editing = true },
                        onPrivateDns = onPrivateDns,
                        onVpnSettings = onVpnSettings
                    )
                    Screen.Settings -> SettingsView(
                        listState = settingsListState,
                        nestedScroll = scrollBehavior.nestedScrollConnection,
                        focus = settingsFocus,
                        contentPadding = contentPadding,
                        themeMode = themeMode,
                        onTheme = { themeDialog = true },
                        selfUpdate = selfUpdate,
                        onUpdateApp = onUpdateApp
                    )
                }
            }
        }
    }
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = goBack,
        transitionSpec = { forwardTransition(motion, slide) },
        popTransitionSpec = { backTransition(motion) },
        predictivePopTransitionSpec = { predictiveBackTransition() }
    ) { screen -> NavEntry(screen, content = content) }

    if (themeDialog) {
        ThemeDialog(
            current = themeMode,
            onDismiss = { themeDialog = false },
            onSelect = { mode ->
                themeDialog = false
                onThemeChange(mode)
            }
        )
    }

    if (editing) {
        UrlDialog(
            current = uiState.url,
            onDismiss = { editing = false },
            onSave = { url ->
                editing = false
                onUrlChange(url)
            }
        )
    }
}

@Composable
private fun HomeView(
    listState: LazyListState,
    nestedScroll: NestedScrollConnection,
    focus: FocusRequester,
    contentPadding: PaddingValues,
    uiState: DnsUiState,
    onEnabledChange: (Boolean) -> Unit,
    onUrl: () -> Unit,
    onPrivateDns: () -> Unit,
    onVpnSettings: () -> Unit
) {
    val on = uiState.tunnel != TunnelState.Off
    val alwaysOnShown = uiState.vpnSettings && uiState.tunnel == TunnelState.On
    val lockdown = uiState.tunnel == TunnelState.Lockdown
    val serverRows = if (uiState.serverFailing) 1 else 0
    val count = 2 + serverRows + (if (alwaysOnShown || lockdown) 1 else 0) + (if (uiState.privateDnsServer != null) 1 else 0)
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScroll)
            .focusRequester(focus)
            .focusRestorer(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
    ) {
        item(key = "dns") {
            val supporting = @Composable { Text(text = stringResource(uiState.tunnel.labelRes)) }
            val title = @Composable { Text(text = stringResource(R.string.dns_over_https)) }
            if (uiState.tunnel == TunnelState.AlwaysOn || lockdown) {
                SegmentedListItem(
                    checked = true,
                    onCheckedChange = { onVpnSettings() },
                    modifier = Modifier.semantics { role = Role.Switch },
                    shapes = ListItemDefaults.segmentedShapes(0, count),
                    colors = listItemColors(),
                    supportingContent = supporting,
                    trailingContent = { Switch(checked = true, onCheckedChange = null, enabled = false) },
                    content = title
                )
            } else {
                SegmentedListItem(
                    checked = on,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.semantics { role = Role.Switch },
                    shapes = ListItemDefaults.segmentedShapes(0, count),
                    colors = listItemColors(),
                    supportingContent = supporting,
                    trailingContent = { Switch(checked = on, onCheckedChange = null) },
                    content = title
                )
            }
        }

        item(key = "url") {
            SegmentedListItem(
                onClick = onUrl,
                enabled = uiState.tunnel != TunnelState.Connecting,
                shapes = ListItemDefaults.segmentedShapes(1, count),
                colors = listItemColors(),
                supportingContent = { Text(text = uiState.url.ifEmpty { stringResource(R.string.server_url_not_set) }) }
            ) {
                Text(text = stringResource(R.string.server_url))
            }
        }

        if (uiState.serverFailing) {
            item(key = "server-failing") {
                SegmentedListItem(
                    onClick = onUrl,
                    shapes = ListItemDefaults.segmentedShapes(2, count),
                    colors = listItemColors(),
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_warning),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    supportingContent = { Text(text = stringResource(R.string.server_not_answering_description)) }
                ) {
                    Text(text = stringResource(R.string.server_not_answering))
                }
            }
        }

        if (alwaysOnShown) {
            item(key = "always-on") {
                SegmentedListItem(
                    onClick = onVpnSettings,
                    shapes = ListItemDefaults.segmentedShapes(2 + serverRows, count),
                    colors = listItemColors(),
                    supportingContent = { Text(text = stringResource(R.string.always_on_vpn_description)) }
                ) {
                    Text(text = stringResource(R.string.always_on_vpn))
                }
            }
        }

        if (lockdown) {
            item(key = "lockdown") {
                SegmentedListItem(
                    onClick = onVpnSettings,
                    shapes = ListItemDefaults.segmentedShapes(2 + serverRows, count),
                    colors = listItemColors(),
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_warning),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    supportingContent = { Text(text = stringResource(R.string.lockdown_on_description)) }
                ) {
                    Text(text = stringResource(R.string.lockdown_on))
                }
            }
        }

        uiState.privateDnsServer?.let { server ->
            item(key = "private-dns") {
                SegmentedListItem(
                    onClick = onPrivateDns,
                    shapes = ListItemDefaults.segmentedShapes(count - 1, count),
                    colors = listItemColors(),
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_warning),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    supportingContent = { Text(text = stringResource(R.string.private_dns_on_description)) }
                ) {
                    Text(text = stringResource(R.string.private_dns_on, server))
                }
            }
        }
    }
}

@Composable
private fun SettingsView(
    listState: LazyListState,
    nestedScroll: NestedScrollConnection,
    focus: FocusRequester,
    contentPadding: PaddingValues,
    themeMode: ThemeMode,
    onTheme: () -> Unit,
    selfUpdate: SelfUpdate,
    onUpdateApp: () -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScroll)
            .focusRequester(focus)
            .focusRestorer(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
    ) {
        if (selfUpdate != SelfUpdate.Idle) {
            item(key = "app-update") {
                AppUpdateItem(status = selfUpdate, onUpdate = onUpdateApp)
            }
        }

        item(key = "appearance") {
            SectionHeader(text = stringResource(R.string.appearance))
        }

        item(key = "theme") {
            SegmentedListItem(
                onClick = onTheme,
                shapes = ListItemDefaults.segmentedShapes(0, 1),
                colors = listItemColors(),
                supportingContent = { Text(text = stringResource(themeMode.labelRes)) }
            ) {
                Text(text = stringResource(R.string.theme))
            }
        }
    }
}

@Composable
private fun AppUpdateItem(status: SelfUpdate, onUpdate: () -> Unit, modifier: Modifier = Modifier) {
    val shapes = ListItemDefaults.segmentedShapes(0, 1)
    val headline = @Composable { Text(text = stringResource(R.string.self_update_available)) }
    val supporting = @Composable {
        when (status) {
            is SelfUpdate.Downloading -> Column {
                Text(text = stringResource(R.string.self_update_downloading, (status.progress * 100).roundToInt()))
                LinearWavyProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
            }
            SelfUpdate.Installing -> Column {
                Text(text = stringResource(R.string.self_update_installing))
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            SelfUpdate.Failed -> Text(text = stringResource(R.string.self_update_failed))
            else -> Text(text = stringResource(R.string.self_update_install))
        }
    }
    if (status == SelfUpdate.Available || status == SelfUpdate.Failed) {
        SegmentedListItem(
            onClick = onUpdate,
            shapes = shapes,
            colors = listItemColors(),
            modifier = modifier,
            supportingContent = supporting,
            content = headline
        )
    } else {
        SegmentedListItem(
            shapes = shapes,
            colors = listItemColors(),
            modifier = modifier,
            supportingContent = supporting,
            content = headline
        )
    }
}

@Composable
private fun UrlDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val state = rememberTextFieldState(current)
    val url = dohUrl(state.text.toString())
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { url?.let(onSave) }, enabled = url != null, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        title = { Text(text = stringResource(R.string.server_url)) },
        text = {
            val invalid = state.text.isNotBlank() && url == null
            OutlinedTextField(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
                shape = OutlinedTextFieldDefaults.roundedShape,
                colors = OutlinedTextFieldDefaults.tonalColors(),
                labelPosition = TextFieldLabelPosition.Inside(),
                label = { Text(text = stringResource(R.string.server_url_label)) },
                placeholder = { Text(text = stringResource(R.string.server_url_placeholder)) },
                supportingText = if (invalid) {
                    { Text(text = stringResource(R.string.server_url_invalid)) }
                } else {
                    null
                },
                isError = invalid,
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                onKeyboardAction = { url?.let(onSave) },
                lineLimits = TextFieldLineLimits.SingleLine
            )
        },
        properties = dialogProperties()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarAction(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(text = label) } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes(), modifier = modifier) {
            Icon(painter = painterResource(icon), contentDescription = label)
        }
    }
}
@Composable
private fun ThemeDialog(
    current: ThemeMode,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit
) {
    val inputModeManager = LocalInputModeManager.current
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (inputModeManager.inputMode == InputMode.Keyboard) focus.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        title = { Text(text = stringResource(R.string.theme)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    val selected = mode == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (selected) Modifier.focusRequester(focus) else Modifier)
                            .height(OptionHeight)
                            .selectable(selected = selected, onClick = { onSelect(mode) }, role = Role.RadioButton),
                        horizontalArrangement = Arrangement.spacedBy(ContentMargin),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(text = stringResource(mode.labelRes), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        properties = dialogProperties()
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = ContentMargin, top = HeaderSpacing, end = ContentMargin, bottom = SmallSpacing)
            .semantics { heading() }
    )
}

@Composable
private fun dialogProperties() = DialogProperties(
    blurBehindRadius = DialogBlurRadius,
    scrimAlpha = if (LocalWindowInfo.current.isCrossWindowBlurEnabled) BLURRED_SCRIM_ALPHA else SCRIM_ALPHA
)

private fun AnimatedContentTransitionScope<*>.forwardTransition(motion: MotionScheme, distance: Int) =
    (slideIntoContainer(SlideDirection.Start, motion.defaultSpatialSpec()) { it.sign * distance } + fadeIn(motion.fastEffectsSpec()))
        .togetherWith(slideOutOfContainer(SlideDirection.Start, motion.defaultSpatialSpec()) { it.sign * distance })

private fun backTransition(motion: MotionScheme) =
    (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), PREDICTIVE_BACK_ENTER_SCALE))
        .togetherWith(fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.defaultSpatialSpec(), PREDICTIVE_BACK_EXIT_SCALE))

private fun predictiveBackTransition() =
    (fadeIn(tween(easing = PredictiveBackEnterFade)) + scaleIn(tween(easing = PredictiveBackEasing), PREDICTIVE_BACK_ENTER_SCALE))
        .togetherWith(fadeOut(tween(easing = PredictiveBackExitFade)) + scaleOut(tween(easing = PredictiveBackEasing), PREDICTIVE_BACK_EXIT_SCALE))

@Composable
private fun listItemColors() = MaterialTheme.colorScheme.surfaceContainer.let { container ->
    ListItemDefaults.segmentedColors(containerColor = container, disabledContainerColor = container)
}

@get:StringRes
private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.System -> R.string.theme_system
        ThemeMode.Light -> R.string.theme_light
        ThemeMode.Dark -> R.string.theme_dark
    }

private val TunnelState.labelRes: Int
    get() = when (this) {
        TunnelState.Off -> R.string.tunnel_off
        TunnelState.Connecting -> R.string.tunnel_connecting
        TunnelState.On -> R.string.tunnel_on
        TunnelState.AlwaysOn, TunnelState.Lockdown -> R.string.tunnel_always_on
    }

internal fun DohResult.text(resources: Resources): String =
    if (this is DohResult.Rejected) {
        resources.getString(R.string.server_rejected, status)
    } else {
        resources.getString(R.string.server_unreachable)
    }

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

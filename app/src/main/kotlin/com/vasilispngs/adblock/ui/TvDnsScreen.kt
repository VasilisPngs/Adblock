package com.vasilispngs.adblock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.ListItem
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.vasilispngs.adblock.R
import com.vasilispngs.adblock.data.SelfUpdate
import com.vasilispngs.adblock.data.ThemeMode
import com.vasilispngs.adblock.vpn.TunnelState
import com.vasilispngs.adblock.vpn.dohUrl
import com.vasilispngs.adblock.vpn.errorCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

private val TvOverscanHorizontal = 48.dp
private val TvOverscanVertical = 27.dp
private val TvGutter = 20.dp
private val TvLineSpacing = 4.dp
private val ListItemInset = 16.dp
private val TvListPadding = TvOverscanHorizontal - ListItemInset
private val TvContentPadding = PaddingValues(top = TvGutter, bottom = TvOverscanVertical)
private val ReadingWidth = 600.dp
private const val SIDEBAR_FRACTION = 0.4f
private const val TEXT_FIELD_FOCUSED_SCALE = 1.01f

@Serializable
private sealed interface TvDestination : NavKey {
    @Serializable
    data object Home : TvDestination

    @Serializable
    data object Settings : TvDestination

    @Serializable
    data object Server : TvDestination
}

private enum class SettingsSection { AppUpdate, Theme }

@Composable
internal fun TvDnsScreen(
    uiState: DnsUiState,
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
    val backStack = rememberSerializable(serializer = serializer()) { NavBackStack<TvDestination>(TvDestination.Home) }
    val open: (TvDestination) -> Unit = { destination -> if (backStack.last() != destination) backStack.add(destination) }
    val goBack: () -> Unit = { backStack.removeLastOrNull() }

    Surface(modifier = modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            onBack = goBack,
            entryProvider = entryProvider {
                entry<TvDestination.Home> {
                    TvHome(
                        uiState = uiState,
                        onEnabledChange = { enabled -> if (enabled && uiState.url.isEmpty()) open(TvDestination.Server) else onEnabledChange(enabled) },
                        onServer = { open(TvDestination.Server) },
                        onPrivateDns = onPrivateDns,
                        onVpnSettings = onVpnSettings,
                        onSettings = { open(TvDestination.Settings) }
                    )
                }
                entry<TvDestination.Settings> {
                    TvSettings(themeMode = themeMode, onThemeChange = onThemeChange, selfUpdate = selfUpdate, onUpdateApp = onUpdateApp)
                }
                entry<TvDestination.Server> {
                    TvServer(
                        current = uiState.url,
                        onSave = { url ->
                            goBack()
                            onUrlChange(url)
                        }
                    )
                }
            }
        )
    }
}

@Composable
private fun TvHome(
    uiState: DnsUiState,
    onEnabledChange: (Boolean) -> Unit,
    onServer: () -> Unit,
    onPrivateDns: () -> Unit,
    onVpnSettings: () -> Unit,
    onSettings: () -> Unit
) {
    val focus = rememberInitialFocus()
    val on = uiState.tunnel != TunnelState.Off
    val kept = uiState.tunnel == TunnelState.AlwaysOn || uiState.tunnel == TunnelState.Lockdown
    val resources = LocalResources.current

    TvScreen(
        title = stringResource(R.string.home),
        action = {
            IconButton(onClick = onSettings) {
                Icon(painter = painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .focusRestorer(),
            contentPadding = TvContentPadding,
            verticalArrangement = Arrangement.spacedBy(TvLineSpacing)
        ) {
            item(key = "dns") {
                TvListItem(
                    headline = stringResource(R.string.dns_over_https),
                    supporting = stringResource(uiState.tunnel.labelRes),
                    onClick = if (kept) onVpnSettings else { { onEnabledChange(!on) } },
                    trailing = { Switch(checked = on, onCheckedChange = null, enabled = !kept) },
                    modifier = Modifier.semantics {
                        role = Role.Switch
                        toggleableState = ToggleableState(on)
                    }
                )
            }
            item(key = "url") {
                TvListItem(
                    headline = stringResource(R.string.server_url),
                    supporting = uiState.url.ifEmpty { stringResource(R.string.server_url_not_set) },
                    onClick = onServer,
                    enabled = uiState.tunnel != TunnelState.Connecting
                )
            }
            uiState.serverFailure?.let { failure ->
                item(key = "server-failing") {
                    TvListItem(
                        headline = stringResource(R.string.server_not_answering).withCode(failure.errorCode(), resources),
                        supporting = stringResource(R.string.server_not_answering_description),
                        onClick = onServer,
                        warning = true
                    )
                }
            }
            if (uiState.vpnSettings && uiState.tunnel == TunnelState.On) {
                item(key = "always-on") {
                    TvListItem(
                        headline = stringResource(R.string.always_on_vpn),
                        supporting = stringResource(R.string.always_on_vpn_description),
                        onClick = onVpnSettings
                    )
                }
            }
            if (uiState.tunnel == TunnelState.Lockdown) {
                item(key = "lockdown") {
                    TvListItem(
                        headline = stringResource(R.string.lockdown_on),
                        supporting = stringResource(R.string.lockdown_on_description),
                        onClick = onVpnSettings,
                        warning = true
                    )
                }
            }
            uiState.privateDnsServer?.let { server ->
                item(key = "private-dns") {
                    TvListItem(
                        headline = stringResource(R.string.private_dns_on, server),
                        supporting = stringResource(R.string.private_dns_on_description),
                        onClick = onPrivateDns,
                        warning = true
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSettings(themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit, selfUpdate: SelfUpdate, onUpdateApp: () -> Unit) {
    val sections = remember(selfUpdate == SelfUpdate.Idle) {
        SettingsSection.entries.filter { it != SettingsSection.AppUpdate || selfUpdate != SelfUpdate.Idle }
    }
    var chosen by rememberSaveable { mutableStateOf(SettingsSection.Theme) }
    val section = chosen.takeIf { it in sections } ?: sections.first()
    val sidebar = rememberInitialFocus()
    var optionsFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = optionsFocused,
        onBackCompleted = { sidebar.requestFocus() }
    )

    TvScreen(title = stringResource(R.string.settings)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = TvListPadding),
            horizontalArrangement = Arrangement.spacedBy(TvGutter)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth(SIDEBAR_FRACTION)
                    .fillMaxHeight()
                    .focusRequester(sidebar)
                    .focusRestorer(),
                contentPadding = TvContentPadding,
                verticalArrangement = Arrangement.spacedBy(TvLineSpacing)
            ) {
                items(sections, key = { it.name }) { item ->
                    ListItem(
                        selected = item == section,
                        onClick = {
                            when (item) {
                                SettingsSection.AppUpdate -> if (selfUpdate == SelfUpdate.Available || selfUpdate is SelfUpdate.Failed) onUpdateApp()
                                SettingsSection.Theme -> focusManager.moveFocus(FocusDirection.Right)
                            }
                        },
                        headlineContent = {
                            Text(text = stringResource(if (item == SettingsSection.AppUpdate) R.string.self_update_available else R.string.theme))
                        },
                        supportingContent = {
                            Text(
                                text = if (item == SettingsSection.AppUpdate) selfUpdate.text() else stringResource(themeMode.labelRes),
                                style = LocalTextStyle.current.copy(fontFeatureSettings = TABULAR_FIGURES)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (it.isFocused) chosen = item }
                    )
                }
            }
            if (section == SettingsSection.Theme) {
                OptionList(
                    options = ThemeMode.entries,
                    current = themeMode,
                    onSelect = onThemeChange,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onFocusChanged { optionsFocused = it.hasFocus }
                ) { stringResource(it.labelRes) }
            }
        }
    }
}

@Composable
private fun TvServer(current: String, onSave: (String) -> Unit) {
    val state = rememberTextFieldState(current)
    val url = dohUrl(state.text.toString())
    val save: () -> Unit = { url?.let(onSave) }
    val field = rememberInitialFocus()

    TvScreen(title = stringResource(R.string.server_url)) {
        Column(
            modifier = Modifier
                .padding(horizontal = TvOverscanHorizontal)
                .padding(TvContentPadding)
                .widthIn(max = ReadingWidth),
            verticalArrangement = Arrangement.spacedBy(TvGutter)
        ) {
            TvTextField(
                state = state,
                placeholder = stringResource(R.string.server_url_placeholder),
                onDone = save,
                modifier = Modifier.focusRequester(field)
            )
            if (state.text.isNotBlank() && url == null) {
                Text(
                    text = stringResource(R.string.server_url_invalid),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(onClick = save, enabled = url != null) {
                Text(text = stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun TvTextField(state: TextFieldState, placeholder: String, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val container = remember { FocusRequester() }
    val text = remember { FocusRequester() }
    Surface(
        onClick = { text.requestFocus() },
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(container),
        scale = ClickableSurfaceDefaults.scale(focusedScale = TEXT_FIELD_FOCUSED_SCALE),
        colors = ClickableSurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        BasicTextField(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .padding(ListItemInset)
                .focusRequester(text)
                .onPreviewKeyEvent { event ->
                    val back = event.key == Key.Back || event.key == Key.Escape
                    if (back && event.type == KeyEventType.KeyUp) container.requestFocus()
                    back
                },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = LocalContentColor.current),
            cursorBrush = SolidColor(LocalContentColor.current),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            onKeyboardAction = { onDone() },
            lineLimits = TextFieldLineLimits.SingleLine,
            decorator = { field ->
                Box {
                    if (state.text.isEmpty()) {
                        Text(text = placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    field()
                }
            }
        )
    }
}

@Composable
private fun TvListItem(
    headline: String,
    supporting: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    warning: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    ListItem(
        selected = false,
        onClick = onClick,
        enabled = enabled,
        headlineContent = { Text(text = headline) },
        supportingContent = { Text(text = supporting) },
        leadingContent = if (warning) {
            { Icon(painter = painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error) }
        } else {
            null
        },
        trailingContent = trailing,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TvListPadding)
    )
}

@Composable
private fun <T> OptionList(
    options: List<T>,
    current: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (T) -> String
) {
    val currentFocus = remember { FocusRequester() }
    Column(
        modifier = modifier
            .focusRestorer(currentFocus)
            .verticalScroll(rememberScrollState())
            .padding(TvContentPadding),
        verticalArrangement = Arrangement.spacedBy(TvLineSpacing)
    ) {
        options.forEach { option ->
            val chosen = option == current
            ListItem(
                selected = chosen,
                onClick = { onSelect(option) },
                headlineContent = { Text(text = label(option)) },
                trailingContent = if (chosen) {
                    { Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null) }
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (chosen) Modifier.focusRequester(currentFocus) else Modifier)
            )
        }
    }
}

@Composable
private fun TvScreen(title: String, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = TvOverscanVertical)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TvOverscanHorizontal),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            action?.invoke()
        }
        content()
    }
}

@Composable
private fun rememberInitialFocus(): FocusRequester {
    val focus = remember { FocusRequester() }
    val inputModeManager = LocalInputModeManager.current
    LaunchedEffect(Unit) {
        if (inputModeManager.inputMode == InputMode.Keyboard) focus.requestFocus()
    }
    return focus
}

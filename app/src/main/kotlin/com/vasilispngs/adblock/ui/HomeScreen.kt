package com.vasilispngs.adblock.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExpandedDockedSearchBar
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.relocation.bringIntoView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import androidx.window.core.layout.WindowSizeClass
import com.vasilispngs.adblock.R
import com.vasilispngs.adblock.data.Action
import com.vasilispngs.adblock.data.LoggedQuery
import com.vasilispngs.adblock.data.Origin
import com.vasilispngs.adblock.vpn.TunnelState
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val LogBarSpacing = 12.dp
private const val LOG_BAR_KEY = "log-bar"

@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    log: LazyPagingItems<LoggedQuery>,
    snackbarHostState: SnackbarHostState?,
    onEnabledChange: (Boolean) -> Unit,
    onPrivateDns: () -> Unit,
    onVpnSettings: () -> Unit,
    onFilterChange: (LogFilter) -> Unit,
    onSearchChange: (String) -> Unit,
    onRule: (String, Action) -> Unit,
    onRemoveRule: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val search = rememberTextFieldState(uiState.search)
    val searchBarState = rememberSearchBarState()
    var searchKeyboard by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val time = rememberTimeFormatter()
    val focus = remember { FocusRequester() }
    val inputModeManager = LocalInputModeManager.current
    val searchChange by rememberUpdatedState(onSearchChange)
    LaunchedEffect(search) { snapshotFlow { search.text.toString() }.collect { searchChange(it) } }
    LaunchedEffect(searchBarState) {
        snapshotFlow { searchBarState.targetValue }.collect { if (it == SearchBarValue.Collapsed) searchKeyboard = false }
    }
    val searchField = @Composable {
        SearchField(
            search = search,
            searchBarState = searchBarState,
            keyboardRequested = searchKeyboard,
            onKeyboardRequestedChange = { searchKeyboard = it }
        )
    }
    LaunchedEffect(Unit) {
        if (inputModeManager.inputMode == InputMode.Keyboard) focus.requestFocus()
    }

    ScreenScaffold(title = R.string.home, snackbarHostState = snackbarHostState, modifier = modifier) { contentPadding, contentModifier ->
        val layoutDirection = LocalLayoutDirection.current
        val side = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            end = contentPadding.calculateEndPadding(layoutDirection)
        )
        val statusCount = 1 + (if (uiState.alwaysOnShown || uiState.tunnel == TunnelState.Lockdown) 1 else 0) +
            (if (uiState.privateDnsServer != null) 1 else 0)
        val barIndex = statusCount + 1
        val stuck by remember(barIndex) {
            derivedStateOf {
                listState.firstVisibleItemIndex > barIndex ||
                    (listState.firstVisibleItemIndex == barIndex && listState.firstVisibleItemScrollOffset > 0)
            }
        }
        val colors = TopAppBarDefaults.topAppBarColors()
        val barColor by animateColorAsState(
            targetValue = if (stuck) colors.scrolledContainerColor else colors.containerColor,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
        )
        LazyColumn(
            state = listState,
            modifier = contentModifier
                .fillMaxSize()
                .focusRequester(focus)
                .focusRestorer(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            statusItems(uiState, statusCount, side, onEnabledChange, onPrivateDns, onVpnSettings)
            item(key = "log-header") { SectionHeader(text = stringResource(R.string.query_log), modifier = Modifier.padding(side)) }
            stickyHeader(key = LOG_BAR_KEY) {
                LogBar(
                    searchBarState = searchBarState,
                    inputField = searchField,
                    filter = uiState.filter,
                    onFilterChange = onFilterChange,
                    modifier = Modifier
                        .bringWholeIntoView()
                        .drawBehind { drawRect(barColor) }
                        .padding(side)
                        .padding(top = SmallSpacing, bottom = ContentMargin)
                )
            }
            logItems(uiState, log, time, side, onRule, onRemoveRule)
        }
    }

    FocusedSearch(searchBarState = searchBarState, inputField = searchField) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = ContentMargin),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            logItems(uiState, log, time, PaddingValues(horizontal = ContentMargin), onRule, onRemoveRule)
        }
    }
}

private val HomeUiState.alwaysOnShown: Boolean
    get() = vpnSettings && tunnel == TunnelState.On

private fun LazyListScope.statusItems(
    uiState: HomeUiState,
    count: Int,
    side: PaddingValues,
    onEnabledChange: (Boolean) -> Unit,
    onPrivateDns: () -> Unit,
    onVpnSettings: () -> Unit
) {
    val lockdown = uiState.tunnel == TunnelState.Lockdown
    item(key = "protection") {
        val heldBySystem = uiState.tunnel == TunnelState.AlwaysOn || lockdown
        val on = uiState.tunnel != TunnelState.Off
        SegmentedListItem(
            checked = on,
            onCheckedChange = if (heldBySystem) { _ -> onVpnSettings() } else onEnabledChange,
            modifier = Modifier
                .padding(side)
                .semantics { role = Role.Switch },
            shapes = ListItemDefaults.segmentedShapes(0, count),
            colors = listItemColors(),
            supportingContent = { Text(text = stringResource(uiState.tunnel.labelRes)) },
            trailingContent = { Switch(checked = on, onCheckedChange = null, enabled = !heldBySystem) }
        ) {
            Text(text = stringResource(R.string.protection))
        }
    }

    if (uiState.alwaysOnShown) {
        item(key = "always-on") {
            SegmentedListItem(
                onClick = onVpnSettings,
                modifier = Modifier.padding(side),
                shapes = ListItemDefaults.segmentedShapes(1, count),
                colors = listItemColors(),
                supportingContent = { Text(text = stringResource(R.string.always_on_vpn_description)) }
            ) {
                Text(text = stringResource(R.string.always_on_vpn))
            }
        }
    }

    if (lockdown) {
        item(key = "lockdown") {
            WarningItem(
                title = stringResource(R.string.lockdown_on),
                text = stringResource(R.string.lockdown_on_description),
                index = 1,
                count = count,
                onClick = onVpnSettings,
                modifier = Modifier.padding(side)
            )
        }
    }

    uiState.privateDnsServer?.let { server ->
        item(key = "private-dns") {
            WarningItem(
                title = stringResource(R.string.private_dns_on, server),
                text = stringResource(R.string.private_dns_on_description),
                index = count - 1,
                count = count,
                onClick = onPrivateDns,
                modifier = Modifier.padding(side)
            )
        }
    }
}

@Composable
private fun WarningItem(title: String, text: String, index: Int, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SegmentedListItem(
        onClick = onClick,
        modifier = modifier,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = listItemColors(),
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        supportingContent = { Text(text = text) }
    ) {
        Text(text = title)
    }
}

private fun LazyListScope.logItems(
    uiState: HomeUiState,
    log: LazyPagingItems<LoggedQuery>,
    time: (Long) -> String,
    side: PaddingValues,
    onRule: (String, Action) -> Unit,
    onRemoveRule: (String) -> Unit
) {
    if (log.itemCount == 0 && log.loadState.refresh is LoadState.NotLoading) {
        item(key = "log-empty", contentType = "empty") {
            val modifier = Modifier.padding(side)
            when {
                uiState.filter != LogFilter.All || uiState.search.isNotBlank() ->
                    EmptyState(R.drawable.ic_search, stringResource(R.string.no_results), stringResource(R.string.no_results_description), modifier)
                uiState.logEnabled ->
                    EmptyState(R.drawable.ic_schedule, stringResource(R.string.no_queries), stringResource(R.string.no_queries_description), modifier)
                else ->
                    EmptyState(R.drawable.ic_pause, stringResource(R.string.log_off), stringResource(R.string.log_off_description), modifier)
            }
        }
    }
    items(count = log.itemCount, key = log.itemKey { it.id }, contentType = log.itemContentType { "query" }) { index ->
        log[index]?.let { entry ->
            LogRow(
                entry = entry,
                rule = uiState.rules[entry.name],
                index = index,
                count = log.itemCount,
                time = time,
                onRule = onRule,
                onRemoveRule = onRemoveRule,
                modifier = Modifier.padding(side)
            )
        }
    }
}

@Composable
private fun LogBar(
    searchBarState: SearchBarState,
    inputField: @Composable () -> Unit,
    filter: LogFilter,
    onFilterChange: (LogFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LogBarSpacing)) {
        SearchBar(state = searchBarState, inputField = inputField, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(SmallSpacing)) {
            LogFilter.entries.forEach { option ->
                val selected = option == filter
                FilterChip(
                    selected = selected,
                    onClick = { onFilterChange(option) },
                    label = { Text(text = stringResource(option.labelRes)) },
                    shapes = FilterChipDefaults.shapes(),
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    search: TextFieldState,
    searchBarState: SearchBarState,
    keyboardRequested: Boolean,
    onKeyboardRequestedChange: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val collapse: () -> Unit = { scope.launch { searchBarState.animateToCollapsed() } }
    val touch = LocalInputModeManager.current.inputMode == InputMode.Touch
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides DefaultBringIntoViewSpec) {
        SearchBarDefaults.InputField(
            textFieldState = search,
            searchBarState = searchBarState,
            onSearch = { collapse() },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    if (focused && !state.isFocused) onKeyboardRequestedChange(false)
                    focused = state.isFocused
                }
                .onPreviewKeyEvent { event ->
                    val pressed = event.type == KeyEventType.KeyDown
                    val collapsed = searchBarState.targetValue == SearchBarValue.Collapsed
                    when {
                        !focused -> false
                        event.key == Key.DirectionCenter -> {
                            if (pressed) {
                                onKeyboardRequestedChange(true)
                                if (collapsed) scope.launch { searchBarState.animateToExpanded() }
                            }
                            false
                        }
                        collapsed && (event.key == Key.DirectionDown || event.key == Key.NumPadDirectionDown) -> {
                            if (pressed) focusManager.moveFocus(FocusDirection.Down)
                            true
                        }
                        else -> false
                    }
                },
            keyboardOptions = KeyboardOptions(showKeyboardOnFocus = touch || keyboardRequested),
            placeholder = { Text(text = stringResource(R.string.search_domains), modifier = Modifier.clearAndSetSemantics {}) },
            leadingIcon = {
                if (searchBarState.currentValue == SearchBarValue.Expanded) {
                    IconAction(icon = R.drawable.ic_arrow_back, label = stringResource(R.string.back), onClick = collapse)
                } else {
                    Icon(painter = painterResource(R.drawable.ic_search), contentDescription = null)
                }
            },
            trailingIcon = if (search.text.isNotEmpty()) {
                { IconAction(icon = R.drawable.ic_close, label = stringResource(R.string.clear_search), onClick = search::clearText) }
            } else {
                null
            }
        )
    }
}

@Composable
private fun FocusedSearch(searchBarState: SearchBarState, inputField: @Composable () -> Unit, results: @Composable () -> Unit) {
    if (currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
        ExpandedDockedSearchBar(state = searchBarState, inputField = inputField) { results() }
    } else {
        ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) { results() }
    }
}

@Composable
private fun LogRow(
    entry: LoggedQuery,
    rule: Action?,
    index: Int,
    count: Int,
    time: (Long) -> String,
    onRule: (String, Action) -> Unit,
    onRemoveRule: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val blocked = entry.action == Action.Block
    val meta = buildList {
        add(time(entry.at))
        add(entry.type)
        add(stringResource(entry.origin.labelRes))
        entry.rule?.takeIf { it != entry.name }?.let { add(it) }
        rule?.let { add(stringResource(R.string.rule_meta, stringResource(it.labelRes))) }
    }.joinToString(" · ")
    SegmentedListItem(
        modifier = modifier,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = listItemColors(),
        leadingContent = {
            Icon(
                painter = painterResource(if (blocked) R.drawable.ic_block else R.drawable.ic_check_circle),
                contentDescription = null,
                tint = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        supportingContent = { Text(text = meta) },
        trailingContent = {
            when {
                rule != null -> LogAction(R.string.remove, entry.name, tonal = false) { onRemoveRule(entry.name) }
                blocked -> LogAction(R.string.allow, entry.name, tonal = true) { onRule(entry.name, Action.Allow) }
                else -> LogAction(R.string.block, entry.name, tonal = true) { onRule(entry.name, Action.Block) }
            }
        }
    ) {
        Text(text = entry.name)
    }
}

@Composable
private fun LogAction(@StringRes label: Int, host: String, tonal: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.action_on_host, stringResource(label), host)
    val modifier = Modifier.semantics { contentDescription = description }
    val content: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            listOf(R.string.remove, R.string.allow, R.string.block).forEach { option ->
                Text(
                    text = stringResource(option),
                    modifier = if (option == label) Modifier else Modifier.alpha(0f).clearAndSetSemantics {}
                )
            }
        }
    }
    if (tonal) {
        FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = modifier) { content() }
    } else {
        TextButton(onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = modifier) { content() }
    }
}

@Composable
private fun rememberTimeFormatter(): (Long) -> String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val twentyFour = DateFormat.is24HourFormat(context)
    return remember(locale, twentyFour) {
        val clock = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (twentyFour) "Hms" else "hms"), locale)
            .withZone(ZoneId.systemDefault())
        val formatter: (Long) -> String = { at ->
            val text = clock.format(Instant.ofEpochMilli(at))
            if (DateUtils.isToday(at)) {
                text
            } else {
                "${DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS)} $text"
            }
        }
        formatter
    }
}

@get:StringRes
private val TunnelState.labelRes: Int
    get() = when (this) {
        TunnelState.Off -> R.string.tunnel_off
        TunnelState.Connecting -> R.string.tunnel_connecting
        TunnelState.On -> R.string.tunnel_on
        TunnelState.AlwaysOn, TunnelState.Lockdown -> R.string.tunnel_always_on
    }

@get:StringRes
private val LogFilter.labelRes: Int
    get() = when (this) {
        LogFilter.All -> R.string.filter_all
        LogFilter.Blocked -> R.string.filter_blocked
        LogFilter.Allowed -> R.string.filter_allowed
    }

@get:StringRes
internal val Action.labelRes: Int
    get() = when (this) {
        Action.Allow -> R.string.allow
        Action.Block -> R.string.block
    }

@get:StringRes
private val Origin.labelRes: Int
    get() = when (this) {
        Origin.List -> R.string.origin_list
        Origin.Cname -> R.string.origin_cname
        Origin.Custom -> R.string.origin_custom
        Origin.Allow -> R.string.origin_allow
        Origin.None -> R.string.origin_none
        Origin.Cache -> R.string.origin_cache
        Origin.Stale -> R.string.origin_stale
    }

private val DefaultBringIntoViewSpec = object : BringIntoViewSpec {}

private fun Modifier.bringWholeIntoView(): Modifier = this then BringWholeIntoViewElement

private data object BringWholeIntoViewElement : ModifierNodeElement<BringWholeIntoViewNode>() {
    override fun create() = BringWholeIntoViewNode()

    override fun update(node: BringWholeIntoViewNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "bringWholeIntoView"
    }
}

private class BringWholeIntoViewNode : Modifier.Node(), BringIntoViewModifierNode {
    override suspend fun bringIntoView(childCoordinates: LayoutCoordinates, boundsProvider: () -> Rect?) = bringIntoView()
}

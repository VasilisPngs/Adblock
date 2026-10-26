package com.vasilispngs.adblock.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.vasilispngs.adblock.R
import com.vasilispngs.adblock.data.Appearance
import com.vasilispngs.adblock.data.SelfUpdate
import com.vasilispngs.adblock.data.ThemeMode
import kotlin.math.roundToInt

@Composable
internal fun SettingsScreen(
    appearance: Appearance,
    logEnabled: Boolean,
    selfUpdate: SelfUpdate,
    snackbarHostState: SnackbarHostState?,
    onThemeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onLogEnabledChange: (Boolean) -> Unit,
    onUpdateApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    var themeDialog by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(title = R.string.settings, snackbarHostState = snackbarHostState, modifier = modifier) { contentPadding, contentModifier ->
        LazyColumn(
            modifier = contentModifier
                .fillMaxSize()
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
                    onClick = { themeDialog = true },
                    shapes = ListItemDefaults.segmentedShapes(0, 2),
                    colors = listItemColors(),
                    supportingContent = { Text(text = stringResource(appearance.themeMode.labelRes)) }
                ) {
                    Text(text = stringResource(R.string.theme))
                }
            }

            item(key = "dynamic-color") {
                SwitchItem(
                    title = R.string.dynamic_color,
                    description = R.string.dynamic_color_description,
                    checked = appearance.dynamicColor,
                    onCheckedChange = onDynamicColorChange,
                    index = 1,
                    count = 2
                )
            }

            item(key = "privacy") {
                SectionHeader(text = stringResource(R.string.privacy))
            }

            item(key = "log-queries") {
                SwitchItem(
                    title = R.string.log_queries,
                    description = R.string.log_queries_description,
                    checked = logEnabled,
                    onCheckedChange = onLogEnabledChange,
                    index = 0,
                    count = 1
                )
            }
        }
    }

    if (themeDialog) {
        ThemeDialog(
            current = appearance.themeMode,
            onDismiss = { themeDialog = false },
            onSelect = { mode ->
                themeDialog = false
                onThemeChange(mode)
            }
        )
    }
}

@Composable
private fun SwitchItem(
    @StringRes title: Int,
    @StringRes description: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    index: Int,
    count: Int
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.semantics { role = Role.Switch },
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = listItemColors(),
        supportingContent = { Text(text = stringResource(description)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) }
    ) {
        Text(text = stringResource(title))
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
private fun ThemeDialog(current: ThemeMode, onDismiss: () -> Unit, onSelect: (ThemeMode) -> Unit) {
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
                    RadioOption(
                        label = stringResource(mode.labelRes),
                        selected = selected,
                        onClick = { onSelect(mode) },
                        modifier = if (selected) Modifier.focusRequester(focus) else Modifier
                    )
                }
            }
        },
        properties = dialogProperties()
    )
}

@get:StringRes
private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.System -> R.string.theme_system
        ThemeMode.Light -> R.string.theme_light
        ThemeMode.Dark -> R.string.theme_dark
    }

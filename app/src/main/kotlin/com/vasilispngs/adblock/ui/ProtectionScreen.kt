package com.vasilispngs.adblock.ui

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.vasilispngs.adblock.R
import com.vasilispngs.adblock.data.Action
import com.vasilispngs.adblock.data.ListSource
import com.vasilispngs.adblock.data.Rule
import com.vasilispngs.adblock.dns.httpsUrl
import java.text.NumberFormat

private val OptionHeight = 56.dp

@Composable
internal fun ProtectionScreen(
    uiState: ProtectionUiState,
    snackbarHostState: SnackbarHostState?,
    onAddSource: (String, String) -> Unit,
    onRemoveSource: (String) -> Unit,
    onAddRule: (String, Action) -> Unit,
    onRemoveRule: (String) -> Unit,
    onResolverChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var addingSource by rememberSaveable { mutableStateOf(false) }
    var removingSource by rememberSaveable { mutableStateOf<String?>(null) }
    var addingRule by rememberSaveable { mutableStateOf(false) }
    var editingResolver by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(title = R.string.protection, snackbarHostState = snackbarHostState, modifier = modifier) { contentPadding, contentModifier ->
        val locale = LocalConfiguration.current.locales[0]
        val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
        LazyColumn(
            modifier = contentModifier
                .fillMaxSize()
                .focusRestorer(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            item(key = "blocklists-header") { SectionHeader(text = stringResource(R.string.blocklists)) }
            val sourceCount = uiState.sources.size + 1
            itemsIndexed(uiState.sources, key = { _, source -> "source:${source.url}" }, contentType = { _, _ -> "source" }) { index, source ->
                SourceItem(source, numbers, index, sourceCount) { removingSource = source.url }
            }
            item(key = "add-blocklist") { AddItem(R.string.add_blocklist, sourceCount - 1, sourceCount) { addingSource = true } }

            item(key = "rules-header") { SectionHeader(text = stringResource(R.string.custom_rules)) }
            val ruleCount = uiState.rules.size + 1
            itemsIndexed(uiState.rules, key = { _, rule -> "rule:${rule.host}" }, contentType = { _, _ -> "rule" }) { index, rule ->
                RuleItem(rule, index, ruleCount) { onRemoveRule(rule.host) }
            }
            item(key = "add-rule") { AddItem(R.string.add_rule, ruleCount - 1, ruleCount) { addingRule = true } }

            item(key = "upstream-header") { SectionHeader(text = stringResource(R.string.upstream_dns)) }
            item(key = "resolver") {
                SegmentedListItem(
                    onClick = { editingResolver = true },
                    shapes = ListItemDefaults.segmentedShapes(0, 1),
                    colors = listItemColors(),
                    supportingContent = { Text(text = uiState.resolver) }
                ) {
                    Text(text = stringResource(R.string.doh_url))
                }
            }
        }
    }

    if (addingSource) {
        AddBlocklistDialog(
            onDismiss = { addingSource = false },
            onAdd = { url, name ->
                addingSource = false
                onAddSource(url, name)
            }
        )
    }

    removingSource?.let { url ->
        AlertDialog(
            onDismissRequest = { removingSource = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        removingSource = null
                        onRemoveSource(url)
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(text = stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { removingSource = null }, shapes = ButtonDefaults.shapes()) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
            title = { Text(text = stringResource(R.string.remove_blocklist)) },
            text = { Text(text = stringResource(R.string.remove_blocklist_description)) },
            properties = dialogProperties()
        )
    }

    if (addingRule) {
        AddRuleDialog(
            onDismiss = { addingRule = false },
            onAdd = { host, action ->
                addingRule = false
                onAddRule(host, action)
            }
        )
    }

    if (editingResolver) {
        ResolverDialog(
            current = uiState.resolver,
            onDismiss = { editingResolver = false },
            onSave = { url ->
                editingResolver = false
                onResolverChange(url)
            }
        )
    }
}

@Composable
private fun SourceItem(source: ListSource, numbers: NumberFormat, index: Int, count: Int, onRemove: () -> Unit) {
    val title = source.name.ifEmpty { source.url }
    val domains = source.domains
    val status = when {
        domains != null -> listOf(
            pluralStringResource(R.plurals.blocklist_domains, domains, numbers.format(domains)),
            updatedText(source.updatedAt)
        ).joinToString(" · ")
        source.failedAt != null -> stringResource(R.string.blocklist_failed)
        else -> stringResource(R.string.blocklist_downloading)
    }
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = listItemColors(),
        supportingContent = {
            Column {
                Text(text = link(source.url))
                Text(text = status)
            }
        },
        trailingContent = {
            IconAction(
                icon = R.drawable.ic_delete,
                label = stringResource(R.string.remove_named, title),
                tooltip = stringResource(R.string.remove),
                onClick = onRemove
            )
        }
    ) {
        Text(text = title)
    }
}

@Composable
private fun link(url: String): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    return remember(url, colors) {
        buildAnnotatedString {
            withLink(
                LinkAnnotation.Url(
                    url,
                    TextLinkStyles(
                        style = SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline),
                        focusedStyle = SpanStyle(color = colors.onPrimaryContainer, background = colors.primaryContainer)
                    )
                )
            ) {
                append(url)
            }
        }
    }
}

@Composable
private fun updatedText(updatedAt: Long?): String {
    val now = System.currentTimeMillis()
    return when {
        updatedAt == null || now - updatedAt < DateUtils.MINUTE_IN_MILLIS -> stringResource(R.string.blocklist_updated_now)
        else -> stringResource(
            R.string.blocklist_updated,
            DateUtils.getRelativeTimeSpanString(updatedAt, now, DateUtils.MINUTE_IN_MILLIS)
        )
    }
}

@Composable
private fun RuleItem(rule: Rule, index: Int, count: Int, onRemove: () -> Unit) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = listItemColors(),
        supportingContent = { Text(text = stringResource(rule.action.labelRes)) },
        trailingContent = {
            IconAction(
                icon = R.drawable.ic_close,
                label = stringResource(R.string.remove_named, rule.host),
                tooltip = stringResource(R.string.remove),
                onClick = onRemove
            )
        }
    ) {
        Text(text = rule.host)
    }
}

@Composable
private fun AddItem(@StringRes label: Int, index: Int, count: Int, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = listItemColors(),
        leadingContent = { Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null) }
    ) {
        Text(text = stringResource(label))
    }
}

@Composable
private fun AddBlocklistDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    val url = rememberTextFieldState()
    val name = rememberTextFieldState()
    val valid = httpsUrl(url.text.toString())
    val add: () -> Unit = { if (valid != null) onAdd(valid, name.text.toString()) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = add, enabled = valid != null, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        title = { Text(text = stringResource(R.string.add_blocklist)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SmallSpacing)) {
                DialogTextField(
                    state = url,
                    label = R.string.url,
                    error = R.string.url_invalid.takeIf { url.text.isNotBlank() && valid == null },
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                    modifier = Modifier.focusRequester(focus)
                )
                DialogTextField(
                    state = name,
                    label = R.string.name_optional,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                    onDone = add
                )
            }
        },
        properties = dialogProperties()
    )
}

@Composable
private fun AddRuleDialog(onDismiss: () -> Unit, onAdd: (String, Action) -> Unit) {
    val domain = rememberTextFieldState()
    var action by rememberSaveable { mutableStateOf(Action.Block) }
    val host = ruleHost(domain.text.toString())
    val add: () -> Unit = { if (host != null) onAdd(host, action) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = add, enabled = host != null, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        title = { Text(text = stringResource(R.string.add_rule)) },
        text = {
            Column {
                DialogTextField(
                    state = domain,
                    label = R.string.domain,
                    placeholder = R.string.domain_placeholder,
                    error = R.string.domain_invalid.takeIf { domain.text.isNotBlank() && host == null },
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                    onDone = add,
                    modifier = Modifier.focusRequester(focus)
                )
                Column(modifier = Modifier.selectableGroup()) {
                    listOf(Action.Block, Action.Allow).forEach { option ->
                        RadioOption(
                            label = stringResource(option.labelRes),
                            selected = option == action,
                            onClick = { action = option }
                        )
                    }
                }
            }
        },
        properties = dialogProperties()
    )
}

@Composable
private fun ResolverDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val state = rememberTextFieldState(current)
    val url = httpsUrl(state.text.toString())
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
        title = { Text(text = stringResource(R.string.upstream_dns)) },
        text = {
            DialogTextField(
                state = state,
                label = R.string.doh_url,
                placeholder = R.string.doh_url_placeholder,
                error = R.string.url_invalid.takeIf { state.text.isNotBlank() && url == null },
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
                onDone = { url?.let(onSave) },
                modifier = Modifier.focusRequester(focus)
            )
        },
        properties = dialogProperties()
    )
}

@Composable
private fun DialogTextField(
    state: TextFieldState,
    @StringRes label: Int,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
    @StringRes placeholder: Int? = null,
    @StringRes error: Int? = null,
    onDone: () -> Unit = {}
) {
    OutlinedTextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        shape = OutlinedTextFieldDefaults.roundedShape,
        colors = OutlinedTextFieldDefaults.tonalColors(),
        labelPosition = TextFieldLabelPosition.Inside(),
        label = { Text(text = stringResource(label)) },
        placeholder = placeholder?.let { { Text(text = stringResource(it)) } },
        supportingText = error?.let { { Text(text = stringResource(it)) } },
        isError = error != null,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = keyboardType, imeAction = imeAction),
        onKeyboardAction = { if (imeAction == ImeAction.Done) onDone() else it() },
        lineLimits = TextFieldLineLimits.SingleLine
    )
}

@Composable
internal fun RadioOption(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(OptionHeight)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        horizontalArrangement = Arrangement.spacedBy(ContentMargin),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

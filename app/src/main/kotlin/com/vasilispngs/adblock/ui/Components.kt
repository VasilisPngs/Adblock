package com.vasilispngs.adblock.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.isCrossWindowBlurEnabled
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.window.core.layout.WindowSizeClass

private val CompactMargin = 16.dp
private val WideMargin = 24.dp
private val MaxListWidth = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp
private val HeaderSpacing = 20.dp
private val EmptyStatePadding = 48.dp
private val EmptyStateIcon = 48.dp
private val DialogBlurRadius = 10.dp
private const val BLURRED_SCRIM_ALPHA = 0.2f
private const val SCRIM_ALPHA = 0.32f
internal val ContentMargin = 16.dp
internal val SmallSpacing = 8.dp

@Composable
internal fun ScreenScaffold(
    @StringRes title: Int,
    snackbarHostState: SnackbarHostState?,
    barState: TopAppBarState,
    modifier: Modifier = Modifier,
    content: @Composable (contentPadding: PaddingValues, contentModifier: Modifier) -> Unit
) {
    val wide = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val margin = if (wide) WideMargin else CompactMargin
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(barState)
    val contentInsets = ScaffoldDefaults.contentWindowInsets
    var consumedInsets by remember { mutableStateOf(WindowInsets(0)) }
    BoxWithConstraints(modifier = modifier.onConsumedWindowInsetsChanged { consumedInsets = it }) {
        val layoutDirection = LocalLayoutDirection.current
        val insets = contentInsets.exclude(consumedInsets).asPaddingValues()
        val safeWidth = maxWidth - insets.calculateStartPadding(layoutDirection) - insets.calculateEndPadding(layoutDirection)
        val sideMargin = ((safeWidth - MaxListWidth) / 2).coerceAtLeast(margin)
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = stringResource(title)) },
                    contentPadding = PaddingValues(horizontal = sideMargin - CompactMargin),
                    scrollBehavior = scrollBehavior
                )
            },
            snackbarHost = { snackbarHostState?.let { SnackbarHost(hostState = it) } },
            contentWindowInsets = contentInsets
        ) { padding ->
            content(
                PaddingValues(
                    start = padding.calculateStartPadding(layoutDirection) + sideMargin,
                    end = padding.calculateEndPadding(layoutDirection) + sideMargin,
                    bottom = padding.calculateBottomPadding() + margin
                ),
                Modifier
                    .padding(top = padding.calculateTopPadding())
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
            )
        }
    }
}

@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = ContentMargin, top = HeaderSpacing, end = ContentMargin, bottom = SmallSpacing)
            .semantics { heading() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IconAction(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tooltip: String = label
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(text = tooltip) } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes(), modifier = modifier) {
            Icon(painter = painterResource(icon), contentDescription = label)
        }
    }
}

@Composable
internal fun EmptyState(@DrawableRes icon: Int, title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ContentMargin, vertical = EmptyStatePadding),
        verticalArrangement = Arrangement.spacedBy(SmallSpacing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(bottom = SmallSpacing)
                .size(EmptyStateIcon)
        )
        Text(text = title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun dialogProperties() = DialogProperties(
    blurBehindRadius = DialogBlurRadius,
    scrimAlpha = if (LocalWindowInfo.current.isCrossWindowBlurEnabled) BLURRED_SCRIM_ALPHA else SCRIM_ALPHA
)

@Composable
internal fun listItemColors() = MaterialTheme.colorScheme.surfaceContainer.let { container ->
    ListItemDefaults.segmentedColors(containerColor = container, disabledContainerColor = container)
}

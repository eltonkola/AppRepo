package com.eltonkola.appdepo.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.eltonkola.appdepo.R
import com.eltonkola.appdepo.ui.screens.common.ErrorMessage
import com.eltonkola.appdepo.ui.screens.common.FullScreenLoadingIndicator
import com.eltonkola.appdepo.ui.viewmodel.HomeViewModel
import com.eltonkola.appdepo.ui.viewmodel.TrackedAppUiItem

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToAddApp: () -> Unit,
    onNavigateToDetails: (appId: Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.loadTrackedAppsAndCheckUpdates() // Ensure it's called on composition
    }
    LaunchedEffect(uiState.apps) {
        if (uiState.apps.isNotEmpty() || uiState.error != null) {
            focusRequester.requestFocus()
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.home_screen_title)) })
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 24.dp) // Typical TV padding
        ) {
            when {
                uiState.isLoading && uiState.apps.isEmpty() -> FullScreenLoadingIndicator()
                uiState.error != null -> ErrorMessage(
                    message = uiState.error ?: stringResource(R.string.error_fetching_data),
                    onRetry = { viewModel.loadTrackedAppsAndCheckUpdates() },
                    modifier = Modifier.focusRequester(focusRequester).focusable()
                )
                uiState.apps.isEmpty() -> EmptyState(
                    onAddAppClick = onNavigateToAddApp,
                    modifier = Modifier.focusRequester(focusRequester)
                )
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = onNavigateToAddApp,
                            modifier = Modifier
                                .padding(bottom = 24.dp)
                                .focusRequester(focusRequester) // Initial focus here or on the list
                        ) {
                            Text(stringResource(R.string.add_app_title))
                        }
                        LazyColumn (
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 16.dp) // Padding for last item
                        ) {
                            itemsIndexed (uiState.apps, key = { _, item -> item.entity.id }) { index, appItem ->
                                AppRowItem(
                                    appItem = appItem,
                                    onClick = { onNavigateToDetails(appItem.entity.id) },
                                    modifier = if (index == 0 && uiState.apps.isNotEmpty()) Modifier else Modifier // Focus first item if add button not focused
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EmptyState(onAddAppClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .focusable(), // Make the column focusable to receive initial focus
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.no_apps_tracked), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddAppClick) {
            Text(stringResource(R.string.add_your_first_app))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SimpleTvChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    borderColor: Color? = MaterialTheme.colorScheme.tertiary,
    borderWidth: Dp = 1.dp
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
//        shape = MaterialTheme.shapes.small, // Or a more pill-like shape: RoundedCornerShape(50)
//        color = containerColor,
//        contentColor = contentColor,
//        border = borderColor?.let { BorderStroke(borderWidth, it) },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge, // Or bodySmall for TV chips
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppRowItem(
    appItem: TrackedAppUiItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val app = appItem.entity

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        ListItem(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            headlineContent = { // <<<--- THIS IS WHERE THE MAIN TITLE GOES
                Text(
                    text = "${app.owner}/${app.repoName}",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = { // <<<--- THIS IS FOR SUBTEXT OR DESCRIPTION
                Text(
                    text = app.description ?: stringResource(R.string.no_description_available),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = { // <<<--- THIS IS FOR AN ICON/IMAGE ON THE LEFT
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(56.dp)
                ) {
                    AsyncImage(
                        model = "https://github.com/${app.owner}.png?size=120",
                        contentDescription = "${app.owner} avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            },

            trailingContent = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) { // Spacing for multiple chips
                    if (appItem.hasUpdate) {
                        SimpleTvChip( // Use your custom chip
                            text = stringResource(R.string.update_available_chip),
                            onClick = { /* Chip action, maybe same as card click or specific */ },
                            containerColor = MaterialTheme.colorScheme.primaryContainer, // Example: Highlight update
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else if (appItem.isInstalled) {
                        SimpleTvChip( // Use your custom chip
                            text = "${stringResource(R.string.app_installed_chip)}: ${app.installedVersionTag}",
                            onClick = { /* Chip action */ }
                            // Default colors will be used from SimpleTvChip definition
                        )
                    } else if (app.latestKnownReleaseTag != null) {
                        SimpleTvChip(
                            text = "Latest: ${app.latestKnownReleaseTag}",
                            onClick = { /* Chip action */ },
                            borderColor = MaterialTheme.colorScheme.tertiary // Example for an outlined look
                        )
                    }
                }
            },
            selected = false,
            onClick = {},

        )
    }
}


package com.eltonkola.appdepo.ui.screens

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import androidx.tv.material3.CardDefaults
import coil.compose.AsyncImage
import com.eltonkola.appdepo.R
import com.eltonkola.appdepo.data.remote.models.GithubAsset
import com.eltonkola.appdepo.data.remote.models.GithubRelease
import com.eltonkola.appdepo.ui.screens.common.ErrorMessage
import com.eltonkola.appdepo.ui.screens.common.FullScreenLoadingIndicator
import com.eltonkola.appdepo.ui.theme.FocusHighlight
import com.eltonkola.appdepo.ui.viewmodel.AppDetailsViewModel
import com.eltonkola.appdepo.ui.viewmodel.DownloadState

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun AppDetailsScreen(
    appId: Long, // Keep for fetching if needed, though VM handles it
    viewModel: AppDetailsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.loadAppDetails()
    }
    // Refocus after dialog closes or certain actions
    LaunchedEffect(uiState.showDeleteConfirmDialog, uiState.downloadState) {
        if (!uiState.showDeleteConfirmDialog && uiState.downloadState is DownloadState.Idle) {
            // Give a frame or two for composition to settle
            // kotlinx.coroutines.delay(16) // Delay for one frame (approx 16ms) - import kotlinx.coroutines.delay
            // or
            // withContext(NonCancellable) { /* if you need to ensure it runs */ }
            // A simple yield might also work sometimes to let other coroutines run.
            kotlinx.coroutines.yield() // Let composition/layout pass complete
            try {
                focusRequester.requestFocus()
            } catch (e: IllegalStateException) {
                // Log or handle if it still fails, which means the target isn't ready
                Log.w("FocusDebug", "Still couldn't request focus: ${e.message}")
            }
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.app?.repoName ?: stringResource(R.string.app_details_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    uiState.app?.let {
                        IconButton(onClick = { viewModel.promptDeleteApp() }) {
                            Icon(Icons.Filled.Delete, "Delete App")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 48.dp, vertical = 24.dp)
        ) {
            when {
                uiState.isLoadingDetails -> FullScreenLoadingIndicator()
                uiState.error != null && uiState.app == null -> ErrorMessage(
                    message = uiState.error!!,
                    onRetry = { viewModel.loadAppDetails() },
                    modifier = Modifier.focusRequester(focusRequester).focusable()
                )
                uiState.app == null -> ErrorMessage( // Should not happen if loading is false and no error
                    message = "App data unavailable.",
                    modifier = Modifier.focusRequester(focusRequester).focusable()
                )
                else -> {
                    val app = uiState.app!!
                    Column(modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester) // Main content focus
                        .focusable() // Allow main column to take focus
                    ) {
                        AppHeader(app = app, uiState = uiState, viewModel = viewModel)
                        Spacer(Modifier.height(24.dp))
                        ReleaseSection(uiState = uiState, viewModel = viewModel)
                    }
                }
            }

            if (uiState.showDeleteConfirmDialog) {
                DeleteConfirmationDialog(
                    appName = uiState.app?.repoName ?: "this app",
                    onConfirm = { viewModel.confirmDeleteApp(onNavigateBack) },
                    onDismiss = { viewModel.cancelDeleteApp() }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppHeader(
    app: com.eltonkola.appdepo.data.local.TrackedAppEntity,
    uiState: com.eltonkola.appdepo.ui.viewmodel.AppDetailsUiState,
    viewModel: AppDetailsViewModel
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Placeholder for App Icon if available from GitHub owner avatar
            // For now, using a generic icon or just text
            AsyncImage(
                model = "https://github.com/${app.owner}.png?size=120", // Owner avatar as placeholder
                contentDescription = "${app.owner} avatar",
                modifier = Modifier.size(80.dp).padding(end = 16.dp)
            )
            Column {
                Text(app.repoName, style = MaterialTheme.typography.displaySmall)
                Text(app.owner, style = MaterialTheme.typography.headlineSmall)
            }
        }
        Spacer(Modifier.height(16.dp))
        app.description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .heightIn(max = 100.dp) // Limit height for long descriptions
                    .verticalScroll(rememberScrollState())
            )
        }
        Spacer(Modifier.height(16.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.Start)
        ) {
            val latestApkAsset = uiState.apkAssetForLatestRelease
            val latestReleaseTag = app.latestKnownReleaseTag

            // Install/Update Button for Latest Release
            if (latestApkAsset != null && latestReleaseTag != null) {
                if (uiState.isInstalled && uiState.hasUpdate) {
                    ActionButton(
                        text = stringResource(R.string.update_button) + " ($latestReleaseTag)",
                        onClick = { viewModel.downloadAndInstallApk(uiState.releases.find { it.tagName == latestReleaseTag }!!, latestApkAsset) },
                        downloadState = uiState.downloadState,
                        isPrimary = true,
                        onCancelDownload = { viewModel.cancelDownload() }
                    )
                } else if (!uiState.isInstalled) {
                    ActionButton(
                        text = stringResource(R.string.install_button) + " ($latestReleaseTag)",
                        onClick = { viewModel.downloadAndInstallApk(uiState.releases.find { it.tagName == latestReleaseTag }!!, latestApkAsset) },
                        downloadState = uiState.downloadState,
                        isPrimary = true,
                        onCancelDownload = { viewModel.cancelDownload() }
                    )
                }
            }

            // Open Button
            if (uiState.isInstalled) {
                Button(onClick = { viewModel.openInstalledApp() }) {
                    Text(stringResource(R.string.open_button))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Display installed version
        if (uiState.isInstalled) {
            Text(
                "${stringResource(R.string.installed_version_label)} ${app.installedVersionTag}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (app.latestKnownReleaseTag != null && app.installedVersionTag != app.latestKnownReleaseTag) {
            Text(
                "${stringResource(R.string.latest_release_label)} ${app.latestKnownReleaseTag}",
                style = MaterialTheme.typography.bodyMedium
            )
        }


        // Show Download Progress if separate from button
        if (uiState.downloadState is DownloadState.Downloading) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (uiState.downloadState as DownloadState.Downloading).progress },
                modifier = Modifier.fillMaxWidth(0.7f)
            )
            Text(
                text = stringResource(R.string.downloading_apk) + " (${((uiState.downloadState as DownloadState.Downloading).progress * 100).toInt()}%)",
                style = MaterialTheme.typography.labelSmall
            )
        } else if (uiState.downloadState is DownloadState.Error) {
            Text((uiState.downloadState as DownloadState.Error).message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    downloadState: DownloadState,
    isPrimary: Boolean = false,
    onCancelDownload: () -> Unit
) {
    val colors = if (isPrimary) {
        ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer, // Or a brighter primary
            focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        ButtonDefaults.colors()
    }

    when (downloadState) {
        is DownloadState.Downloading -> {
            OutlinedButton(onClick = onCancelDownload, colors = ButtonDefaults.colors()) { // Or a cancel icon button
                Text("Cancel Download (${(downloadState.progress * 100).toInt()}%)")
            }
        }
        else -> {
            Button(onClick = onClick, colors = colors) {
                Text(text)
            }
        }
    }
}


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ReleaseSection(
    uiState: com.eltonkola.appdepo.ui.viewmodel.AppDetailsUiState,
    viewModel: AppDetailsViewModel
) {
    Column {
        Text(stringResource(R.string.releases_label), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        if (uiState.isLoadingReleases && uiState.releases.isEmpty()) {
            CircularProgressIndicator()
        } else if (uiState.releases.isEmpty()) {
            Text(stringResource(R.string.no_releases_found), style = MaterialTheme.typography.bodyLarge)
        } else {
            LazyColumn (
                modifier = Modifier.weight(1f), // Take remaining space
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items (uiState.releases, key = { it.id }) { release ->
                    ReleaseItem(
                        release = release,
                        currentApp = uiState.app!!,
                        downloadState = if (uiState.apkAssetForLatestRelease?.name == release.assets.firstOrNull { it.isApk() }?.name) uiState.downloadState else DownloadState.Idle, // Pass specific download state if this is the one being downloaded
                        onDownloadInstall = { asset -> viewModel.downloadAndInstallApk(release, asset) },
                        onCancelDownload = { viewModel.cancelDownload() },
                        isInstalledVersion = release.tagName == uiState.app.installedVersionTag
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ReleaseItem(
    release: GithubRelease,
    currentApp: com.eltonkola.appdepo.data.local.TrackedAppEntity,
    downloadState: DownloadState,
    onDownloadInstall: (GithubAsset) -> Unit,
    onCancelDownload: () -> Unit,
    isInstalledVersion: Boolean
) {
    val apkAsset = release.assets.firstOrNull { it.isApk() }

    Card(
        onClick = { apkAsset?.let { onDownloadInstall(it) } }, // Click card to install this specific release
        modifier = Modifier.fillMaxWidth(),
        // Highlight if it's the installed version or latest
        border = CardDefaults.border(
            border = if (isInstalledVersion) Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary))
            else if (release.tagName == currentApp.latestKnownReleaseTag) Border(BorderStroke(1.dp, MaterialTheme.colorScheme.secondary))
            else Border.None
        ),


    ) {
        Column(Modifier.padding(16.dp)) {
            Text(release.name ?: release.tagName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Tag: ${release.tagName}", style = MaterialTheme.typography.bodySmall)
            Text("Published: ${formatDateTime(release.publishedAt)}", style = MaterialTheme.typography.bodySmall)
            if (isInstalledVersion) {
                Button (onClick = {}) { Text("Currently Installed", color = MaterialTheme.colorScheme.primary) }
            }
            release.body?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it.take(200) + if (it.length > 200) "..." else "", // Truncate body
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3
                )
            }
            apkAsset?.let { asset ->
                Spacer(Modifier.height(12.dp))
                val buttonText = when {
                    isInstalledVersion -> "Reinstall (${asset.name.take(20)})"
                    release.tagName == currentApp.installedVersionTag -> "Reinstall (${asset.name.take(20)})"
                    release.tagName == currentApp.latestKnownReleaseTag && currentApp.installedVersionTag != null -> "Update to this (${asset.name.take(20)})"
                    else -> "Install (${asset.name.take(20)})"
                }
                ActionButton(
                    text = buttonText,
                    onClick = { onDownloadInstall(asset) },
                    downloadState = downloadState, // This needs to be specific to *this* asset if multiple downloads are possible
                    onCancelDownload = onCancelDownload
                )
                Text("Size: ${String.format("%.2f MB", asset.size / (1024.0 * 1024.0))}", style = MaterialTheme.typography.labelSmall)

            } ?: Text(
                stringResource(R.string.no_apk_asset),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DeleteConfirmationDialog(
    appName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $appName?") },
        text = { Text("Are you sure you want to remove $appName from your tracked apps? This action cannot be undone.") },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Delete")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = Modifier.widthIn(max = 400.dp) // Standard TV dialog width
    )
}


// Helper function (consider moving to a util file)
fun formatDateTime(dateTimeString: String): String {
    return try {
        // Assuming ISO 8601 format from GitHub API (e.g., "2023-10-26T10:00:00Z")
        val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault())
        inputFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(dateTimeString)
        val outputFormat = java.text.SimpleDateFormat("MMM dd, yyyy hh:mm a", java.util.Locale.getDefault())
        outputFormat.timeZone = java.util.TimeZone.getDefault() // Local timezone for display
        date?.let { outputFormat.format(it) } ?: dateTimeString
    } catch (e: Exception) {
        dateTimeString // Fallback to original string if parsing fails
    }
}
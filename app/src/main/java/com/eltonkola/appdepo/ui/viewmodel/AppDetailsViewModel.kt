package com.eltonkola.appdepo.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eltonkola.appdepo.data.local.TrackedAppEntity
import com.eltonkola.appdepo.data.remote.models.GithubAsset
import com.eltonkola.appdepo.data.remote.models.GithubRelease
import com.eltonkola.appdepo.data.repository.AppRepository
import com.eltonkola.appdepo.data.repository.RepoResult
import com.eltonkola.appdepo.ui.navigation.AppDestinations
import com.eltonkola.appdepo.util.ApkInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

data class AppDetailsUiState(
    val app: TrackedAppEntity? = null,
    val releases: List<GithubRelease> = emptyList(),
    val apkAssetForLatestRelease: GithubAsset? = null,
    val isLoadingDetails: Boolean = true,
    val isLoadingReleases: Boolean = false,
    val error: String? = null,
    val downloadState: DownloadState = DownloadState.Idle,
    val isInstalled: Boolean = false,
    val hasUpdate: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false,
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState() // progress 0.0 to 1.0
    data class Downloaded(val apkFile: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

@HiltViewModel
class AppDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appRepository: AppRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val TAG = "AppDetailsViewModel"
    private val appId: Long = checkNotNull(savedStateHandle[AppDestinations.APP_ID_ARG])

    private val _uiState = MutableStateFlow(AppDetailsUiState())
    val uiState: StateFlow<AppDetailsUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null

    init {
        loadAppDetails()
    }

    fun loadAppDetails() {
        _uiState.update { it.copy(isLoadingDetails = true, error = null) }
        viewModelScope.launch {
            val appEntity = appRepository.getTrackedAppById(appId)
            if (appEntity == null) {
                _uiState.update { it.copy(isLoadingDetails = false, error = "App not found.") }
                return@launch
            }

            val isInstalled = appEntity.installedVersionTag != null
            val hasUpdate = isInstalled &&
                    appEntity.latestKnownReleaseTag != null &&
                    appEntity.latestKnownReleaseTag != appEntity.installedVersionTag

            _uiState.update {
                it.copy(
                    app = appEntity,
                    isLoadingDetails = false,
                    isInstalled = isInstalled,
                    hasUpdate = hasUpdate
                )
            }
            fetchReleases(appEntity) // Fetch all releases
            fetchLatestReleaseInfo(appEntity) // Also specifically check latest for install/update button
        }
    }

    private fun fetchLatestReleaseInfo(app: TrackedAppEntity) {
        viewModelScope.launch {
            when (val result = appRepository.fetchLatestRelease(app.owner, app.repoName)) {
                is RepoResult.Success -> {
                    val latestRelease = result.data
                    val apkAsset = latestRelease.assets.firstOrNull { it.isApk() }
                    _uiState.update {
                        it.copy(
                            apkAssetForLatestRelease = apkAsset,
                            // Update local DB with this info if it's newer or different
                            // This is already handled by HomeViewModel's update check, but can be done here too
                        )
                    }
                    // Update the app entity in the DB (which will flow back to uiState.app)
                    appRepository.updateLatestReleaseInfoForApp(app)
                }
                is RepoResult.Error -> {
                    _uiState.update { it.copy(apkAssetForLatestRelease = null) } // Clear if error
                    Log.e(TAG, "Error fetching latest release for ${app.repoName}: ${result.message}")
                }
            }
        }
    }

    private fun fetchReleases(app: TrackedAppEntity) {
        _uiState.update { it.copy(isLoadingReleases = true) }
        viewModelScope.launch {
            when (val result = appRepository.fetchReleases(app.owner, app.repoName)) {
                is RepoResult.Success -> {
                    _uiState.update {
                        it.copy(
                            releases = result.data.filter { release -> !release.draft && release.assets.any { asset -> asset.isApk() } }, // Filter for usable releases
                            isLoadingReleases = false
                        )
                    }
                }
                is RepoResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingReleases = false,
                            error = result.message // Show error related to releases
                        )
                    }
                }
            }
        }
    }

    fun downloadAndInstallApk(release: GithubRelease, asset: GithubAsset) {
        val appEntity = _uiState.value.app ?: return
        if (_uiState.value.downloadState is DownloadState.Downloading) return // Already downloading

        downloadJob?.cancel() // Cancel previous if any
        _uiState.update { it.copy(downloadState = DownloadState.Downloading(0f), error = null) }

        downloadJob = viewModelScope.launch {
            val cacheDir = context.cacheDir
            val apkDir = File(cacheDir, "apks")
            if (!apkDir.exists()) apkDir.mkdirs()
            // Sanitize asset name for file system
            val sanitizedAssetName = asset.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val apkFile = File(apkDir, sanitizedAssetName)

            Log.i(TAG, "Starting download: ${asset.browserDownloadUrl} to ${apkFile.absolutePath}")

            appRepository.downloadApk(asset.browserDownloadUrl).let { result ->
                when (result) {
                    is RepoResult.Success -> {
                        val responseBody = result.data
                        try {
                            withContext(Dispatchers.IO) {
                                responseBody.byteStream().use { inputStream ->
                                    FileOutputStream(apkFile).use { outputStream ->
                                        val totalBytes = responseBody.contentLength()
                                        var bytesCopied = 0L
                                        val buffer = ByteArray(8 * 1024)
                                        var bytes = inputStream.read(buffer)
                                        while (bytes >= 0 && downloadJob?.isActive == true) {
                                            outputStream.write(buffer, 0, bytes)
                                            bytesCopied += bytes
                                            if (totalBytes > 0) {
                                                val progress = bytesCopied.toFloat() / totalBytes
                                                _uiState.update {
                                                    it.copy(downloadState = DownloadState.Downloading(progress))
                                                }
                                            }
                                            bytes = inputStream.read(buffer)
                                        }
                                    }
                                }
                            }
                            if (downloadJob?.isActive == true) { // Check if job was cancelled during IO
                                _uiState.update { it.copy(downloadState = DownloadState.Downloaded(apkFile)) }
                                installApkFromFile(apkFile, release.tagName)
                            } else {
                                Log.i(TAG, "Download cancelled for ${asset.name}")
                                apkFile.delete() // Clean up partially downloaded file
                                _uiState.update { it.copy(downloadState = DownloadState.Idle) }
                            }

                        } catch (e: IOException) {
                            Log.e(TAG, "IOException during download: ${e.message}", e)
                            _uiState.update { it.copy(downloadState = DownloadState.Error("Download IO Error: ${e.message}")) }
                            apkFile.delete()
                        } finally {
                            responseBody.close()
                        }
                    }
                    is RepoResult.Error -> {
                        Log.e(TAG, "Download API error: ${result.message}")
                        _uiState.update { it.copy(downloadState = DownloadState.Error("Download failed: ${result.message}")) }
                    }
                }
            }
        }
    }

    private fun installApkFromFile(apkFile: File, versionTag: String) {
        _uiState.value.app?.let { currentApp ->
            val installSuccess = ApkInstaller.installApk(context, apkFile)
            if (installSuccess) {
                // The user will be prompted by the system. We don't get a direct callback here.
                // We optimistically update the DB. A more robust solution might involve
                // listening for package installation broadcasts or re-checking on resume.
                viewModelScope.launch {
                    appRepository.updateInstalledVersion(currentApp.id, versionTag)
                    // Update local state to reflect installation attempt
                    _uiState.update {
                        it.copy(
                            isInstalled = true, // Assume install will succeed or user will complete
                            hasUpdate = false, // If this was an update, it's now up-to-date
                            downloadState = DownloadState.Idle // Reset download state
                        )
                    }
                    Log.i(TAG, "Installation initiated for ${apkFile.name}, version $versionTag")
                }
            } else {
                Log.e(TAG, "Failed to initiate APK installation for ${apkFile.name}")
                _uiState.update { it.copy(downloadState = DownloadState.Error("Could not start installation."), error = "Failed to start installer.") }
            }
        }
    }

    fun openInstalledApp() {
        _uiState.value.app?.let { appEntity ->
            // This is a simplification. For a real app store, you'd need the package name
            // of the installed app. GitHub repos don't directly provide this.
            // You might store it if the app registers itself, or if you parse it from manifests
            // during a more complex addition process.
            // For now, we'll assume a convention or that it's not implemented.
            val packageName = guessPackageName(appEntity.owner, appEntity.repoName) // Placeholder
            val launchIntent = context.packageManager.getLeanbackLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                try {
                    context.startActivity(launchIntent)
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = "Could not open app: ${e.message}") }
                }
            } else {
                _uiState.update { it.copy(error = "App not found or no TV launcher intent for $packageName.") }
            }
        }
    }

    // Placeholder: In a real scenario, you'd need a reliable way to get the package name.
    private fun guessPackageName(owner: String, repo: String): String {
        return "com.$owner.$repo".lowercase().replace("-", "").replace("_", "")
    }

    fun refreshAppInstallationStatus() {
        _uiState.value.app?.let { appEntity ->
            viewModelScope.launch {
                // This is a basic check. A more robust way is to get package info.
                val isActuallyInstalled = appEntity.installedVersionTag != null // Re-evaluate based on DB
                // You could also query PackageManager for the actual installed version if you knew the package name.
                // For now, rely on the DB state which is updated after install attempt.
                _uiState.update {
                    it.copy(
                        isInstalled = isActuallyInstalled,
                        hasUpdate = isActuallyInstalled &&
                                appEntity.latestKnownReleaseTag != null &&
                                appEntity.latestKnownReleaseTag != appEntity.installedVersionTag
                    )
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _uiState.update { it.copy(downloadState = DownloadState.Idle) }
        // Optionally delete any partially downloaded file if you know its path
        Log.i(TAG, "Download cancelled by user.")
    }

    fun promptDeleteApp() {
        _uiState.update { it.copy(showDeleteConfirmDialog = true) }
    }

    fun cancelDeleteApp() {
        _uiState.update { it.copy(showDeleteConfirmDialog = false) }
    }
    fun confirmDeleteApp(onAppDeleted: () -> Unit) {
        _uiState.update { it.copy(showDeleteConfirmDialog = false) }
        _uiState.value.app?.let { appToDelete ->
            viewModelScope.launch {
                appRepository.deleteTrackedApp(appToDelete)
                onAppDeleted() // Navigate back or update UI
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        downloadJob?.cancel() // Ensure download is cancelled if VM is cleared
    }
}
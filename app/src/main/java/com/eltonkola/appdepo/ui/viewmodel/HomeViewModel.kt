package com.eltonkola.appdepo.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eltonkola.appdepo.data.local.TrackedAppEntity
import com.eltonkola.appdepo.data.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackedAppUiItem(
    val entity: TrackedAppEntity,
    val hasUpdate: Boolean,
    val isInstalled: Boolean
)

data class HomeUiState(
    val apps: List<TrackedAppUiItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val lastCheckedTimestamp: Long = 0L // To trigger recomposition after checks
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appRepository: AppRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadTrackedAppsAndCheckUpdates()
    }

    fun loadTrackedAppsAndCheckUpdates() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            appRepository.getTrackedApps().collectLatest { apps ->
                // Perform update checks for all apps when the list is loaded or changes
                apps.forEach { app ->
                    // Only check if not recently checked (e.g., within last 5 minutes)
                    // to avoid excessive API calls on rapid DB changes.
                    // Or remove this check if you want to check every time DB changes.
                    if (System.currentTimeMillis() - app.lastCheckedTimestamp > 5 * 60 * 1000) {
                        appRepository.updateLatestReleaseInfoForApp(app)
                    }
                }
                // The DB flow will emit again after updateLatestReleaseInfoForApp updates the DB,
                // so we map the fresh data from DB here.
                val uiItems = apps.map { entity ->
                    val isInstalled = entity.installedVersionTag != null
                    val hasUpdate = isInstalled &&
                            entity.latestKnownReleaseTag != null &&
                            entity.latestKnownReleaseTag != entity.installedVersionTag
                    TrackedAppUiItem(entity, hasUpdate, isInstalled)
                }
                _uiState.update {
                    it.copy(
                        apps = uiItems,
                        isLoading = false,
                        lastCheckedTimestamp = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    // Call this explicitly if needed, e.g., from a refresh button
    fun forceCheckAllUpdates() {
        viewModelScope.launch {
            _uiState.value.apps.forEach { appItem ->
                appRepository.updateLatestReleaseInfoForApp(appItem.entity)
            }
            // DB flow will automatically refresh the list.
            // No need to manually update _uiState here unless you want immediate isLoading feedback
        }
    }
}
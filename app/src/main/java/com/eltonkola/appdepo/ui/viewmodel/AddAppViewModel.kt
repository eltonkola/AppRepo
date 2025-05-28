package com.eltonkola.appdepo.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eltonkola.appdepo.data.remote.models.GithubRepo
import com.eltonkola.appdepo.data.repository.AppRepository
import com.eltonkola.appdepo.data.repository.RepoResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeaturedApp(
    val id: String,
    val name: String,
    val description: String?, // Nullable if it can be missing
    val iconUrl: String?,    // Nullable
    val tags: List<String>? = emptyList() // Optional, provide default
)


data class AddAppUiState(
    // Unified input field
    val searchQuery: String = "",

    // Search results
    val searchResults: List<GithubRepo> = emptyList(),
    val isLoadingSearch: Boolean = false,
    val searchError: String? = null,

    // Featured Apps
    val featuredApps: List<FeaturedApp> = emptyList(),
    val isLoadingFeatured: Boolean = false,
    val featuredError: String? = null,

    // Specific loading states for adding apps
    val isLoadingAddByFullName: Boolean = false,       // For the "Add by Full Name" button
    val isLoadingAddFromSearch: Boolean = false,    // When adding from a search result item
    val isLoadingAddFromFeatured: Boolean = false,  // When adding from a featured app item
    val addingInProgressId: String? = null,        // ID (e.g., repo.fullName or featuredApp.id) of the item being added

    // General messages
    val addError: String? = null,
    val addSuccessMessage: String? = null
)

@HiltViewModel
class AddAppViewModel @Inject constructor(
    private val appRepository: AppRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddAppUiState())
    val uiState: StateFlow<AddAppUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query, searchError = null, addError = null, addSuccessMessage = null) }
        searchJob?.cancel()
        if (query.length > 2) {
            searchJob = viewModelScope.launch {
                delay(500) // Debounce
                performSearch(query)
            }
        } else {
            _uiState.update { it.copy(searchResults = emptyList(), isLoadingSearch = false) }
        }
    }

    private fun performSearch(query: String) {
        _uiState.update { it.copy(isLoadingSearch = true, searchError = null) }
        viewModelScope.launch {
            when (val result = appRepository.searchGithub(query)) {
                is RepoResult.Success -> {
                    _uiState.update {
                        it.copy(
                            searchResults = result.data.items,
                            isLoadingSearch = false
                        )
                    }
                }
                is RepoResult.Error -> {
                    _uiState.update {
                        it.copy(
                            searchError = result.message,
                            isLoadingSearch = false,
                            searchResults = emptyList()
                        )
                    }
                }
            }
        }
    }

    fun addAppByFullName(onAppAddedSuccessfully: () -> Unit) {
        val fullName = _uiState.value.searchQuery.trim()
        if (!fullName.contains('/')) {
            _uiState.update { it.copy(addError = "Invalid format. Use owner/repo_name.") } // Consider using string resource
            return
        }
        val parts = fullName.split('/')
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            _uiState.update { it.copy(addError = "Invalid format. Use owner/repo_name.") } // Consider using string resource
            return
        }
        val owner = parts[0]
        val repoName = parts[1]

        _uiState.update { it.copy(isLoadingAddByFullName = true, addError = null, addSuccessMessage = null) }
        viewModelScope.launch {
            when (val result = appRepository.addTrackedAppByOwnerRepo(owner, repoName)) {
                is RepoResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingAddByFullName = false,
                            addSuccessMessage = "App '$fullName' added!", // Consider using string resource with placeholder
                            searchQuery = "" // Clear input on success
                        )
                    }
                    onAppAddedSuccessfully()
                }
                is RepoResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingAddByFullName = false,
                            addError = result.message // This message comes from the repository
                        )
                    }
                }
            }
        }
    }

    fun addAppFromSearchResult(repo: GithubRepo, onAppAddedSuccessfully: () -> Unit) {
        _uiState.update {
            it.copy(
                isLoadingAddFromSearch = true,
                addingInProgressId = repo.fullName, // Or repo.id.toString()
                addError = null,
                addSuccessMessage = null
            )
        }
        viewModelScope.launch {
            when (val result = appRepository.addTrackedAppFromRepo(repo)) {
                is RepoResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingAddFromSearch = false,
                            addingInProgressId = null,
                            addSuccessMessage = "App '${repo.fullName}' added!", // Consider using string resource
                            searchQuery = "",
                            searchResults = emptyList()
                        )
                    }
                    onAppAddedSuccessfully()
                }
                is RepoResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingAddFromSearch = false,
                            addingInProgressId = null,
                            addError = result.message
                        )
                    }
                }
            }
        }
    }

    fun loadFeaturedApps() {
        _uiState.update { it.copy(isLoadingFeatured = true, featuredError = null) }
        viewModelScope.launch {
            // No more delay, actual network call
            when (val result = appRepository.fetchFeaturedApps()) { // Call the repository method
                is RepoResult.Success -> {
                    _uiState.update { it.copy(isLoadingFeatured = false, featuredApps = result.data) }
                }
                is RepoResult.Error -> {
                    _uiState.update { it.copy(isLoadingFeatured = false, featuredError = result.message) }
                }
            }
        }
    }

    fun addAppFromFeatured(app: FeaturedApp, onAppAddedSuccessfully: () -> Unit) {
        val fullName = app.name
        if (!fullName.contains('/')) {
            _uiState.update { it.copy(addError = "Featured app '${app.name}' has invalid format.") }
            return
        }
        val parts = fullName.split('/')
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            _uiState.update { it.copy(addError = "Featured app '${app.name}' has invalid format.") }
            return
        }
        val owner = parts[0]
        val repoName = parts[1]

        _uiState.update {
            it.copy(
                isLoadingAddFromFeatured = true,
                addingInProgressId = app.id,
                addError = null,
                addSuccessMessage = null
            )
        }
        viewModelScope.launch {
            when (val result = appRepository.addTrackedAppByOwnerRepo(owner, repoName)) {
                is RepoResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingAddFromFeatured = false,
                            addingInProgressId = null,
                            addSuccessMessage = "App '${app.name}' added!" // Consider using string resource
                        )
                    }
                    onAppAddedSuccessfully()
                }
                is RepoResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingAddFromFeatured = false,
                            addingInProgressId = null,
                            addError = "Error adding '${app.name}': ${result.message}"
                        )
                    }
                }
            }
        }
    }

    fun clearAddMessage() {
        _uiState.update { it.copy(addError = null, addSuccessMessage = null) }
    }
}
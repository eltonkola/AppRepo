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

data class AddAppUiState(
    val searchQuery: String = "",
    val ownerRepoInput: String = "",
    val searchResults: List<GithubRepo> = emptyList(),
    val isLoadingSearch: Boolean = false,
    val isLoadingAdd: Boolean = false,
    val searchError: String? = null,
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
        _uiState.update { it.copy(searchQuery = query, searchError = null) }
        searchJob?.cancel() // Cancel previous job
        if (query.length > 2) { // Start search after 3 characters
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

    fun onOwnerRepoInputChanged(input: String) {
        _uiState.update { it.copy(ownerRepoInput = input, addError = null, addSuccessMessage = null) }
    }

    fun addAppByOwnerRepo(onAppAddedSuccessfully: () -> Unit) {
        val input = _uiState.value.ownerRepoInput.trim()
        if (!input.contains('/')) {
            _uiState.update { it.copy(addError = "Invalid format. Use owner/repo_name.") }
            return
        }
        val parts = input.split('/')
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            _uiState.update { it.copy(addError = "Invalid format. Use owner/repo_name.") }
            return
        }
        val owner = parts[0]
        val repoName = parts[1]

        _uiState.update { it.copy(isLoadingAdd = true, addError = null, addSuccessMessage = null) }
        viewModelScope.launch {
            when (val result = appRepository.addTrackedAppByOwnerRepo(owner, repoName)) {
                is RepoResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingAdd = false,
                            addSuccessMessage = "App '$owner/$repoName' added!",
                            ownerRepoInput = "" // Clear input on success
                        )
                    }
                    onAppAddedSuccessfully()
                }
                is RepoResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingAdd = false,
                            addError = result.message
                        )
                    }
                }
            }
        }
    }

    fun addAppFromSearchResult(repo: GithubRepo, onAppAddedSuccessfully: () -> Unit) {
        _uiState.update { it.copy(isLoadingAdd = true, addError = null, addSuccessMessage = null) }
        viewModelScope.launch {
            when (val result = appRepository.addTrackedAppFromRepo(repo)) {
                is RepoResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingAdd = false,
                            addSuccessMessage = "App '${repo.fullName}' added!",
                            // Optionally clear search results or query
                            searchQuery = "",
                            searchResults = emptyList()
                        )
                    }
                    onAppAddedSuccessfully()
                }
                is RepoResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingAdd = false,
                            addError = result.message
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
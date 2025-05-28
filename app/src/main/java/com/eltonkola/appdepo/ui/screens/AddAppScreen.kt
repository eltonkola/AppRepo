package com.eltonkola.appdepo.ui.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import androidx.tv.material3.MaterialTheme
import coil.compose.AsyncImage
import com.eltonkola.appdepo.R
import com.eltonkola.appdepo.data.remote.models.GithubRepo
import com.eltonkola.appdepo.ui.screens.common.CenteredMessage
import com.eltonkola.appdepo.ui.screens.common.ErrorMessage
import com.eltonkola.appdepo.ui.viewmodel.AddAppViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun AddAppScreen(
    viewModel: AddAppViewModel,
    onNavigateBack: () -> Unit,
    onAppAdded: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val (searchFieldFocus, ownerRepoFieldFocus, addButtonFocus, searchResultsListFocus) = remember { FocusRequester.createRefs() }

    // For showing temporary success/error messages
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.addSuccessMessage) {
        uiState.addSuccessMessage?.let {
            snackbarMessage = it
            viewModel.clearAddMessage() // Clear from VM state
            delay(2000) // Show for 2 seconds
            snackbarMessage = null
            onAppAdded() // Callback after success message
        }
    }
    LaunchedEffect(uiState.addError) {
        uiState.addError?.let {
            snackbarMessage = it
            viewModel.clearAddMessage() // Clear from VM state
            delay(3000) // Show error longer
            snackbarMessage = null
        }
    }
    LaunchedEffect(Unit) {
        ownerRepoFieldFocus.requestFocus() // Initial focus on the owner/repo field
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_app_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        // Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        Text("Back") // Simpler for TV for now
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 48.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Add by owner/repo section
                Text("Add by Owner/Repository Name", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = uiState.ownerRepoInput,
                    onValueChange = { viewModel.onOwnerRepoInputChanged(it) },
                    label = { Text(stringResource(R.string.add_by_owner_repo_hint)) },
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .focusRequester(ownerRepoFieldFocus),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        viewModel.addAppByOwnerRepo(onAppAdded)
                        keyboardController?.hide()
                    })
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.addAppByOwnerRepo(onAppAdded)
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    },
                    enabled = uiState.ownerRepoInput.isNotBlank() && !uiState.isLoadingAdd,
                    modifier = Modifier.focusRequester(addButtonFocus)
                ) {
                    if (uiState.isLoadingAdd && uiState.ownerRepoInput.isNotBlank()) { // Check if this button caused loading
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.add_button))
                    }
                }

                Spacer(Modifier.height(32.dp))
                HorizontalDivider()
                Spacer(Modifier.height(32.dp))

                // Search section
                Text("Search on GitHub", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    label = { Text(stringResource(R.string.search_apps_hint)) },
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .focusRequester(searchFieldFocus),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        // Search is triggered by onValueChange debounce
                        keyboardController?.hide()
                        focusManager.clearFocus() // Or move focus to results
                    })
                )
                Spacer(Modifier.height(16.dp))

                if (uiState.isLoadingSearch) {
                    CircularProgressIndicator(modifier = Modifier.padding(vertical = 16.dp))
                } else if (uiState.searchError != null) {
                    ErrorMessage(message = uiState.searchError!!, onRetry = { viewModel.onSearchQueryChanged(uiState.searchQuery) })
                } else if (uiState.searchQuery.length > 2 && uiState.searchResults.isEmpty() && !uiState.isLoadingSearch) {
                    CenteredMessage(message = stringResource(R.string.no_search_results))
                } else if (uiState.searchResults.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(0.8f)
                            .focusRequester(searchResultsListFocus)
                            .focusable(), // Make the list itself focusable
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items (uiState.searchResults, key = { it.id }) { repo ->
                            SearchResultItem(
                                repo = repo,
                                onAddClick = { viewModel.addAppFromSearchResult(repo, onAppAdded) },
                                isLoading = uiState.isLoadingAdd // General loading for add
                            )
                        }
                    }
                }
            }

            // Snackbar-like message display at the bottom
            snackbarMessage?.let { message ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = SurfaceDefaults.colors(
                        containerColor = if (uiState.addError != null || uiState.searchError != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (uiState.addError != null || uiState.searchError != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchResultItem(
    repo: GithubRepo,
    onAddClick: () -> Unit,
    isLoading: Boolean
) {
    Card(
        onClick = { /* Maybe navigate to a pre-add detail view or directly add */ },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(repo.fullName, style = MaterialTheme.typography.titleMedium)
                repo.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Icon(painter = painterResource(id = R.drawable.ic_star), contentDescription = "Stars") // Placeholder
                    Text("⭐ ${repo.stars ?: 0}", style = MaterialTheme.typography.bodySmall)
                    repo.language?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(" ($it)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            AsyncImage(
                model = repo.owner.avatarUrl,
                contentDescription = "${repo.owner.login} avatar",
                modifier = Modifier.size(40.dp).padding(end = 8.dp) // Example size
            )
            Button(onClick = onAddClick, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.add_button))
                }
            }
        }
    }
}
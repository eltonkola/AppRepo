package com.eltonkola.appdepo.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator // Core M3 for general indicators
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider // Core M3
import androidx.compose.material3.Icon // Core M3
import androidx.compose.material3.Scaffold // Core M3
import androidx.compose.material3.TextField // Core M3
import androidx.compose.material3.TopAppBar // Core M3
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.* // TV Material3 components
import androidx.tv.material3.MaterialTheme // TV MaterialTheme
import coil.compose.AsyncImage
import com.eltonkola.appdepo.R
import com.eltonkola.appdepo.data.remote.models.GithubRepo
import com.eltonkola.appdepo.ui.screens.common.CenteredMessage // Assuming you have this
import com.eltonkola.appdepo.ui.screens.common.ErrorMessage // Assuming you have this
import com.eltonkola.appdepo.ui.viewmodel.AddAppViewModel
import com.eltonkola.appdepo.ui.viewmodel.FeaturedApp // Import FeaturedApp if defined in ViewModel package
import kotlinx.coroutines.delay

@OptIn(
    ExperimentalTvMaterial3Api::class,
    ExperimentalComposeUiApi::class,
    ExperimentalMaterial3Api::class // For Scaffold, TopAppBar, TextField, Icon from core M3
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

    val (
        searchInputFocus,
        addByFullNameButtonFocus,
        searchResultsListFocus,
        featuredAppsListFocus
    ) = remember { FocusRequester.createRefs() }

    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.addSuccessMessage) {
        uiState.addSuccessMessage?.let {
            snackbarMessage = it
            viewModel.clearAddMessage()
            delay(2500) // Show success a bit longer
            snackbarMessage = null
            onAppAdded()
        }
    }
    LaunchedEffect(uiState.addError) {
        uiState.addError?.let {
            snackbarMessage = it
            viewModel.clearAddMessage()
            delay(3500) // Show error longer
            snackbarMessage = null
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadFeaturedApps()
        searchInputFocus.requestFocus()
    }

    Scaffold( // Using core M3 Scaffold
        topBar = {
            TopAppBar( // Using core M3 TopAppBar
                title = { androidx.compose.material3.Text(stringResource(R.string.add_app_title)) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onNavigateBack) {
                        Icon( // Core M3 Icon
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_button_desc)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 48.dp, vertical = 24.dp) // Main content padding
            ) {
                // --- Left Panel: Search and Add ---
                Column(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                        .padding(end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text( // TV Text
                        stringResource(R.string.add_or_search_apps),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.height(16.dp))

                    TextField( // Core M3 TextField
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        label = { androidx.compose.material3.Text(stringResource(R.string.owner_repo_or_search_hint)) },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .focusRequester(searchInputFocus)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    keyboardController?.show()
                                }
                            },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboardController?.hide()
                            if (uiState.searchResults.isNotEmpty()) {
                                searchResultsListFocus.requestFocus()
                            } else {
                                // focusManager.clearFocus(true) // Clear focus if no results to move to
                            }
                        })
                    )
                    Spacer(Modifier.height(12.dp))

                    Button( // TV Button
                        onClick = {
                            viewModel.addAppByFullName(onAppAdded)
                            keyboardController?.hide()
                        },
                        enabled = uiState.searchQuery.isNotBlank() && !uiState.isLoadingAddByFullName,
                        modifier = Modifier.focusRequester(addByFullNameButtonFocus)
                    ) {
                        if (uiState.isLoadingAddByFullName && uiState.searchQuery.isNotBlank()) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.add_by_full_name_button))
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider(modifier = Modifier.fillMaxWidth(0.9f)) // Core M3 Divider
                    Spacer(Modifier.height(16.dp))

                    if (uiState.isLoadingSearch) {
                        CircularProgressIndicator(modifier = Modifier.padding(vertical = 16.dp))
                    } else if (uiState.searchError != null) {
                        ErrorMessage( // Your custom error composable
                            message = uiState.searchError!!,
                            onRetry = { viewModel.onSearchQueryChanged(uiState.searchQuery) }
                        )
                    } else if (uiState.searchQuery.length > 2 && uiState.searchResults.isEmpty() && !uiState.isLoadingSearch) {
                        CenteredMessage(message = stringResource(R.string.no_search_results))
                    } else if (uiState.searchResults.isNotEmpty()) {
                        Text(stringResource(R.string.search_results_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(0.95f)
                                .focusRequester(searchResultsListFocus)
                                .focusable(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.searchResults, key = { it.id }) { repo ->
                                SearchResultItem(
                                    repo = repo,
                                    onAddClick = { viewModel.addAppFromSearchResult(repo, onAppAdded) },
                                    isLoading = uiState.isLoadingAddFromSearch && uiState.addingInProgressId == repo.fullName, // or repo.id.toString()
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        CenteredMessage(message = stringResource(R.string.type_to_search_apps))
                    }
                }

                // --- Right Panel: Featured Apps ---
                Column(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight()
                        .padding(start = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.featured_apps_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.height(16.dp))

                    if (uiState.isLoadingFeatured) {
                        CircularProgressIndicator(modifier = Modifier.padding(vertical = 16.dp))
                    } else if (uiState.featuredError != null) {
                        ErrorMessage(
                            message = uiState.featuredError!!,
                            onRetry = { viewModel.loadFeaturedApps() }
                        )
                    } else if (uiState.featuredApps.isEmpty()) {
                        CenteredMessage(message = stringResource(R.string.no_featured_apps))
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize() // Takes all available space in this column
                                .focusRequester(featuredAppsListFocus)
                                .focusable(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.featuredApps, key = { it.id }) { app ->
                                FeaturedAppItem(
                                    app = app,
                                    onAddClick = { viewModel.addAppFromFeatured(app, onAppAdded) },
                                    isLoading = uiState.isLoadingAddFromFeatured && uiState.addingInProgressId == app.id,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // Snackbar-like message display at the bottom
            snackbarMessage?.let { message ->
                Surface( // TV Surface
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(0.7f) // Snackbar width
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.medium, // TV Shapes
                    colors = SurfaceDefaults.colors( // TV SurfaceDefaults
                        containerColor = if (uiState.addError != null || uiState.searchError != null || uiState.featuredError != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.inverseSurface,
                        contentColor = if (uiState.addError != null || uiState.searchError != null || uiState.featuredError != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.inverseOnSurface
                    )
                ) {
                    Text( // TV Text
                        text = message,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
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
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card( // TV Card
        onClick = { /* If clickable, decide action e.g. onAddClick() or focus button */ },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = repo.owner.avatarUrl,
                contentDescription = "${repo.owner.login} avatar",
                modifier = Modifier.size(40.dp).padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(repo.fullName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                repo.description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }
                // Row(verticalAlignment = Alignment.CenterVertically) { // Optional: Stars/Language
                //     Text("⭐ ${repo.stars ?: 0}", style = MaterialTheme.typography.bodySmall)
                //     repo.language?.let {
                //         Spacer(Modifier.width(8.dp))
                //         Text(" ($it)", style = MaterialTheme.typography.bodySmall)
                //     }
                // }
            }
            Button(onClick = onAddClick, enabled = !isLoading, modifier = Modifier.height(40.dp)) { // TV Button
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.add_button_short), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class /*, androidx.compose.foundation.layout.ExperimentalLayoutApi::class // if using FlowRow from compose foundation layout once stable */)
@Composable
fun FeaturedAppItem(
    app: FeaturedApp,
    onAddClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { /* Decide action */ },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            app.iconUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = "${app.name} icon",
                    modifier = Modifier.size(40.dp).padding(end = 12.dp)
                )
            } ?: Spacer(Modifier.size(40.dp).padding(end = 12.dp))

            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(app.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                app.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                }

                // Display Tags as Chips
                if (app.tags?.isNotEmpty() == true) {
                    Row( // Use FlowRow if you expect many tags and want wrapping.
                        // For TV, a simple Row showing a limited number is often safer.
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        app.tags.take(3).forEach { tag -> // Show max 3 tags to prevent overflow
                            Surface(
                                shape = RoundedCornerShape(8.dp), // Or MaterialTheme.shapes.small
                                colors = SurfaceDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),

//                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (app.tags.size > 3) {
                            Text("...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Button(onClick = onAddClick, enabled = !isLoading, modifier = Modifier.height(40.dp)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.add_button_short), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
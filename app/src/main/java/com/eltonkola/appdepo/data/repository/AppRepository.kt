package com.eltonkola.appdepo.data.repository

import android.util.Log
import com.eltonkola.appdepo.data.local.TrackedAppDao
import com.eltonkola.appdepo.data.local.TrackedAppEntity
import com.eltonkola.appdepo.data.remote.FeaturedAppsApiService
import com.eltonkola.appdepo.data.remote.GithubApiService
import com.eltonkola.appdepo.data.remote.models.GithubRelease
import com.eltonkola.appdepo.data.remote.models.GithubRepo
import com.eltonkola.appdepo.data.remote.models.SearchResponse
import com.eltonkola.appdepo.ui.viewmodel.FeaturedApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import javax.inject.Inject
import javax.inject.Singleton

sealed class RepoResult<out T> {
    data class Success<out T>(val data: T) : RepoResult<T>()
    data class Error(val message: String, val code: Int? = null) : RepoResult<Nothing>()
}

@Singleton
class AppRepository @Inject constructor(
    private val trackedAppDao: TrackedAppDao,
    private val githubApiService: GithubApiService,
    private val featuredAppsApiService: FeaturedAppsApiService
) {
    private val TAG = "AppRepository"

    fun getTrackedApps(): Flow<List<TrackedAppEntity>> = trackedAppDao.getAllTrackedApps()

    suspend fun getTrackedAppById(id: Long): TrackedAppEntity? =
        withContext(Dispatchers.IO) {
            trackedAppDao.getTrackedAppById(id)
        }

    suspend fun addTrackedAppFromRepo(githubRepo: GithubRepo): RepoResult<Long> {
        return withContext(Dispatchers.IO) {
            try {
                val existing = trackedAppDao.getTrackedAppByOwnerAndRepo(githubRepo.owner.login, githubRepo.name)
                if (existing != null) {
                    Log.w(TAG, "App ${githubRepo.fullName} already tracked with id ${existing.id}")
                    RepoResult.Error("App already tracked.", -1) // Using -1 for already tracked
                } else {
                    val entity = TrackedAppEntity(
                        owner = githubRepo.owner.login,
                        repoName = githubRepo.name,
                        description = githubRepo.description,
                        htmlUrl = githubRepo.htmlUrl
                    )
                    val id = trackedAppDao.insertTrackedApp(entity)
                    Log.i(TAG, "Added app ${entity.repoName} with id $id")
                    RepoResult.Success(id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding tracked app: ${githubRepo.fullName}", e)
                RepoResult.Error("Database error: ${e.localizedMessage}")
            }
        }
    }

    suspend fun addTrackedAppByOwnerRepo(owner: String, repoName: String): RepoResult<Long> {
        return withContext(Dispatchers.IO) {
            try {
                val response = githubApiService.getRepository(owner, repoName)
                if (response.isSuccessful && response.body() != null) {
                    addTrackedAppFromRepo(response.body()!!)
                } else {
                    Log.e(TAG, "Failed to fetch repo $owner/$repoName: ${response.code()} - ${response.message()}")
                    RepoResult.Error("GitHub API Error: ${response.code()} - ${response.message()}", response.code())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network error fetching repo $owner/$repoName", e)
                RepoResult.Error("Network error: ${e.localizedMessage}")
            }
        }
    }


    suspend fun searchGithub(query: String): RepoResult<SearchResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = githubApiService.searchRepositories(query = "$query fork:true")
                if (response.isSuccessful && response.body() != null) {
                    RepoResult.Success(response.body()!!)
                } else {
                    Log.e(TAG, "Search failed for '$query': ${response.code()} - ${response.message()}")
                    RepoResult.Error("Search API Error: ${response.code()} - ${response.message()}", response.code())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network error during search for '$query'", e)
                RepoResult.Error("Network error: ${e.localizedMessage}")
            }
        }
    }

    suspend fun fetchReleases(owner: String, repo: String): RepoResult<List<GithubRelease>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = githubApiService.getReleases(owner, repo)
                if (response.isSuccessful && response.body() != null) {
                    RepoResult.Success(response.body()!!)
                } else {
                    Log.e(TAG, "Failed to fetch releases for $owner/$repo: ${response.code()} - ${response.message()}")
                    RepoResult.Error("API Error: ${response.code()} - ${response.message()}", response.code())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network error fetching releases for $owner/$repo", e)
                RepoResult.Error("Network error: ${e.localizedMessage}")
            }
        }
    }

    suspend fun fetchLatestRelease(owner: String, repo: String): RepoResult<GithubRelease> {
        return withContext(Dispatchers.IO) {
            try {
                val response = githubApiService.getLatestRelease(owner, repo)
                if (response.isSuccessful && response.body() != null) {
                    RepoResult.Success(response.body()!!)
                } else {
                    if (response.code() == 404) {
                        RepoResult.Error("No releases found.", 404)
                    } else {
                        Log.e(TAG, "Failed to fetch latest release for $owner/$repo: ${response.code()} - ${response.message()}")
                        RepoResult.Error("API Error: ${response.code()} - ${response.message()}", response.code())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network error fetching latest release for $owner/$repo", e)
                RepoResult.Error("Network error: ${e.localizedMessage}")
            }
        }
    }

    suspend fun updateInstalledVersion(appId: Long, versionTag: String?) {
        withContext(Dispatchers.IO) {
            trackedAppDao.updateInstalledVersion(appId, versionTag)
            Log.i(TAG, "Updated installed version for app ID $appId to $versionTag")
        }
    }

    suspend fun updateLatestReleaseInfoForApp(app: TrackedAppEntity) {
        withContext(Dispatchers.IO) {
            fetchLatestRelease(app.owner, app.repoName).let { result ->
                val currentTime = System.currentTimeMillis()
                when (result) {
                    is RepoResult.Success -> {
                        val latestRelease = result.data
                        if (app.latestKnownReleaseTag != latestRelease.tagName || app.latestReleaseId != latestRelease.id) {
                            trackedAppDao.updateLatestReleaseInfo(
                                id = app.id,
                                versionTag = latestRelease.tagName,
                                releaseId = latestRelease.id,
                                publishedAt = latestRelease.publishedAt,
                                timestamp = currentTime
                            )
                            Log.i(TAG, "Updated latest release info for ${app.repoName} to ${latestRelease.tagName}")
                        } else {
                            trackedAppDao.updateLatestReleaseInfo(
                                id = app.id, versionTag = app.latestKnownReleaseTag,
                                releaseId = app.latestReleaseId, publishedAt = app.latestReleasePublishedAt,
                                timestamp = currentTime // Just update timestamp
                            )
                        }
                    }
                    is RepoResult.Error -> {
                        if (result.code == 404) {
                            trackedAppDao.updateLatestReleaseInfo(
                                id = app.id, versionTag = null, releaseId = null,
                                publishedAt = null, timestamp = currentTime
                            )
                            Log.w(TAG, "No releases found for ${app.repoName}, cleared latest release info.")
                        } else {
                            Log.e(TAG, "Error updating latest release for ${app.repoName}: ${result.message}")
                            trackedAppDao.updateLatestReleaseInfo( // Update timestamp anyway
                                id = app.id, versionTag = app.latestKnownReleaseTag,
                                releaseId = app.latestReleaseId, publishedAt = app.latestReleasePublishedAt,
                                timestamp = currentTime
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun downloadApk(url: String): RepoResult<ResponseBody> {
        return try {
            val response = githubApiService.downloadFile(url)
            if (response.isSuccessful && response.body() != null) {
                RepoResult.Success(response.body()!!)
            } else {
                Log.e(TAG, "Download failed for URL $url: ${response.code()} - ${response.message()}")
                RepoResult.Error("Download API Error: ${response.code()} - ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error during download from $url", e)
            RepoResult.Error("Download Network error: ${e.localizedMessage}")
        }
    }

    suspend fun deleteTrackedApp(app: TrackedAppEntity) {
        withContext(Dispatchers.IO) {
            trackedAppDao.deleteTrackedApp(app)
            Log.i(TAG, "Deleted app: ${app.owner}/${app.repoName}")
        }
    }

    suspend fun fetchFeaturedApps(): RepoResult<List<FeaturedApp>> {
        return try {
            val featuredApps = featuredAppsApiService.getFeaturedApps()
            RepoResult.Success(featuredApps)
        } catch (e: Exception) {
            // Log the exception e.g. Timber.e(e, "Failed to fetch featured apps")
            RepoResult.Error("Failed to load featured apps. Check connection.") // Or e.localizedMessage
        }
    }

}
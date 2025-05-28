package com.eltonkola.appdepo.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedAppDao {
    @Query("SELECT * FROM tracked_apps ORDER BY repoName ASC")
    fun getAllTrackedApps(): Flow<List<TrackedAppEntity>>

    @Query("SELECT * FROM tracked_apps WHERE id = :id")
    suspend fun getTrackedAppById(id: Long): TrackedAppEntity?

    @Query("SELECT * FROM tracked_apps WHERE owner = :owner AND repoName = :repoName")
    suspend fun getTrackedAppByOwnerAndRepo(owner: String, repoName: String): TrackedAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackedApp(app: TrackedAppEntity): Long

    @Update
    suspend fun updateTrackedApp(app: TrackedAppEntity)

    @Delete
    suspend fun deleteTrackedApp(app: TrackedAppEntity)

    @Query("UPDATE tracked_apps SET installedVersionTag = :versionTag WHERE id = :id")
    suspend fun updateInstalledVersion(id: Long, versionTag: String?)

    @Query(
        "UPDATE tracked_apps SET " +
                "latestKnownReleaseTag = :versionTag, " +
                "latestReleaseId = :releaseId, " +
                "latestReleasePublishedAt = :publishedAt, " +
                "lastCheckedTimestamp = :timestamp " +
                "WHERE id = :id"
    )
    suspend fun updateLatestReleaseInfo(
        id: Long,
        versionTag: String?,
        releaseId: Long?,
        publishedAt: String?,
        timestamp: Long
    )
}
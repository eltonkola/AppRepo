package com.eltonkola.appdepo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracked_apps")
data class TrackedAppEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val owner: String,
    val repoName: String,
    val description: String?,
    val htmlUrl: String?,
    val installedVersionTag: String? = null,
    val latestKnownReleaseTag: String? = null,
    val latestReleaseId: Long? = null,
    val latestReleasePublishedAt: String? = null,
    val lastCheckedTimestamp: Long = System.currentTimeMillis()
)
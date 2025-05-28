package com.eltonkola.appdepo.data.remote.models

import com.google.gson.annotations.SerializedName

data class GithubRelease(
    val id: Long,
    @SerializedName("tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    @SerializedName("published_at") val publishedAt: String,
    val assets: List<GithubAsset>,
    @SerializedName("html_url") val htmlUrl: String,
    val prerelease: Boolean,
    val draft: Boolean
)
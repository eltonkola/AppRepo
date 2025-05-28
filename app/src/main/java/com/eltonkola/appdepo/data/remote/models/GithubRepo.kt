package com.eltonkola.appdepo.data.remote.models

import com.google.gson.annotations.SerializedName

data class GithubRepo(
    val id: Long,
    val name: String,
    @SerializedName("full_name") val fullName: String,
    val owner: GithubOwner,
    val description: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("stargazers_count") val stars: Int?,
    val language: String?
)

data class GithubOwner(
    val login: String,
    @SerializedName("avatar_url") val avatarUrl: String?
)

data class SearchResponse(
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("incomplete_results") val incompleteResults: Boolean,
    val items: List<GithubRepo>
)
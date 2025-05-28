package com.eltonkola.appdepo.data.remote.models

import com.google.gson.annotations.SerializedName

data class GithubAsset(
    val id: Long,
    val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
    @SerializedName("content_type") val contentType: String,
    val size: Long,
    @SerializedName("download_count") val downloadCount: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
) {
    fun isApk(): Boolean = contentType == "application/vnd.android.package-archive" && name.endsWith(".apk", ignoreCase = true)
}
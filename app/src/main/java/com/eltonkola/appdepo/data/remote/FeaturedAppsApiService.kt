package com.eltonkola.appdepo.data.remote

import retrofit2.http.GET
import com.eltonkola.appdepo.ui.viewmodel.FeaturedApp

interface FeaturedAppsApiService {
    @GET("https://github.com/eltonkola/AppRepo/main/tv_featured_apps.json")
    suspend fun getFeaturedApps(): List<FeaturedApp>
}

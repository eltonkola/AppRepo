package com.eltonkola.appdepo.di

import android.content.Context
import androidx.compose.ui.unit.Constraints
import androidx.room.Room
import com.eltonkola.appdepo.data.local.AppDatabase
import com.eltonkola.appdepo.data.local.TrackedAppDao
import com.eltonkola.appdepo.data.remote.GithubApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import com.eltonkola.appdepo.BuildConfig
import com.eltonkola.appdepo.data.remote.FeaturedAppsApiService
import com.eltonkola.appdepo.util.Constants

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) { // Only log in debug builds
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            // TODO: Add an interceptor for GitHub API Token if you have one
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideGithubApiService(okHttpClient: OkHttpClient): GithubApiService {
        return Retrofit.Builder()
            .baseUrl(Constants.GITHUB_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GithubApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideFeaturedAppsApiService(okHttpClient: OkHttpClient): FeaturedAppsApiService {
        return Retrofit.Builder()
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FeaturedAppsApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "appdepo_db"
        ).fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideTrackedAppDao(database: AppDatabase): TrackedAppDao {
        return database.trackedAppDao()
    }
}
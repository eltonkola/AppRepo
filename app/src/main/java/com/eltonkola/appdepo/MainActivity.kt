package com.eltonkola.appdepo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.eltonkola.appdepo.ui.navigation.AppNavigation
import com.eltonkola.appdepo.ui.theme.AppDepoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // Call before super.onCreate()

        super.onCreate(savedInstanceState)
        setContent {
            AppDepoTheme {
                AppNavigation()
            }
        }
    }
}

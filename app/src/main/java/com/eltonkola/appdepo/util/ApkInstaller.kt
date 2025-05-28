package com.eltonkola.appdepo.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.eltonkola.appdepo.BuildConfig
import java.io.File

object ApkInstaller {
    private const val TAG = "ApkInstaller"

    fun installApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file does not exist: ${apkFile.absolutePath}")
            return false
        }

        val apkUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                FileProvider.getUriForFile(
                    context,
                    "${BuildConfig.APPLICATION_ID}.provider", // Matches authority in AndroidManifest
                    apkFile
                )
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Error getting FileProvider URI: ${e.message}", e)
                // Fallback for some devices or configurations if FileProvider fails unexpectedly
                // This is less secure and might not work on newer Android versions.
                // Consider removing this fallback if strict FileProvider usage is required.
                Log.w(TAG, "Falling back to Uri.fromFile - this might not work on Android N+")
                Uri.fromFile(apkFile)
            }
        } else {
            Uri.fromFile(apkFile)
        }

        Log.i(TAG, "Attempting to install APK from URI: $apkUri")

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        // Check if the system can handle this intent
        if (installIntent.resolveActivity(context.packageManager) != null) {
            try {
                context.startActivity(installIntent)
                Log.i(TAG, "APK installation intent started.")
                return true
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException starting APK installation: ${e.message}", e)
                // This can happen if REQUEST_INSTALL_PACKAGES permission is not granted (on Android O+)
                // Or if the source is not trusted.
            } catch (e: Exception) {
                Log.e(TAG, "Exception starting APK installation: ${e.message}", e)
            }
        } else {
            Log.e(TAG, "No activity found to handle APK installation intent.")
        }
        return false
    }
}
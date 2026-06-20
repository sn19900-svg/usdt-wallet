package com.nabil.usdtwallet.data.repository

import android.util.Log
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson

data class GithubRelease(
    @SerializedName("tag_name") val tagName: String = "",
    @SerializedName("html_url") val htmlUrl: String = "",
    @SerializedName("assets") val assets: List<GithubAsset> = emptyList()
)

data class GithubAsset(
    @SerializedName("name") val name: String = "",
    @SerializedName("browser_download_url") val downloadUrl: String = ""
)

data class UpdateInfo(
    val isUpdateAvailable: Boolean,
    val latestVersion: String = "",
    val downloadUrl: String = ""
)

/**
 * يتحقق من وجود إصدار أحدث على GitHub Releases
 * يقارن رقم البناء الحالي (BuildConfig) مع آخر tag منشور
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val REPO_API = "https://api.github.com/repos/sn19900-svg/usdt-wallet/releases/latest"

    suspend fun checkForUpdate(currentBuildNumber: Int): UpdateInfo {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(REPO_API)
                    .addHeader("Accept", "application/vnd.github+json")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.w(TAG, "فشل التحقق من التحديث: ${response.code}")
                    return@withContext UpdateInfo(isUpdateAvailable = false)
                }

                val body = response.body?.string() ?: return@withContext UpdateInfo(isUpdateAvailable = false)
                val release = Gson().fromJson(body, GithubRelease::class.java)

                // tag بصيغة "build-17" → نستخرج الرقم
                val latestBuildNumber = release.tagName.removePrefix("build-").toIntOrNull() ?: 0

                val apkAsset = release.assets.find { it.name.endsWith(".apk") }

                if (latestBuildNumber > currentBuildNumber && apkAsset != null) {
                    UpdateInfo(
                        isUpdateAvailable = true,
                        latestVersion = release.tagName,
                        downloadUrl = apkAsset.downloadUrl
                    )
                } else {
                    UpdateInfo(isUpdateAvailable = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "خطأ في التحقق من التحديث: ${e.message}")
                UpdateInfo(isUpdateAvailable = false)
            }
        }
    }
}

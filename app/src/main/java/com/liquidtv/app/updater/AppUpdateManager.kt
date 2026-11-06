package com.liquidtv.app.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.liquidtv.app.BuildConfig
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val userPreferencesRepository: UserPreferencesRepository,
) {

    /**
     * Checks if a newer version of LIQUID TV is published on GitHub.
     *
     * Queries the GitHub Releases API (`https://api.github.com/repos/{owner}/{repo}/releases/latest`)
     * and falls back to `version.json` if releases are unavailable.
     */
    suspend fun checkForUpdate(repoOverride: String? = null): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val userPrefs = userPreferencesRepository.userPreferences.firstOrNull()
            val rawRepo = repoOverride ?: userPrefs?.githubRepo ?: "manvithgkacharya/LiquidTV"
            val repo = sanitizeRepo(rawRepo)

            if (repo.isBlank() || !repo.contains("/")) {
                return@withContext Result.failure(IllegalArgumentException("Please specify a valid GitHub repository as 'owner/repo'"))
            }

            // 1. Try GitHub Releases API first
            val releaseResult = checkGithubReleaseApi(repo)
            if (releaseResult.isSuccess && releaseResult.getOrNull() != null) {
                val info = releaseResult.getOrNull()!!
                if (isNewer(info.versionName, BuildConfig.VERSION_NAME)) {
                    return@withContext Result.success(info)
                } else {
                    return@withContext Result.success(null) // Already up to date
                }
            }

            // 2. Fallback: try raw version.json from main / master branch
            val jsonResult = checkRawVersionJson(repo)
            if (jsonResult.isSuccess && jsonResult.getOrNull() != null) {
                val info = jsonResult.getOrNull()!!
                if (isNewer(info.versionName, BuildConfig.VERSION_NAME)) {
                    return@withContext Result.success(info)
                } else {
                    return@withContext Result.success(null)
                }
            }

            // If both returned null or failed, propagate message if error
            if (releaseResult.isFailure && jsonResult.isFailure) {
                return@withContext Result.failure(releaseResult.exceptionOrNull() ?: Exception("Failed to check for updates"))
            }

            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun checkGithubReleaseApi(repo: String): Result<AppUpdateInfo?> {
        val url = "https://api.github.com/repos/$repo/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "LiquidTV/${BuildConfig.VERSION_NAME}")
            .build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(Exception("GitHub API returned HTTP ${response.code} for $repo"))
            }

            val body = response.body?.string() ?: return Result.failure(Exception("Empty response body from GitHub"))
            val json = JSONObject(body)

            val rawTag = json.optString("tag_name", "").trim()
            val versionName = rawTag.removePrefix("v").removePrefix("V").trim()
            val releaseTitle = json.optString("name", "Version $versionName")
            val releaseNotes = json.optString("body", "")
            val publishedAt = if (json.has("published_at")) json.optString("published_at") else null

            // Locate .apk asset
            val assets: JSONArray = json.optJSONArray("assets") ?: JSONArray()
            var apkUrl: String? = null
            var apkSize = 0L

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val assetName = asset.optString("name", "")
                if (assetName.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url", "")
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }

            if (apkUrl.isNullOrBlank()) {
                return Result.failure(Exception("Release $versionName found on GitHub, but no .apk asset was uploaded to it"))
            }

            Result.success(
                AppUpdateInfo(
                    versionName = versionName,
                    releaseTitle = releaseTitle,
                    releaseNotes = releaseNotes,
                    apkDownloadUrl = apkUrl,
                    apkSize = apkSize,
                    publishedAt = publishedAt,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun checkRawVersionJson(repo: String): Result<AppUpdateInfo?> {
        val branches = listOf("main", "master")
        for (branch in branches) {
            val url = "https://raw.githubusercontent.com/$repo/$branch/version.json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LiquidTV/${BuildConfig.VERSION_NAME}")
                .build()

            try {
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: continue
                    val json = JSONObject(body)
                    val versionName = json.optString("versionName", "").removePrefix("v").trim()
                    val apkUrl = json.optString("apkUrl", "")
                    if (versionName.isNotBlank() && apkUrl.isNotBlank()) {
                        return Result.success(
                            AppUpdateInfo(
                                versionName = versionName,
                                versionCode = json.optInt("versionCode", 0),
                                releaseTitle = json.optString("releaseTitle", "Version $versionName"),
                                releaseNotes = json.optString("changelog", json.optString("releaseNotes", "")),
                                apkDownloadUrl = apkUrl,
                                apkSize = json.optLong("apkSize", 0L),
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        return Result.failure(Exception("No version.json found in repository $repo"))
    }

    /**
     * Downloads the APK file to the local cache directory with progress reporting.
     */
    suspend fun downloadUpdate(
        updateInfo: AppUpdateInfo,
        onProgress: (Float, Long, Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updateDir = File(context.cacheDir, "updates")
            if (!updateDir.exists()) updateDir.mkdirs()

            val apkFile = File(updateDir, "liquidtv-v${updateInfo.versionName}.apk")
            if (apkFile.exists()) apkFile.delete()

            val request = Request.Builder()
                .url(updateInfo.apkDownloadUrl)
                .header("User-Agent", "LiquidTV/${BuildConfig.VERSION_NAME}")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to download APK: HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty download response body"))
            val totalBytes = if (updateInfo.apkSize > 0) updateInfo.apkSize else body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var downloadedBytes = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val progress = if (totalBytes > 0) {
                            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        onProgress(progress, downloadedBytes, totalBytes)
                    }
                    output.flush()
                }
            }

            if (!apkFile.exists() || apkFile.length() == 0L) {
                return@withContext Result.failure(Exception("Downloaded APK file is empty or missing"))
            }

            Result.success(apkFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Prompts Android's Package Installer to install the downloaded APK.
     */
    fun installUpdate(apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists()) {
                return Result.failure(Exception("Update file not found on disk"))
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(installIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sanitizeRepo(input: String): String {
        return input.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
    }

    /**
     * Returns true if [remoteVersion] is strictly newer than [currentVersion].
     * Supports standard semantic versioning (e.g. "1.0.1" > "1.0.0").
     */
    fun isNewer(remoteVersion: String, currentVersion: String): Boolean {
        try {
            val remoteParts = remoteVersion.split(".", "-").mapNotNull { it.toIntOrNull() }
            val currentParts = currentVersion.split(".", "-").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
        } catch (_: Exception) {}
        return false
    }
}

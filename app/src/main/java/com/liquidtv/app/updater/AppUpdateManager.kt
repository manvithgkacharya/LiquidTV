package com.liquidtv.app.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.liquidtv.app.BuildConfig
import com.liquidtv.app.domain.repository.SonixControlRepository
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
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val sonixControlRepository: SonixControlRepository? = null,
) {

    suspend fun checkForUpdate(repoOverride: String? = null): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            // 1. Check SONIX-CONTROL releases first unless caller explicitly requested a GitHub repo override
            if (repoOverride == null && sonixControlRepository != null) {
                val panelRelease = sonixControlRepository.checkForPanelUpdate().getOrNull()
                if (panelRelease != null) {
                    val downloadUrl = panelRelease.apkUrl.trim().ifBlank { panelRelease.releaseUrl.trim() }
                    val newerByCode = panelRelease.versionCode > BuildConfig.VERSION_CODE
                    val newerByName = isNewer(panelRelease.versionName, BuildConfig.VERSION_NAME)
                    if (downloadUrl.startsWith("https://", ignoreCase = true) && (newerByCode || newerByName)) {
                        val mandatory = panelRelease.updatePolicy.equals("mandatory", ignoreCase = true) ||
                            BuildConfig.VERSION_CODE < panelRelease.minSupportedVersionCode
                        return@withContext Result.success(
                            AppUpdateInfo(
                                versionName = panelRelease.versionName,
                                versionCode = panelRelease.versionCode,
                                releaseTitle = "الإصدار ${panelRelease.versionName}",
                                releaseNotes = panelRelease.releaseNotes,
                                apkDownloadUrl = downloadUrl,
                                apkSize = panelRelease.apkSizeBytes ?: 0L,
                                sha256 = panelRelease.sha256.takeIf { it.isNotBlank() },
                                publishedAt = panelRelease.publishedAt,
                                isMandatory = mandatory,
                                source = "panel",
                            ),
                        )
                    }
                }
            }

            val userPrefs = userPreferencesRepository.userPreferences.firstOrNull()
            val rawRepo = repoOverride ?: userPrefs?.githubRepo ?: DEFAULT_REPO
            val repo = sanitizeRepo(rawRepo)

            if (!isValidRepoFormat(repo)) {
                return@withContext Result.failure(IllegalArgumentException("تعذّر التحقق من إعدادات التحديثات"))
            }

            val releaseResult = checkGithubReleaseApi(repo)
            if (releaseResult.isSuccess && releaseResult.getOrNull() != null) {
                val info = releaseResult.getOrNull()!!
                return@withContext if (isNewer(info.versionName, BuildConfig.VERSION_NAME)) {
                    Result.success(info)
                } else {
                    Result.success(null)
                }
            }

            val jsonResult = checkRawVersionJson(repo)
            if (jsonResult.isSuccess && jsonResult.getOrNull() != null) {
                val info = jsonResult.getOrNull()!!
                val newerByCode = info.versionCode != null && info.versionCode > BuildConfig.VERSION_CODE
                return@withContext if (isNewer(info.versionName, BuildConfig.VERSION_NAME) || newerByCode) {
                    Result.success(info)
                } else {
                    Result.success(null)
                }
            }

            if (releaseResult.isFailure && jsonResult.isFailure) {
                return@withContext Result.failure(releaseResult.exceptionOrNull() ?: Exception("تعذّر التحقق من وجود تحديثات"))
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
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    runCatching { android.util.Log.w("AppUpdateManager", "Release check returned ${response.code}") }
                    return Result.failure(Exception("تعذّر التحقق من توفر تحديثات جديدة حالياً"))
                }

                val body = response.body?.string() ?: return Result.failure(Exception("استجابة التحديثات غير مكتملة"))
                val json = JSONObject(body)

                val rawTag = json.optString("tag_name", "").trim()
                val versionName = rawTag.removePrefix("v").removePrefix("V").trim()
                val releaseTitle = json.optString("name", "Version $versionName")
                val releaseNotes = json.optString("body", "")
                val publishedAt = if (json.has("published_at")) json.optString("published_at") else null

                val assets: JSONArray = json.optJSONArray("assets") ?: JSONArray()
                var apkUrl: String? = null
                var apkSize = 0L

                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        val candidate = asset.optString("browser_download_url", "")
                        if (isTrustedGithubDownloadUrl(candidate)) {
                            apkUrl = candidate
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                    }
                }

                if (apkUrl.isNullOrBlank()) {
                    return Result.failure(Exception("لا تتوفر حزمة تثبيت صالحة لهذا الإصدار حالياً"))
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
            }
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
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val versionName = json.optString("versionName", "").removePrefix("v").removePrefix("V").trim()
                        val apkUrl = json.optString("apkUrl", "").trim()
                        val sha256 = json.optString("sha256", "").trim().takeIf { it.isNotBlank() }
                        if (versionName.isNotBlank() && isTrustedGithubDownloadUrl(apkUrl)) {
                            return Result.success(
                                AppUpdateInfo(
                                    versionName = versionName,
                                    versionCode = json.optInt("versionCode", 0).takeIf { it > 0 },
                                    releaseTitle = json.optString("releaseTitle", "Version $versionName"),
                                    releaseNotes = json.optString("changelog", json.optString("releaseNotes", "")),
                                    apkDownloadUrl = apkUrl,
                                    apkSize = json.optLong("apkSize", 0L),
                                    sha256 = sha256,
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return Result.failure(Exception("لا توجد تحديثات جديدة متاحة حالياً"))
    }

    suspend fun downloadUpdate(
        updateInfo: AppUpdateInfo,
        onProgress: (Float, Long, Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!isTrustedUpdateDownloadUrl(updateInfo, updateInfo.apkDownloadUrl)) {
            return@withContext Result.failure(SecurityException("Blocked untrusted APK download URL"))
        }

        val updateDir = File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }
        val safeVersion = updateInfo.versionName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val tempFile = File(updateDir, "liquidtv-v$safeVersion.apk.tmp")
        val apkFile = File(updateDir, "liquidtv-v$safeVersion.apk")

        try {
            if (tempFile.exists()) tempFile.delete()
            if (apkFile.exists()) apkFile.delete()

            val request = Request.Builder()
                .url(updateInfo.apkDownloadUrl)
                .header("User-Agent", "LiquidTV/${BuildConfig.VERSION_NAME}")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("تعذّر تنزيل ملف التحديث من الخادم"))
                }

                // Verify final redirect host stayed on trusted GitHub infrastructure
                val finalUrl = response.request.url.toString()
                if (!isTrustedUpdateDownloadUrl(updateInfo, finalUrl)) {
                    return@withContext Result.failure(SecurityException("Blocked redirect to untrusted download host"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Empty download response body"))
                val totalBytes = if (updateInfo.apkSize > 0) updateInfo.apkSize else body.contentLength()
                val digest = MessageDigest.getInstance("SHA-256")

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var downloadedBytes = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            digest.update(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            val progress = if (totalBytes > 0) {
                                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            onProgress(progress, downloadedBytes, totalBytes)
                        }
                        output.flush()
                    }
                }

                if (!tempFile.exists() || tempFile.length() == 0L) {
                    tempFile.delete()
                    return@withContext Result.failure(Exception("Downloaded APK file is empty"))
                }

                // Verify optional SHA-256 hash if declared
                updateInfo.sha256?.takeIf { it.isNotBlank() }?.let { expected ->
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!actual.equals(expected.trim(), ignoreCase = true)) {
                        tempFile.delete()
                        return@withContext Result.failure(SecurityException("Downloaded APK checksum mismatch"))
                    }
                }
            }

            // Verify APK package identity, version code, and signing certificate match installed app
            val verifyError = verifyApkFile(tempFile)
            if (verifyError != null) {
                tempFile.delete()
                return@withContext Result.failure(SecurityException(verifyError))
            }

            if (!tempFile.renameTo(apkFile)) {
                tempFile.copyTo(apkFile, overwrite = true)
                tempFile.delete()
            }

            Result.success(apkFile)
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(e)
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyApkFile(apkFile: File): String? {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

        val archiveInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } ?: return "Downloaded file is not a valid Android APK package"

        if (archiveInfo.packageName != context.packageName) {
            return "APK package ID mismatch (${archiveInfo.packageName})"
        }

        val archiveVersionCode = PackageInfoCompat.getLongVersionCode(archiveInfo)
        val installedInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageInfo(context.packageName, flags)
        }
        val installedVersionCode = PackageInfoCompat.getLongVersionCode(installedInfo)
        if (archiveVersionCode < installedVersionCode) {
            return "Update APK version ($archiveVersionCode) is older than installed version ($installedVersionCode)"
        }

        val installedCerts = extractCertDigests(installedInfo)
        val archiveCerts = extractCertDigests(archiveInfo)
        if (installedCerts.isNotEmpty() && (archiveCerts.isEmpty() || archiveCerts.intersect(installedCerts).isEmpty())) {
            return "Update APK signing certificate does not match installed app signature"
        }

        return null
    }

    @Suppress("DEPRECATION")
    private fun extractCertDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
                ?: info.signingInfo?.signingCertificateHistory
        } else {
            info.signatures
        } ?: return emptySet()

        val md = MessageDigest.getInstance("SHA-256")
        return signatures.map { sig ->
            md.reset()
            md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun installUpdate(apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists()) {
                return Result.failure(Exception("ملف التحديث غير موجود على الجهاز"))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
                return Result.failure(IllegalStateException("يرجى السماح بصلاحية تثبيت التطبيقات من مصادر غير معروفة لتطبيق SONIX IPTV ثم اضغط تثبيت مجدداً."))
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

    private fun isValidRepoFormat(repo: String): Boolean {
        return Regex("^[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+$").matches(repo)
    }

    fun isTrustedUpdateDownloadUrl(updateInfo: AppUpdateInfo, rawUrl: String): Boolean {
        if (updateInfo.source == "panel") {
            val uri = try { Uri.parse(rawUrl.trim()) } catch (_: Exception) { return false }
            return uri.scheme?.equals("https", ignoreCase = true) == true && !uri.host.isNullOrBlank()
        }
        return isTrustedGithubDownloadUrl(rawUrl)
    }

    fun isTrustedGithubDownloadUrl(rawUrl: String): Boolean {
        val uri = try {
            Uri.parse(rawUrl.trim())
        } catch (_: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (scheme != "https") return false
        return host == "github.com" ||
            host == "api.github.com" ||
            host == "raw.githubusercontent.com" ||
            host == "objects.githubusercontent.com" ||
            host == "release-assets.githubusercontent.com" ||
            host == "github-releases.githubusercontent.com" ||
            host.endsWith(".githubusercontent.com")
    }

    /**
     * Semantic version comparison supporting core versions and pre-release tags
     * (e.g., "1.1.0" > "1.1.0-rc.2" > "1.1.0-beta.1" > "1.0.2").
     */
    fun isNewer(remoteVersion: String, currentVersion: String): Boolean {
        val remote = parseSemVer(remoteVersion) ?: return false
        val current = parseSemVer(currentVersion) ?: return false

        val maxCore = maxOf(remote.core.size, current.core.size)
        for (i in 0 until maxCore) {
            val r = remote.core.getOrElse(i) { 0 }
            val c = current.core.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }

        // Core versions equal; stable release > prerelease
        if (remote.prerelease.isEmpty() && current.prerelease.isNotEmpty()) return true
        if (remote.prerelease.isNotEmpty() && current.prerelease.isEmpty()) return false
        if (remote.prerelease.isEmpty() && current.prerelease.isEmpty()) return false

        val maxPre = maxOf(remote.prerelease.size, current.prerelease.size)
        for (i in 0 until maxPre) {
            val r = remote.prerelease.getOrNull(i) ?: return false
            val c = current.prerelease.getOrNull(i) ?: return true
            val rNum = r.toIntOrNull()
            val cNum = c.toIntOrNull()
            val cmp = when {
                rNum != null && cNum != null -> rNum.compareTo(cNum)
                rNum != null -> -1 // numeric identifiers have lower precedence than non-numeric
                cNum != null -> 1
                else -> r.compareTo(c, ignoreCase = true)
            }
            if (cmp != 0) return cmp > 0
        }
        return false
    }

    private data class SemVer(
        val core: List<Int>,
        val prerelease: List<String>,
    )

    private fun parseSemVer(raw: String): SemVer? {
        val clean = raw.trim().removePrefix("v").removePrefix("V").substringBefore('+')
        if (clean.isEmpty()) return null
        val dashIndex = clean.indexOf('-')
        val coreStr = if (dashIndex >= 0) clean.substring(0, dashIndex) else clean
        val preStr = if (dashIndex >= 0) clean.substring(dashIndex + 1) else ""

        val core = coreStr.split('.').map { it.toIntOrNull() ?: return null }
        val pre = if (preStr.isBlank()) emptyList() else preStr.split('.', '-').filter { it.isNotBlank() }
        return SemVer(core, pre)
    }

    companion object {
        const val DEFAULT_REPO = "kyanik010/LiquidTV"
    }
}

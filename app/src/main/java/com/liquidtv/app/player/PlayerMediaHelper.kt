package com.liquidtv.app.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Tracks
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import com.liquidtv.app.domain.model.TrackInfo

@OptIn(UnstableApi::class)
object PlayerMediaHelper {

    const val EXTRA_HEADERS_BUNDLE = "liquidtv.extra.headers"

    fun detectMimeType(url: String, explicitExtension: String? = null): String? {
        val ext = explicitExtension?.trim()?.lowercase()?.removePrefix(".")
        val path = try {
            Uri.parse(url).path?.lowercase().orEmpty()
        } catch (_: Exception) {
            url.lowercase().substringBefore('?')
        }

        return when {
            ext == "m3u8" || path.endsWith(".m3u8") || url.contains(".m3u8", ignoreCase = true) ->
                MimeTypes.APPLICATION_M3U8
            ext == "mpd" || path.endsWith(".mpd") || url.contains(".mpd", ignoreCase = true) ->
                MimeTypes.APPLICATION_MPD
            ext == "ism" || path.endsWith(".ism") || path.endsWith(".isml") ->
                MimeTypes.APPLICATION_SS
            ext == "ts" || path.endsWith(".ts") ->
                MimeTypes.VIDEO_MP2T
            ext == "mp4" || ext == "m4v" || path.endsWith(".mp4") || path.endsWith(".m4v") ->
                MimeTypes.VIDEO_MP4
            ext == "mkv" || path.endsWith(".mkv") ->
                MimeTypes.VIDEO_MATROSKA
            ext == "webm" || path.endsWith(".webm") ->
                MimeTypes.VIDEO_WEBM
            else -> null
        }
    }

    fun detectSubtitleMimeType(url: String): String {
        val clean = url.lowercase().substringBefore('?')
        return when {
            clean.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            clean.endsWith(".ass") || clean.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    fun buildMediaItem(
        mediaId: String,
        title: String,
        streamUrl: String,
        logoUrl: String? = null,
        explicitExtension: String? = null,
        headers: Map<String, String> = emptyMap(),
        externalSubtitleUrl: String? = null,
        externalSubtitleLang: String = "und",
    ): MediaItem {
        val mimeType = detectMimeType(streamUrl, explicitExtension)
        val headersBundle = Bundle().apply {
            headers.forEach { (k, v) -> putString(k, v) }
        }
        val extras = Bundle().apply {
            if (headers.isNotEmpty()) {
                putBundle(EXTRA_HEADERS_BUNDLE, headersBundle)
            }
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .apply {
                logoUrl?.takeIf { it.isNotBlank() }?.let {
                    runCatching { Uri.parse(it) }.getOrNull()?.let(::setArtworkUri)
                }
            }
            .build()

        val requestMetadata = MediaItem.RequestMetadata.Builder()
            .setMediaUri(Uri.parse(streamUrl))
            .setExtras(extras)
            .build()

        val builder = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(streamUrl)
            .setMediaMetadata(metadata)
            .setRequestMetadata(requestMetadata)

        mimeType?.let { builder.setMimeType(it) }

        if (!externalSubtitleUrl.isNullOrBlank()) {
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(externalSubtitleUrl))
                .setMimeType(detectSubtitleMimeType(externalSubtitleUrl))
                .setLanguage(externalSubtitleLang)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .setLabel("ترجمة خارجية")
                .build()
            builder.setSubtitleConfigurations(listOf(subtitleConfig))
        }

        return builder.build()
    }

    fun extractHeaders(item: MediaItem): Map<String, String> {
        val extras = item.requestMetadata.extras ?: return emptyMap()
        val bundle = extras.getBundle(EXTRA_HEADERS_BUNDLE) ?: return emptyMap()
        val map = linkedMapOf<String, String>()
        for (key in bundle.keySet()) {
            val value = bundle.getString(key)
            if (!value.isNullOrBlank()) {
                map[key] = value
            }
        }
        return map
    }

    fun extractTracks(tracks: Tracks, trackType: Int): List<TrackInfo> {
        val result = mutableListOf<TrackInfo>()
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != trackType) return@forEachIndexed
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val format = group.getTrackFormat(i)
                val lang = format.language?.takeIf { it.isNotBlank() && it != "und" }
                val baseLabel = format.label?.takeIf { it.isNotBlank() }
                    ?: lang?.uppercase()
                    ?: if (trackType == C.TRACK_TYPE_AUDIO) "صوت ${result.size + 1}" else "ترجمة ${result.size + 1}"
                val channelInfo = if (trackType == C.TRACK_TYPE_AUDIO && format.channelCount > 0) {
                    " (${format.channelCount} قناة)"
                } else ""
                result.add(
                    TrackInfo(
                        groupIndex = groupIndex,
                        trackIndex = i,
                        label = "$baseLabel$channelInfo",
                        language = lang,
                        isSelected = group.isTrackSelected(i),
                        bitrate = format.bitrate,
                        height = format.height,
                    )
                )
            }
        }
        return result
    }

    fun isPermanentError(error: PlaybackException): Boolean {
        val cause = error.cause
        if (cause is HttpDataSource.InvalidResponseCodeException) {
            if (cause.responseCode in listOf(401, 403, 404, 410, 451)) {
                return true
            }
        }
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> true
            else -> false
        }
    }

    fun formatPlaybackError(error: PlaybackException): String {
        val cause = error.cause
        return when {
            cause is HttpDataSource.InvalidResponseCodeException -> {
                when (cause.responseCode) {
                    401, 403 -> "تم رفض الوصول للبث. تحقق من اشتراكك أو عدد الأجهزة المتصلة."
                    404, 410 -> "البث غير متوفر على الخادم حالياً."
                    else -> "تعذّر تشغيل البث من الخادم حالياً، يرجى المحاولة لاحقاً."
                }
            }
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                "تعذّر الوصول إلى خادم البث. تحقق من اتصال الإنترنت أو رابط الاشتراك."
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "انتهت مهلة الاتصال أثناء تحميل البث."
            error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ->
                "تأخر البث المباشر؛ جارٍ إعادة المزامنة."
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
                "صيغة البث غير مدعومة أو استجابة غير صالحة."
            else -> "البث غير متاح مؤقتاً، يرجى المحاولة لاحقاً."
        }
    }

    fun backoffDelayMs(attempt: Int): Long {
        val safeAttempt = attempt.coerceIn(1, 6)
        return (1_500L * (1L shl (safeAttempt - 1))).coerceAtMost(10_000L)
    }
}

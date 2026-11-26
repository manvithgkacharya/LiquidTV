package com.liquidtv.app.ui.utils

import com.liquidtv.app.domain.model.EpisodeItem
import com.liquidtv.app.domain.model.SeasonGroup
import com.liquidtv.app.domain.model.SubscriptionStatus
import com.liquidtv.app.data.sonix.SonixAccessStatus
import com.liquidtv.app.domain.model.SubscriptionOrigin
import com.liquidtv.app.domain.model.SubscriptionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun sortSeasons(seasons: List<SeasonGroup>): List<SeasonGroup> =
    seasons.sortedBy { it.seasonNumber }

fun formatSeasonTitleAr(seasonNumber: Int): String =
    if (seasonNumber == 0) "حلقات خاصة" else "الموسم $seasonNumber"

fun formatEpisodeCode(seasonNumber: Int, episodeNumber: Int): String? {
    if (seasonNumber < 0 || episodeNumber <= 0) return null
    return String.format(Locale.US, "S%02dE%02d", seasonNumber, episodeNumber)
}

fun calculateEpisodeProgress(resumePositionMs: Long, durationSec: Int?): Float? {
    if (durationSec == null || durationSec <= 0 || resumePositionMs <= 0L) return null
    val durationMs = durationSec.toLong() * 1000L
    if (durationMs <= 0L) return null
    return (resumePositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

fun findContinueWatchingEpisode(episodes: List<EpisodeItem>): EpisodeItem? {
    if (episodes.isEmpty()) return null
    val withTimestamp = episodes
        .filter { it.lastWatchedAt != null && (it.resumePositionMs > 0L || it.lastWatchedAt > 0L) }
        .maxByOrNull { it.lastWatchedAt ?: 0L }
    if (withTimestamp != null) return withTimestamp
    return episodes.firstOrNull { it.resumePositionMs > 0L }
}

fun formatDurationMinutesAr(durationSec: Int?): String? {
    if (durationSec == null || durationSec <= 0) return null
    val minutes = (durationSec / 60).coerceAtLeast(1)
    return "$minutes د"
}

fun formatResumeTimeAr(resumePositionMs: Long): String? {
    if (resumePositionMs <= 0L) return null
    val totalSec = (resumePositionMs / 1000L).toInt()
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatEpgTimeRange(startMs: Long, endMs: Long): String {
    val fmt = SimpleDateFormat("HH:mm", Locale.US)
    return "${fmt.format(Date(startMs))} - ${fmt.format(Date(endMs))}"
}

fun calculateEpgProgress(startMs: Long, endMs: Long, nowMs: Long = System.currentTimeMillis()): Float {
    val total = (endMs - startMs).coerceAtLeast(1L)
    return ((nowMs - startMs).toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

fun localizedQualityAr(raw: String): String = when (raw) {
    "Auto" -> "تلقائي"
    "Data Saver" -> "توفير البيانات"
    "1080p" -> "1080p فائق"
    "720p" -> "720p عالي"
    "480p" -> "480p قياسي"
    else -> raw
}

fun localizedThemeAr(raw: String): String = when (raw) {
    "midnight_glass" -> "زجاج منتصف الليل"
    "aurora" -> "الشفق القطبي"
    "ocean" -> "أزرق المحيط"
    "cosmic" -> "الفضاء البنفسجي"
    "minimal" -> "داكن هادئ"
    else -> raw
}

fun localizedSubtitleLangAr(raw: String): String = when (raw) {
    "ar" -> "العربية"
    "en" -> "الإنجليزية"
    "fr" -> "الفرنسية"
    "es" -> "الإسبانية"
    "de" -> "الألمانية"
    "tr" -> "التركية"
    else -> raw
}

fun localizedSubscriptionStatusAr(status: SubscriptionStatus): String = when (status) {
    SubscriptionStatus.CONNECTED -> "متصل"
    SubscriptionStatus.ERROR -> "خطأ في الاتصال"
    SubscriptionStatus.EXPIRED -> "منتهي الصلاحية"
    SubscriptionStatus.DISCONNECTED -> "غير متصل"
}

fun localizedSubscriptionTypeAr(type: SubscriptionType): String = when (type) {
    SubscriptionType.XTREAM -> "سيرفر بث"
}

fun localizedSubscriptionOriginAr(origin: SubscriptionOrigin): String = when (origin) {
    SubscriptionOrigin.NORMAL_URL -> "URL عادي"
    SubscriptionOrigin.PANEL_SERVER -> "سيرفر مخصص"
}

fun localizedSonixAccessStatusAr(status: SonixAccessStatus): String = when (status) {
    SonixAccessStatus.ACTIVE -> "مفعّل"
    SonixAccessStatus.EXPIRED -> "منتهي الصلاحية"
    SonixAccessStatus.BLOCKED -> "محظور"
    SonixAccessStatus.REVOKED -> "ملغي الصلاحية"
    SonixAccessStatus.UNKNOWN -> "بانتظار المزامنة"
}

fun formatIsoDateShortAr(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val rawDate = iso.substringBefore("T").takeIf { it.length == 10 } ?: return iso
    return rawDate
}

package com.liquidtv.app.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

interface IptvApiService {
    @GET
    @Streaming
    suspend fun fetchPlaylist(@Url url: String): ResponseBody
}

object PlaylistUrls {
    const val INDEX_ALL = "https://iptv-org.github.io/iptv/index.m3u"
    const val INDEX_CATEGORY = "https://iptv-org.github.io/iptv/index.category.m3u"
    const val INDEX_LANGUAGE = "https://iptv-org.github.io/iptv/index.language.m3u"
    const val INDEX_COUNTRY = "https://iptv-org.github.io/iptv/index.country.m3u"
    const val INDEX_REGION = "https://iptv-org.github.io/iptv/index.region.m3u"

    val ALL_PLAYLISTS = listOf(
        PlaylistSource(INDEX_ALL, "All Channels"),
    )
}

data class PlaylistSource(val url: String, val name: String)


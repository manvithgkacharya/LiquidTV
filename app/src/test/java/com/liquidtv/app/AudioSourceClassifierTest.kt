package com.liquidtv.app

import com.liquidtv.app.data.audio.AudioSourceClassifier
import com.liquidtv.app.domain.model.AudioBrandFilter
import com.liquidtv.app.domain.model.AudioChannelBrand
import com.liquidtv.app.domain.model.ExternalAudioChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSourceClassifierTest {

    @Test
    fun `classifies beIN SPORTS and ALWAN variants while excluding VOD and series`() {
        assertEquals(AudioChannelBrand.BEIN_SPORTS, AudioSourceClassifier.classify("beIN SPORTS 1 HD", "Sport"))
        assertEquals(AudioChannelBrand.BEIN_SPORTS, AudioSourceClassifier.classify("بي أن سبورت 2 FHD", "رياضة"))
        assertEquals(AudioChannelBrand.ALWAN, AudioSourceClassifier.classify("ALWAN 1 HD", "Sports"))
        assertEquals(AudioChannelBrand.ALWAN, AudioSourceClassifier.classify("ألوان سبورت 3", "قنوات ألوان"))

        // Reject movies/series even if brand appears
        assertEquals(AudioChannelBrand.OTHER, AudioSourceClassifier.classify("فيلم وثائقي beIN SPORTS", "أفلام"))
        assertEquals(AudioChannelBrand.OTHER, AudioSourceClassifier.classify("beIN SPORTS S01E02", "Sports"))
        assertEquals(AudioChannelBrand.OTHER, AudioSourceClassifier.classify("مسلسل ألوان", "مسلسلات عربية"))
        assertEquals(AudioChannelBrand.OTHER, AudioSourceClassifier.classify("SSC 1 HD", "Saudi Sports"))
        assertFalse(AudioSourceClassifier.isAudioCandidate("MBC 1 HD", "General"))
        assertTrue(AudioSourceClassifier.isAudioCandidate("beIN Sport Premium 1", null))
    }

    @Test
    fun `ranks quality ascending and sorts channels deterministically`() {
        val sd = ExternalAudioChannel("1", "beIN SPORTS 2 SD", "2", "http://ex/1.m3u8", AudioChannelBrand.BEIN_SPORTS, "beIN", qualityRank = AudioSourceClassifier.qualityRank("beIN SPORTS 2 SD"), qualityLabel = "SD")
        val fhd = ExternalAudioChannel("2", "beIN SPORTS 1 FHD", "1", "http://ex/2.m3u8", AudioChannelBrand.BEIN_SPORTS, "beIN", qualityRank = AudioSourceClassifier.qualityRank("beIN SPORTS 1 FHD"), qualityLabel = "FHD")
        val hd = ExternalAudioChannel("3", "beIN SPORTS 1 HD", "1", "http://ex/3.m3u8", AudioChannelBrand.BEIN_SPORTS, "beIN", qualityRank = AudioSourceClassifier.qualityRank("beIN SPORTS 1 HD"), qualityLabel = "HD")
        val unknown = ExternalAudioChannel("4", "beIN SPORTS 3", "3", "http://ex/4.m3u8", AudioChannelBrand.BEIN_SPORTS, "beIN", qualityRank = AudioSourceClassifier.qualityRank("beIN SPORTS 3"), qualityLabel = "")
        val alwan = ExternalAudioChannel("5", "ALWAN 1 HD", "4", "http://ex/5.m3u8", AudioChannelBrand.ALWAN, "ALWAN", qualityRank = AudioSourceClassifier.qualityRank("ALWAN 1 HD"), qualityLabel = "HD")

        val sorted = AudioSourceClassifier.sortChannels(listOf(alwan, unknown, fhd, hd, sd))
        assertEquals(listOf("1", "3", "2", "4", "5"), sorted.map { it.id })

        val filteredAlwan = AudioSourceClassifier.filterChannels(sorted, AudioBrandFilter.ALWAN, "ألوان")
        assertEquals(listOf("5"), filteredAlwan.map { it.id })
    }
}

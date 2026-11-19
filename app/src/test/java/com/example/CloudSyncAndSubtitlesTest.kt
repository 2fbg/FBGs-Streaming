package com.example

import com.example.data.service.CloudSyncManager
import com.example.data.service.CloudSyncPayload
import com.example.data.service.SubtitleAndSkipManager
import com.example.data.service.SyncContinueItem
import com.example.data.service.SyncFavoriteItem
import com.example.data.service.VodSkipPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CloudSyncAndSubtitlesTest {

    @Test
    fun syncCodeNormalizationAndGenerationMatchFormat() {
        assertEquals("MK21-AB12-CD34", CloudSyncManager.normalizeSyncCode("ab12-cd34"))
        assertEquals("MK21-AB12-CD34", CloudSyncManager.normalizeSyncCode("mk21ab12cd34"))
        assertEquals("", CloudSyncManager.normalizeSyncCode("1234"))
        assertTrue(CloudSyncManager.isValidSyncCode("MK21-9A8B-7C6D"))
        assertFalse(CloudSyncManager.isValidSyncCode("INVALID"))

        val generated = CloudSyncManager.generateSyncCode()
        assertTrue(CloudSyncManager.isValidSyncCode(generated))
    }

    @Test
    fun cloudSyncMergeAppliesLastWriteWinsAndRoundTripsJson() {
        val local = CloudSyncPayload(
            favorites = listOf(
                SyncFavoriteItem(key = "http://srv/1", name = "Globo", url = "http://srv/1", deleted = true, updatedAt = 5000L),
                SyncFavoriteItem(key = "http://srv/2", name = "ESPN", url = "http://srv/2", deleted = false, updatedAt = 2000L)
            ),
            continueWatching = listOf(
                SyncContinueItem(key = "http://srv/m1", name = "Matrix", url = "http://srv/m1", positionSec = 1400, durationSec = 7200, updatedAt = 6000L)
            ),
            updatedAt = 6000L
        )
        val remote = CloudSyncPayload(
            favorites = listOf(
                SyncFavoriteItem(key = "http://srv/1", name = "Globo", url = "http://srv/1", deleted = false, updatedAt = 3000L),
                SyncFavoriteItem(key = "http://srv/2", name = "ESPN", url = "http://srv/2", deleted = true, updatedAt = 4000L)
            ),
            continueWatching = listOf(
                SyncContinueItem(key = "http://srv/m1", name = "Matrix", url = "http://srv/m1", positionSec = 500, durationSec = 7200, updatedAt = 4000L)
            ),
            updatedAt = 4000L
        )

        val merged = CloudSyncManager.mergePayloads(local, remote, nowMs = 7000L)
        val favByKey = merged.favorites.associateBy { it.key }
        assertTrue(favByKey["http://srv/1"]!!.deleted)
        assertTrue(favByKey["http://srv/2"]!!.deleted)
        assertEquals(1400, merged.continueWatching.first().positionSec)

        val reqJson = CloudSyncManager.buildRequestJson("MK21-ABCD-1234", merged)
        assertTrue(reqJson.contains("\"code\":\"MK21-ABCD-1234\""))

        val parsed = CloudSyncManager.parseResponseJson(
            """{"ok":true,"code":"MK21-ABCD-1234","updatedAt":7000,"payload":{"favorites":[{"key":"http://srv/3","name":"SBT","url":"http://srv/3","deleted":false,"updatedAt":7000}],"continueWatching":[{"key":"http://srv/m2","name":"Duna","url":"http://srv/m2","positionSec":900,"durationSec":9000,"updatedAt":7000}]}}"""
        )
        assertTrue(parsed.ok)
        assertEquals("MK21-ABCD-1234", parsed.code)
        assertEquals("SBT", parsed.payload?.favorites?.first()?.name)
        assertEquals(900, parsed.payload?.continueWatching?.first()?.positionSec)
    }

    @Test
    fun subtitlesSrtToVttAndShiftWorkAccurately() {
        val srt = "1\n00:00:01,500 --> 00:00:04,000\nOlá!\n\n2\n00:00:10,000 --> 00:00:12,000\nTeste."
        val vtt = SubtitleAndSkipManager.srtToVtt(srt)
        assertTrue(vtt.startsWith("WEBVTT\n"))
        assertTrue(vtt.contains("00:00:01.500 --> 00:00:04.000"))

        val shiftedPlus = SubtitleAndSkipManager.shiftVttTimestamps(srt, 0.5)
        assertTrue(shiftedPlus.contains("00:00:02.000 --> 00:00:04.500"))
        assertTrue(shiftedPlus.contains("00:00:10.500 --> 00:00:12.500"))

        val shiftedMinus = SubtitleAndSkipManager.shiftVttTimestamps(srt, -2.0)
        assertTrue(shiftedMinus.contains("00:00:00.000 --> 00:00:02.000"))

        assertEquals("Breaking Bad S01E02", SubtitleAndSkipManager.buildSubtitleQuery("[4K] Breaking Bad S01E02 (2008) DUB"))
        assertEquals("+0.5s", SubtitleAndSkipManager.formatOffsetLabel(0.5))
        assertEquals("-1.0s", SubtitleAndSkipManager.formatOffsetLabel(-1.0))
        assertEquals("0.0s", SubtitleAndSkipManager.formatOffsetLabel(0.0))
    }

    @Test
    fun detectSkipPromptIdentifiesIntroCreditsAndIgnoresLive() {
        assertNull(SubtitleAndSkipManager.detectSkipPrompt(20_000L, 3_600_000L, "LIVE"))
        assertNull(SubtitleAndSkipManager.detectSkipPrompt(20_000L, 300_000L, "MOVIE"))

        val intro = SubtitleAndSkipManager.detectSkipPrompt(25_000L, 2_700_000L, "SERIES")
        assertNotNull(intro)
        assertTrue(intro is VodSkipPrompt.SkipIntro)
        assertEquals("Pular Abertura", intro!!.label)
        assertEquals(95_000L, intro.targetMs)

        val credits = SubtitleAndSkipManager.detectSkipPrompt(7_100_000L, 7_200_000L, "MOVIE")
        assertNotNull(credits)
        assertTrue(credits is VodSkipPrompt.SkipCredits)
        assertEquals("Pular Créditos", credits!!.label)
    }
}

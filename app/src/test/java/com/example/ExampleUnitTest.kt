package com.example

import com.example.data.SubtitleRepository
import com.example.model.SubtitleConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testMultiSentenceState() {
        SubtitleRepository.clearSentences()
        assertEquals(0, SubtitleRepository.subtitleState.value.recentSentences.size)

        SubtitleRepository.addSentence("こんにちは", "สวัสดีครับ")
        assertEquals(1, SubtitleRepository.subtitleState.value.recentSentences.size)
        assertEquals("こんにちは", SubtitleRepository.subtitleState.value.recentSentences.first().japaneseText)
        assertEquals("สวัสดีครับ", SubtitleRepository.subtitleState.value.recentSentences.first().thaiText)

        SubtitleRepository.updateConfig {
            it.copy(fontSizeSp = 22f, showOriginalJapanese = false)
        }
        assertEquals(22f, SubtitleRepository.subtitleConfig.value.fontSizeSp, 0.01f)
        assertEquals(false, SubtitleRepository.subtitleConfig.value.showOriginalJapanese)
    }

    @Test
    fun testDefaultSubtitleConfig() {
        val config = SubtitleConfig()
        assertTrue(config.fontSizeSp >= 12f)
        assertTrue(config.backgroundOpacity > 0f)
        assertTrue(config.showThaiTranslation)
    }
}

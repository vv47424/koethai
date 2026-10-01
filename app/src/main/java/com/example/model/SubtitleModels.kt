package com.example.model

/**
 * Represents a completed, grammatically coherent bilingual subtitle sentence.
 */
data class SubtitleSentence(
    val id: Long = System.currentTimeMillis(),
    val japaneseText: String,
    val thaiText: String
)

/**
 * Real-time state of the Japanese-to-Thai subtitle translation engine.
 */
data class SubtitleState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val currentInterimJapanese: String = "",
    val recentSentences: List<SubtitleSentence> = emptyList(),
    val isTranslating: Boolean = false,
    val audioLevel: Float = 0f, // 0.0f to 1.0f for audio meter
    val isVoskModelReady: Boolean = false,
    val isMlKitModelReady: Boolean = false,
    val statusMessage: String = "Ready to start"
)

/**
 * Visual styling preferences for the floating subtitle window.
 */
data class SubtitleConfig(
    val fontSizeSp: Float = 18f,
    val backgroundOpacity: Float = 0.88f,
    val textColorHex: Long = 0xFFFFFFFF,
    val thaiAccentColorHex: Long = 0xFFFCD34D, // High-contrast amber/gold
    val showOriginalJapanese: Boolean = true,
    val showThaiTranslation: Boolean = true,
    val isCompactMode: Boolean = false
)

/**
 * Status of offline translation models.
 */
data class ModelStatus(
    val isJapaneseModelDownloaded: Boolean = false,
    val isThaiModelDownloaded: Boolean = false,
    val isDownloadingJapanese: Boolean = false,
    val isDownloadingThai: Boolean = false,
    val isVoskModelInstalled: Boolean = false,
    val voskModelPath: String = "",
    val statusDetail: String = ""
)

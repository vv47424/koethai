package com.example.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.example.model.ModelStatus
import com.example.model.SubtitleConfig
import com.example.model.SubtitleSentence
import com.example.model.SubtitleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Central repository managing shared application state between
 * MainActivity and FloatingSubtitleService without external cloud storage.
 */
object SubtitleRepository {

    private const val PREFS_NAME = "koethai_subtitle_prefs"
    private const val KEY_FONT_SIZE = "font_size"
    private const val KEY_OPACITY = "opacity"
    private const val KEY_SHOW_JA = "show_ja"
    private const val KEY_SHOW_TH = "show_th"
    private const val KEY_THAI_COLOR = "thai_color"

    private val _subtitleState = MutableStateFlow(SubtitleState())
    val subtitleState: StateFlow<SubtitleState> = _subtitleState.asStateFlow()

    private val _subtitleConfig = MutableStateFlow(SubtitleConfig())
    val subtitleConfig: StateFlow<SubtitleConfig> = _subtitleConfig.asStateFlow()

    private val _modelStatus = MutableStateFlow(ModelStatus())
    val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()

    // Holds MediaProjection permission result across activity and service launch
    var mediaProjectionResultCode: Int = 0
    var mediaProjectionResultData: Intent? = null

    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedFontSize = prefs?.getFloat(KEY_FONT_SIZE, 18f) ?: 18f
            val savedOpacity = prefs?.getFloat(KEY_OPACITY, 0.88f) ?: 0.88f
            val savedShowJa = prefs?.getBoolean(KEY_SHOW_JA, true) ?: true
            val savedShowTh = prefs?.getBoolean(KEY_SHOW_TH, true) ?: true
            val savedThaiColor = prefs?.getLong(KEY_THAI_COLOR, 0xFFFCD34DL) ?: 0xFFFCD34DL

            _subtitleConfig.value = SubtitleConfig(
                fontSizeSp = savedFontSize,
                backgroundOpacity = savedOpacity,
                showOriginalJapanese = savedShowJa,
                showThaiTranslation = savedShowTh,
                thaiAccentColorHex = savedThaiColor
            )
        }
    }

    fun updateState(transform: (SubtitleState) -> SubtitleState) {
        _subtitleState.update(transform)
    }

    fun updateConfig(transform: (SubtitleConfig) -> SubtitleConfig) {
        val updated = transform(_subtitleConfig.value)
        _subtitleConfig.value = updated
        prefs?.edit()?.apply {
            putFloat(KEY_FONT_SIZE, updated.fontSizeSp)
            putFloat(KEY_OPACITY, updated.backgroundOpacity)
            putBoolean(KEY_SHOW_JA, updated.showOriginalJapanese)
            putBoolean(KEY_SHOW_TH, updated.showThaiTranslation)
            putLong(KEY_THAI_COLOR, updated.thaiAccentColorHex)
            apply()
        }
    }

    fun updateModelStatus(transform: (ModelStatus) -> ModelStatus) {
        _modelStatus.update(transform)
    }

    /**
     * Appends a new completed sentence to the rolling multi-sentence subtitle window.
     */
    fun addSentence(japanese: String, thai: String) {
        val cleanJa = japanese.trim()
        val cleanTh = thai.trim()
        if (cleanJa.isBlank() && cleanTh.isBlank()) return

        val sentence = SubtitleSentence(
            japaneseText = cleanJa,
            thaiText = cleanTh
        )

        _subtitleState.update { current ->
            // Keep the last 15 sentences in the active scrollable window
            val updated = (current.recentSentences + sentence).takeLast(15)
            current.copy(
                recentSentences = updated,
                currentInterimJapanese = ""
            )
        }
    }

    /**
     * Clears all active sentences in the floating subtitle view.
     */
    fun clearSentences() {
        _subtitleState.update { it.copy(recentSentences = emptyList(), currentInterimJapanese = "") }
    }
}

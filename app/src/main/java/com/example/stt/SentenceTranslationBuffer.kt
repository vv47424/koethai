package com.example.stt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Intelligent Speech Buffering & Endpointing engine for Japanese.
 *
 * Instead of translating raw fragmented speech chunks immediately, this buffer
 * waits for full grammatical sentences (detecting punctuation, sentence-ending particles,
 * or natural acoustic pauses) before dispatching to Google ML Kit. This yields
 * fluent, grammatically accurate Thai translations.
 */
class SentenceTranslationBuffer(
    private val scope: CoroutineScope,
    private val pauseThresholdMs: Long = 950L,
    private val onSentenceReady: (String) -> Unit,
    private val onInterimUpdate: (String) -> Unit
) {

    private val accumulatedText = StringBuilder()
    private var silenceDebounceJob: Job? = null

    // Common Japanese sentence-ending patterns
    private val sentenceTerminators = listOf("。", "！", "？", "!", "?")
    private val sentenceEndingParticles = listOf(
        "です", "ます", "でした", "ました", "ですね", "ですよ",
        "だね", "だよ", "なんだ", "から", "けど", "もん",
        "でしょう", "だろう", "ください", "ね", "よ", "か"
    )

    /**
     * Receives in-progress partial speech recognition events.
     */
    fun onPartial(partial: String) {
        val clean = partial.trim()
        if (clean.isBlank()) return

        val display = if (accumulatedText.isNotEmpty()) {
            "$accumulatedText $clean"
        } else {
            clean
        }
        onInterimUpdate(display)

        // Reset debounce timer on ongoing speech
        silenceDebounceJob?.cancel()
    }

    /**
     * Receives completed phrase/clause chunks from the speech recognizer.
     */
    fun onSpeechSegment(segment: String) {
        val clean = segment.trim()
        if (clean.isBlank()) return

        if (accumulatedText.isNotEmpty()) {
            accumulatedText.append(" ")
        }
        accumulatedText.append(clean)

        val currentFull = accumulatedText.toString().trim()
        onInterimUpdate(currentFull)

        // Check for immediate explicit punctuation endpoint
        if (sentenceTerminators.any { currentFull.endsWith(it) }) {
            flushBuffer()
            return
        }

        // Check for common Japanese sentence-ending particles
        val endsWithParticle = sentenceEndingParticles.any { particle ->
            currentFull.endsWith(particle)
        }

        // Schedule sentence boundary flush on natural pause
        silenceDebounceJob?.cancel()
        val delayTime = if (endsWithParticle) (pauseThresholdMs * 0.7).toLong() else pauseThresholdMs
        silenceDebounceJob = scope.launch(Dispatchers.Main) {
            delay(delayTime)
            flushBuffer()
        }
    }

    /**
     * Dispatches the accumulated sentence to the translator and clears the buffer.
     */
    fun flushBuffer() {
        silenceDebounceJob?.cancel()
        val sentence = accumulatedText.toString().trim()
        if (sentence.isNotEmpty() && sentence.length >= 2) {
            onSentenceReady(sentence)
        }
        accumulatedText.clear()
        onInterimUpdate("")
    }

    fun reset() {
        silenceDebounceJob?.cancel()
        accumulatedText.clear()
        onInterimUpdate("")
    }
}

package com.example.translation

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.example.data.SubtitleRepository
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Handles completely offline Japanese to Thai translation using Google ML Kit On-Device Translation.
 */
class OfflineTranslationManager(private val context: Context) {

    companion object {
        private const val TAG = "KoeThai_Translator"
    }

    private var translator: Translator? = null
    private val modelManager = RemoteModelManager.getInstance()
    private val japaneseModel = TranslateRemoteModel.Builder(TranslateLanguage.JAPANESE).build()
    private val thaiModel = TranslateRemoteModel.Builder(TranslateLanguage.THAI).build()

    // LRU Cache for translated sentences
    private val translationCache = LruCache<String, String>(300)

    init {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.JAPANESE)
            .setTargetLanguage(TranslateLanguage.THAI)
            .build()
        translator = Translation.getClient(options)
    }

    fun checkModelsStatus(onResult: (jaDownloaded: Boolean, thDownloaded: Boolean) -> Unit) {
        modelManager.isModelDownloaded(japaneseModel)
            .addOnSuccessListener { jaDownloaded ->
                modelManager.isModelDownloaded(thaiModel)
                    .addOnSuccessListener { thDownloaded ->
                        SubtitleRepository.updateModelStatus {
                            it.copy(
                                isJapaneseModelDownloaded = jaDownloaded,
                                isThaiModelDownloaded = thDownloaded,
                                statusDetail = if (jaDownloaded && thDownloaded) {
                                    "Both Japanese and Thai offline models are ready"
                                } else {
                                    "Download models once for offline translation"
                                }
                            )
                        }
                        onResult(jaDownloaded, thDownloaded)
                    }
                    .addOnFailureListener {
                        onResult(jaDownloaded, false)
                    }
            }
            .addOnFailureListener {
                onResult(false, false)
            }
    }

    fun downloadModel(language: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val model = if (language == TranslateLanguage.JAPANESE) japaneseModel else thaiModel
        val conditions = DownloadConditions.Builder().build()

        if (language == TranslateLanguage.JAPANESE) {
            SubtitleRepository.updateModelStatus { it.copy(isDownloadingJapanese = true) }
        } else {
            SubtitleRepository.updateModelStatus { it.copy(isDownloadingThai = true) }
        }

        modelManager.download(model, conditions)
            .addOnSuccessListener {
                if (language == TranslateLanguage.JAPANESE) {
                    SubtitleRepository.updateModelStatus {
                        it.copy(
                            isDownloadingJapanese = false,
                            isJapaneseModelDownloaded = true,
                            statusDetail = "Japanese offline model downloaded"
                        )
                    }
                } else {
                    SubtitleRepository.updateModelStatus {
                        it.copy(
                            isDownloadingThai = false,
                            isThaiModelDownloaded = true,
                            statusDetail = "Thai offline model downloaded"
                        )
                    }
                }
                onSuccess()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error downloading model for $language", exception)
                if (language == TranslateLanguage.JAPANESE) {
                    SubtitleRepository.updateModelStatus {
                        it.copy(
                            isDownloadingJapanese = false,
                            statusDetail = "Failed to download Japanese model"
                        )
                    }
                } else {
                    SubtitleRepository.updateModelStatus {
                        it.copy(
                            isDownloadingThai = false,
                            statusDetail = "Failed to download Thai model"
                        )
                    }
                }
                onFailure(exception)
            }
    }

    fun prepareTranslator(onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val conditions = DownloadConditions.Builder().build()
        translator?.downloadModelIfNeeded(conditions)
            ?.addOnSuccessListener {
                SubtitleRepository.updateState { it.copy(isMlKitModelReady = true) }
                onSuccess()
            }
            ?.addOnFailureListener { e ->
                Log.e(TAG, "Model preparation error", e)
                onFailure(e)
            }
    }

    suspend fun translate(japaneseText: String): String {
        val cleanText = japaneseText.trim()
        if (cleanText.isEmpty()) return ""

        translationCache.get(cleanText)?.let { cached ->
            return cached
        }

        val client = translator ?: return cleanText

        return suspendCancellableCoroutine { continuation ->
            client.translate(cleanText)
                .addOnSuccessListener { thaiResult ->
                    translationCache.put(cleanText, thaiResult)
                    continuation.resume(thaiResult)
                }
                .addOnFailureListener { ex ->
                    Log.w(TAG, "Translation error for text: $cleanText", ex)
                    continuation.resumeWithException(ex)
                }
        }
    }

    fun close() {
        translator?.close()
        translator = null
    }
}

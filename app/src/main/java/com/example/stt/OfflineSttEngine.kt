package com.example.stt

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.SubtitleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Offline Speech-to-Text engine powered by Vosk.
 * Operates 100% locally on device without external API connections.
 */
class OfflineSttEngine(private val context: Context) {

    companion object {
        private const val TAG = "KoeThai_VoskSTT"
        const val MODEL_DIR_NAME = "vosk-model-small-ja"
        const val SAMPLE_RATE = 16000.0f
    }

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var isInitialized = false

    var onPartialResult: ((String) -> Unit)? = null
    var onFinalResult: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun getModelDirectory(): File {
        val modelsParent = File(context.filesDir, "models")
        if (!modelsParent.exists()) {
            modelsParent.mkdirs()
        }
        return File(modelsParent, MODEL_DIR_NAME)
    }

    fun isModelInstalled(): Boolean {
        val modelDir = getModelDirectory()
        if (!modelDir.exists() || !modelDir.isDirectory) return false
        val files = modelDir.list() ?: return false
        return files.isNotEmpty()
    }

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelDir = getModelDirectory()
            if (!isModelInstalled()) {
                val extracted = tryExtractFromAssets()
                if (!extracted) {
                    Log.w(TAG, "Vosk Japanese model not found in ${modelDir.absolutePath}")
                    SubtitleRepository.updateModelStatus {
                        it.copy(
                            isVoskModelInstalled = false,
                            voskModelPath = modelDir.absolutePath,
                            statusDetail = "Vosk model not found. Follow instructions or import .zip"
                        )
                    }
                    return@withContext false
                }
            }

            Log.d(TAG, "Loading Vosk model from ${modelDir.absolutePath}")
            model = Model(modelDir.absolutePath)
            recognizer = Recognizer(model, SAMPLE_RATE)
            isInitialized = true

            SubtitleRepository.updateModelStatus {
                it.copy(
                    isVoskModelInstalled = true,
                    voskModelPath = modelDir.absolutePath,
                    statusDetail = "Vosk Japanese offline model is active"
                )
            }
            SubtitleRepository.updateState { it.copy(isVoskModelReady = true) }
            Log.i(TAG, "Vosk STT successfully initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Vosk model", e)
            onError?.invoke("Vosk initialization error: ${e.localizedMessage}")
            SubtitleRepository.updateModelStatus {
                it.copy(
                    isVoskModelInstalled = false,
                    statusDetail = "Error loading Vosk: ${e.localizedMessage}"
                )
            }
            false
        }
    }

    private fun tryExtractFromAssets(): Boolean {
        try {
            val assetList = context.assets.list("models") ?: emptyArray()
            if (assetList.contains(MODEL_DIR_NAME)) {
                copyAssetDir(context, "models/$MODEL_DIR_NAME", getModelDirectory())
                return true
            }

            val rootAssets = context.assets.list("") ?: emptyArray()
            if (rootAssets.contains("vosk-model-small-ja.zip")) {
                context.assets.open("vosk-model-small-ja.zip").use { input ->
                    unzipStream(input, getModelDirectory().parentFile ?: context.filesDir)
                }
                return true
            }
        } catch (e: Exception) {
            Log.d(TAG, "No assets model found: ${e.message}")
        }
        return false
    }

    suspend fun importModelFromZipUri(zipUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(zipUri) ?: return@withContext false
            val targetParent = File(context.filesDir, "models")
            if (!targetParent.exists()) targetParent.mkdirs()

            unzipStream(inputStream, targetParent)

            val extractedFolders = targetParent.listFiles { file -> file.isDirectory }
            val matchingFolder = extractedFolders?.firstOrNull {
                it.name.startsWith("vosk-model-small-ja") || it.name == MODEL_DIR_NAME
            }

            if (matchingFolder != null && matchingFolder.name != MODEL_DIR_NAME) {
                val targetDir = getModelDirectory()
                if (targetDir.exists()) targetDir.deleteRecursively()
                matchingFolder.renameTo(targetDir)
            }

            val installed = isModelInstalled()
            if (installed) {
                initialize()
            }
            installed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import model zip", e)
            false
        }
    }

    private fun unzipStream(inputStream: InputStream, targetDir: File) {
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            val buffer = ByteArray(8192)
            while (entry != null) {
                val newFile = File(targetDir, entry.name)
                if (!newFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    throw SecurityException("Zip Slip detected: ${entry.name}")
                }
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun copyAssetDir(context: Context, assetPath: String, targetDir: File) {
        val files = context.assets.list(assetPath) ?: return
        if (!targetDir.exists()) targetDir.mkdirs()

        for (filename in files) {
            val subAssetPath = "$assetPath/$filename"
            val subTargetFile = File(targetDir, filename)
            val subFiles = context.assets.list(subAssetPath)
            if (subFiles != null && subFiles.isNotEmpty()) {
                copyAssetDir(context, subAssetPath, subTargetFile)
            } else {
                context.assets.open(subAssetPath).use { input ->
                    FileOutputStream(subTargetFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    fun processAudio(data: ByteArray, length: Int) {
        val rec = recognizer ?: return
        try {
            if (rec.acceptWaveForm(data, length)) {
                val resultJson = rec.result
                parseFinalText(resultJson)?.let { text ->
                    if (text.isNotBlank()) {
                        onFinalResult?.invoke(text)
                    }
                }
            } else {
                val partialJson = rec.partialResult
                parsePartialText(partialJson)?.let { partial ->
                    if (partial.isNotBlank()) {
                        onPartialResult?.invoke(partial)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio recognition processing error", e)
        }
    }

    private fun parseFinalText(json: String): String? {
        return try {
            val obj = JSONObject(json)
            obj.optString("text", "")
        } catch (e: Exception) {
            null
        }
    }

    private fun parsePartialText(json: String): String? {
        return try {
            val obj = JSONObject(json)
            obj.optString("partial", "")
        } catch (e: Exception) {
            null
        }
    }

    fun reset() {
        recognizer?.reset()
    }

    fun release() {
        try {
            recognizer?.close()
            model?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing Vosk recognizer", e)
        }
        recognizer = null
        model = null
        isInitialized = false
        SubtitleRepository.updateState { it.copy(isVoskModelReady = false) }
    }
}

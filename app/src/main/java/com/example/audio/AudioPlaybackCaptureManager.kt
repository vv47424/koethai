package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import com.example.data.SubtitleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Manages capturing internal device audio using the Android 10+ (API 29)
 * AudioPlaybackCapture API via MediaProjection.
 */
class AudioPlaybackCaptureManager(
    private val mediaProjection: MediaProjection?
) {

    companion object {
        private const val TAG = "KoeThai_AudioCapture"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false

    var onAudioChunk: ((ByteArray, Int) -> Unit)? = null
    var onAudioLevelChanged: ((Float) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startCapture(scope: CoroutineScope): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            onError?.invoke("AudioPlaybackCapture requires Android 10 (API 29) or higher.")
            return false
        }

        if (mediaProjection == null) {
            onError?.invoke("MediaProjection is null.")
            return false
        }

        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            val bufferSize = maxOf(minBufferSize, 4096)

            val audioFormatSpec = AudioFormat.Builder()
                .setEncoding(AUDIO_FORMAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_CONFIG)
                .build()

            audioRecord = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(audioFormatSpec)
                .setBufferSizeInBytes(bufferSize)
                .build()

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onError?.invoke("AudioRecord initialization failed.")
                return false
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(2048)
                while (isActive && isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val level = calculateRmsLevel(buffer, read)
                        onAudioLevelChanged?.invoke(level)
                        SubtitleRepository.updateState { it.copy(audioLevel = level) }
                        onAudioChunk?.invoke(buffer, read)
                    } else if (read < 0) {
                        Log.w(TAG, "AudioRecord read error: $read")
                        delay(20)
                    }
                }
            }

            Log.i(TAG, "Internal audio capture started")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioPlaybackCapture", e)
            onError?.invoke("Audio capture error: ${e.localizedMessage}")
            return false
        }
    }

    private fun calculateRmsLevel(buffer: ByteArray, bytesRead: Int): Float {
        var sumSquares = 0.0
        val sampleCount = bytesRead / 2
        if (sampleCount == 0) return 0f

        for (i in 0 until bytesRead step 2) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val signedSample = sample.toShort()
            sumSquares += (signedSample * signedSample)
        }

        val rms = sqrt(sumSquares / sampleCount)
        return (rms / 8000.0).toFloat().coerceIn(0f, 1f)
    }

    fun stopCapture() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord", e)
        }
        audioRecord = null
        SubtitleRepository.updateState { it.copy(audioLevel = 0f) }
    }
}

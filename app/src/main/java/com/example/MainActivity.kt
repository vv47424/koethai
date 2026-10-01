package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.data.SubtitleRepository
import com.example.service.FloatingSubtitleService
import com.example.stt.OfflineSttEngine
import com.example.translation.OfflineTranslationManager
import com.example.ui.screens.MainSubtitleScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var translationManager: OfflineTranslationManager
    private lateinit var sttEngine: OfflineSttEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        SubtitleRepository.initialize(this)
        translationManager = OfflineTranslationManager(this)
        sttEngine = OfflineSttEngine(this)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val subtitleState by SubtitleRepository.subtitleState.collectAsState()
                    val subtitleConfig by SubtitleConfigState()
                    val modelStatus by SubtitleRepository.modelStatus.collectAsState()

                    var hasOverlayPermission by remember {
                        mutableStateOf(Settings.canDrawOverlays(this@MainActivity))
                    }
                    var hasAudioPermission by remember {
                        mutableStateOf(
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                    }
                    var hasNotificationPermission by remember {
                        mutableStateOf(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                            } else true
                        )
                    }
                    var isMediaProjectionReady by remember {
                        mutableStateOf(SubtitleRepository.mediaProjectionResultData != null)
                    }

                    val coroutineScope = rememberCoroutineScope()

                    // Overlay Permission Launcher
                    val overlayPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) {
                        hasOverlayPermission = Settings.canDrawOverlays(this@MainActivity)
                    }

                    // Runtime Permissions Launcher (Audio, Notifications)
                    val runtimePermissionsLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true ||
                                ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            hasNotificationPermission = permissions[Manifest.permission.POST_NOTIFICATIONS] == true ||
                                    ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                        }
                    }

                    // MediaProjection Consent Dialog Launcher
                    val mediaProjectionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        if (result.resultCode == RESULT_OK && result.data != null) {
                            SubtitleRepository.mediaProjectionResultCode = result.resultCode
                            SubtitleRepository.mediaProjectionResultData = result.data
                            isMediaProjectionReady = true
                            startSubtitleService()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Internal audio capture permission was not granted.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    // File Picker for Vosk Model Zip
                    val pickVoskZipLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri: Uri? ->
                        if (uri != null) {
                            coroutineScope.launch {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Unpacking Vosk model... Please wait",
                                    Toast.LENGTH_LONG
                                ).show()

                                val success = sttEngine.importModelFromZipUri(uri)
                                if (success) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Vosk Japanese model imported successfully!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    checkAllModelStatuses()
                                } else {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Failed to extract model. Ensure it is a valid Vosk model zip.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }

                    LaunchedEffect(Unit) {
                        checkAllModelStatuses()
                    }

                    MainSubtitleScreen(
                        subtitleState = subtitleState,
                        subtitleConfig = subtitleConfig,
                        modelStatus = modelStatus,
                        hasOverlayPermission = hasOverlayPermission,
                        hasAudioPermission = hasAudioPermission,
                        hasNotificationPermission = hasNotificationPermission,
                        isMediaProjectionReady = isMediaProjectionReady,
                        onRequestOverlayPermission = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            overlayPermissionLauncher.launch(intent)
                        },
                        onRequestAudioPermission = {
                            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                arrayOf(Manifest.permission.RECORD_AUDIO)
                            }
                            runtimePermissionsLauncher.launch(permissions)
                        },
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                runtimePermissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                            }
                        },
                        onRequestMediaProjection = {
                            if (!hasOverlayPermission) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Please enable 'Draw Over Other Apps' first",
                                    Toast.LENGTH_SHORT
                                ).show()
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                                overlayPermissionLauncher.launch(intent)
                                return@MainSubtitleScreen
                            }

                            if (!hasAudioPermission) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Please grant audio capture permission",
                                    Toast.LENGTH_SHORT
                                ).show()
                                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    arrayOf(Manifest.permission.RECORD_AUDIO)
                                }
                                runtimePermissionsLauncher.launch(permissions)
                                return@MainSubtitleScreen
                            }

                            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                        },
                        onStartService = {
                            startSubtitleService()
                        },
                        onStopService = {
                            stopSubtitleService()
                        },
                        onSimulateSpeech = {
                            runSpeechSimulation(coroutineScope)
                        },
                        onDownloadMlKitModel = { lang ->
                            val targetLang = if (lang == "ja") {
                                com.google.mlkit.nl.translate.TranslateLanguage.JAPANESE
                            } else {
                                com.google.mlkit.nl.translate.TranslateLanguage.THAI
                            }
                            translationManager.downloadModel(
                                targetLang,
                                onSuccess = {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Offline model downloaded successfully!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    checkAllModelStatuses()
                                },
                                onFailure = { ex ->
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Model download failed: ${ex.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        },
                        onPickVoskZip = {
                            pickVoskZipLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        },
                        onRefreshStatus = {
                            checkAllModelStatuses()
                        }
                    )
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun SubtitleConfigState() = SubtitleRepository.subtitleConfig.collectAsState()

    private fun checkAllModelStatuses() {
        translationManager.checkModelsStatus { _, _ -> }
        val isVoskInstalled = sttEngine.isModelInstalled()
        SubtitleRepository.updateModelStatus {
            it.copy(
                isVoskModelInstalled = isVoskInstalled,
                voskModelPath = sttEngine.getModelDirectory().absolutePath
            )
        }
    }

    private fun startSubtitleService() {
        val resultCode = SubtitleRepository.mediaProjectionResultCode
        val resultData = SubtitleRepository.mediaProjectionResultData

        if (resultCode != 0 && resultData != null) {
            val intent = FloatingSubtitleService.startIntent(this, resultCode, resultData)
            ContextCompat.startForegroundService(this, intent)
        } else {
            val intent = Intent(this, FloatingSubtitleService::class.java).apply {
                action = FloatingSubtitleService.ACTION_START
            }
            ContextCompat.startForegroundService(this, intent)
        }
    }

    private fun stopSubtitleService() {
        stopService(FloatingSubtitleService.stopIntent(this))
    }

    private fun runSpeechSimulation(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            Toast.makeText(this@MainActivity, "Simulating fluent sentence translation...", Toast.LENGTH_SHORT).show()

            val serviceIntent = Intent(this@MainActivity, FloatingSubtitleService::class.java).apply {
                action = FloatingSubtitleService.ACTION_SIMULATE_SPEECH
            }
            try {
                startService(serviceIntent)
            } catch (e: Exception) {
                // If service is not running, simulate directly in repository
            }

            val testDialogues = listOf(
                "こんにちは！動画の音声をリアルタイムで認識しています。" to "สวัสดีครับ! กำลังจำแนกเสียงวิดีโอแบบเรียลไทม์",
                "文の区切りを自然に待ってから翻訳するため、タイ語がとても自然になります。" to "ระบบจะรอจุดสิ้นสุดของประโยคก่อนแปล ทำให้ภาษาไทยมีความเป็นธรรมชาติอย่างมาก",
                "ウィンドウの右下をドラッグすると自由にリサイズできます。" to "ลากที่มุมขวาล่างเพื่อปรับขนาดหน้าต่างได้อย่างอิสระ",
                "複数の文がスクロール表示されるので文脈が分かります。" to "แสดงประโยคต่อเนื่องแบบเลื่อนได้ทำให้เข้าใจบริบทได้ดียิ่งขึ้น"
            )

            for ((ja, th) in testDialogues) {
                // Simulate interim speech
                SubtitleRepository.updateState { it.copy(audioLevel = 0.8f, currentInterimJapanese = ja.take(8) + "...") }
                delay(500)

                var finalThai = th
                try {
                    withContext(Dispatchers.IO) {
                        val realTranslation = translationManager.translate(ja)
                        if (realTranslation.isNotBlank()) {
                            finalThai = realTranslation
                        }
                    }
                } catch (e: Exception) {
                    // Fallback
                }

                SubtitleRepository.addSentence(ja, finalThai)
                SubtitleRepository.updateState { it.copy(audioLevel = 0.2f) }
                delay(2000)
            }
            SubtitleRepository.updateState { it.copy(audioLevel = 0f, currentInterimJapanese = "") }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        translationManager.close()
    }
}

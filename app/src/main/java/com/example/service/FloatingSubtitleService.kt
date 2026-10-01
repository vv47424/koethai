package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.AudioPlaybackCaptureManager
import com.example.data.SubtitleRepository
import com.example.model.SubtitleConfig
import com.example.model.SubtitleSentence
import com.example.stt.OfflineSttEngine
import com.example.stt.SentenceTranslationBuffer
import com.example.translation.OfflineTranslationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground Service that captures internal device audio, aggregates speech into
 * full sentences, translates Japanese to Thai via Google ML Kit On-Device Translation,
 * and renders a resizable, multi-sentence floating subtitle window.
 */
class FloatingSubtitleService : Service() {

    companion object {
        private const val TAG = "KoeThai_FloatService"
        const val NOTIFICATION_ID = 4041
        const val CHANNEL_ID = "koethai_subtitles_channel"

        const val ACTION_START = "com.example.service.START"
        const val ACTION_STOP = "com.example.service.STOP"
        const val ACTION_TOGGLE_PAUSE = "com.example.service.TOGGLE_PAUSE"
        const val ACTION_SIMULATE_SPEECH = "com.example.service.SIMULATE"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        fun startIntent(context: Context, resultCode: Int, data: Intent): Intent {
            return Intent(context, FloatingSubtitleService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, FloatingSubtitleService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var windowManager: WindowManager? = null
    private var overlayRootView: View? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null

    private var mediaProjection: MediaProjection? = null
    private var audioCaptureManager: AudioPlaybackCaptureManager? = null
    private var sttEngine: OfflineSttEngine? = null
    private var translationManager: OfflineTranslationManager? = null
    private var sentenceBuffer: SentenceTranslationBuffer? = null

    // Overlay Views
    private var cardContainer: LinearLayout? = null
    private var sentenceListContainer: LinearLayout? = null
    private var liveInterimTextView: TextView? = null
    private var subtitleScrollView: ScrollView? = null
    private var audioEnergyBar: ProgressBar? = null
    private var statusPill: TextView? = null
    private var isMinimized = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        SubtitleRepository.initialize(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        sttEngine = OfflineSttEngine(this)
        translationManager = OfflineTranslationManager(this)

        setupSentenceBuffering()
        observeConfigChanges()
        observeSentenceState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PAUSE -> {
                val currentState = SubtitleRepository.subtitleState.value
                val newPause = !currentState.isPaused
                SubtitleRepository.updateState { it.copy(isPaused = newPause) }
                updateOverlayStatus(if (newPause) "PAUSED" else "LIVE")
                return START_STICKY
            }
            ACTION_SIMULATE_SPEECH -> {
                simulateSpeechDemo()
                return START_STICKY
            }
            ACTION_START -> {
                val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, SubtitleRepository.mediaProjectionResultCode)
                    ?: SubtitleRepository.mediaProjectionResultCode
                val resultData = (intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                    ?: SubtitleRepository.mediaProjectionResultData)

                startInForeground()

                if (resultCode != 0 && resultData != null) {
                    initMediaProjectionAndAudio(resultCode, resultData)
                }

                showFloatingOverlay()
                initEngines()
            }
        }

        return START_STICKY
    }

    private fun startInForeground() {
        val notification = createNotification("Capturing internal audio • Subtitles active")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        SubtitleRepository.updateState { it.copy(isRunning = true, statusMessage = "Capturing Audio") }
    }

    private fun createNotification(content: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingSubtitleService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, FloatingSubtitleService::class.java).apply { action = ACTION_TOGGLE_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KoeThai Subtitles")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "KoeThai Live Subtitles",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active status of offline audio capture and subtitle translation"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun initMediaProjectionAndAudio(resultCode: Int, resultData: Intent) {
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

            if (mediaProjection != null) {
                audioCaptureManager = AudioPlaybackCaptureManager(mediaProjection)
                audioCaptureManager?.onAudioChunk = { buffer, length ->
                    if (!SubtitleRepository.subtitleState.value.isPaused) {
                        sttEngine?.processAudio(buffer, length)
                    }
                }
                audioCaptureManager?.onAudioLevelChanged = { level ->
                    serviceScope.launch(Dispatchers.Main) {
                        audioEnergyBar?.progress = (level * 100).toInt()
                    }
                }
                audioCaptureManager?.onError = { error ->
                    Log.e(TAG, "Audio Capture error: $error")
                    SubtitleRepository.updateState { it.copy(statusMessage = error) }
                }

                audioCaptureManager?.startCapture(serviceScope)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed initializing MediaProjection", e)
        }
    }

    private fun initEngines() {
        serviceScope.launch(Dispatchers.IO) {
            translationManager?.checkModelsStatus { _, _ -> }
            translationManager?.prepareTranslator(
                onSuccess = { Log.d(TAG, "ML Kit Translator ready") },
                onFailure = { Log.w(TAG, "Translator preparation warning: ${it.message}") }
            )

            val sttReady = sttEngine?.initialize() ?: false
            if (!sttReady) {
                SubtitleRepository.updateState {
                    it.copy(statusMessage = "Vosk model not loaded. Import model in app settings")
                }
            }
        }
    }

    /**
     * Configures sentence-level buffering so that complete sentences are
     * accumulated before sending to ML Kit for translation.
     */
    private fun setupSentenceBuffering() {
        sentenceBuffer = SentenceTranslationBuffer(
            scope = serviceScope,
            pauseThresholdMs = 950L,
            onSentenceReady = { fullJapaneseSentence ->
                translateFullSentence(fullJapaneseSentence)
            },
            onInterimUpdate = { interimText ->
                serviceScope.launch(Dispatchers.Main) {
                    liveInterimTextView?.text = if (interimText.isNotBlank()) "▸ $interimText" else ""
                    liveInterimTextView?.visibility = if (interimText.isNotBlank()) View.VISIBLE else View.GONE
                    SubtitleRepository.updateState { it.copy(currentInterimJapanese = interimText) }
                }
            }
        )

        sttEngine?.onPartialResult = { partialJa ->
            sentenceBuffer?.onPartial(partialJa)
        }

        sttEngine?.onFinalResult = { segmentJa ->
            sentenceBuffer?.onSpeechSegment(segmentJa)
        }
    }

    /**
     * Translates a complete grammatical sentence and appends it to the multi-sentence view.
     */
    private fun translateFullSentence(japaneseSentence: String) {
        SubtitleRepository.updateState { it.copy(isTranslating = true) }

        serviceScope.launch(Dispatchers.IO) {
            try {
                val thaiTranslation = translationManager?.translate(japaneseSentence) ?: japaneseSentence
                serviceScope.launch(Dispatchers.Main) {
                    SubtitleRepository.addSentence(japaneseSentence, thaiTranslation)
                    SubtitleRepository.updateState { it.copy(isTranslating = false) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Translation error for sentence: $japaneseSentence", e)
                serviceScope.launch(Dispatchers.Main) {
                    SubtitleRepository.addSentence(japaneseSentence, japaneseSentence)
                    SubtitleRepository.updateState { it.copy(isTranslating = false) }
                }
            }
        }
    }

    /**
     * Creates and attaches the resizable floating overlay view.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission not granted!")
            return
        }

        if (overlayRootView != null) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val defaultWidth = (screenWidth * 0.90f).toInt().coerceAtLeast(600)
        val defaultHeight = 420 // Initial comfortable height for multi-sentence display

        val params = WindowManager.LayoutParams(
            defaultWidth,
            defaultHeight,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 140
        }
        windowLayoutParams = params

        overlayRootView = buildOverlayView()

        setupMoveDragListener(overlayRootView!!.findViewById(R.id.top_bar), params)
        setupResizeDragListener(overlayRootView!!.findViewById(R.id.resize_handle), params)

        try {
            windowManager?.addView(overlayRootView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay view to WindowManager", e)
        }
    }

    /**
     * Inflates and binds the floating subtitle overlay view from XML.
     */
    private fun buildOverlayView(): View {
        val inflater = LayoutInflater.from(this)
        val root = inflater.inflate(R.layout.floating_subtitle_window, null)

        cardContainer = root.findViewById(R.id.card_container)
        sentenceListContainer = root.findViewById(R.id.sentence_list_container)
        liveInterimTextView = root.findViewById(R.id.live_interim_text)
        subtitleScrollView = root.findViewById(R.id.subtitle_scroll_view)
        audioEnergyBar = root.findViewById(R.id.audio_energy_bar)
        statusPill = root.findViewById(R.id.status_pill)

        // Apply dark translucent card style
        val bg = GradientDrawable().apply {
            setColor(Color.argb((255 * 0.88f).toInt(), 15, 23, 42))
            cornerRadius = 24f
            setStroke(2, Color.argb(130, 96, 165, 250))
        }
        cardContainer?.background = bg

        // Status pill background
        statusPill?.background = GradientDrawable().apply {
            setColor(Color.parseColor("#EF4444"))
            cornerRadius = 10f
        }

        // Quick Controls
        root.findViewById<View>(R.id.btn_clear).setOnClickListener {
            SubtitleRepository.clearSentences()
            sentenceBuffer?.reset()
        }

        root.findViewById<View>(R.id.btn_font_minus).setOnClickListener {
            val cur = SubtitleRepository.subtitleConfig.value.fontSizeSp
            SubtitleRepository.updateConfig { it.copy(fontSizeSp = (cur - 2f).coerceAtLeast(12f)) }
        }

        root.findViewById<View>(R.id.btn_font_plus).setOnClickListener {
            val cur = SubtitleRepository.subtitleConfig.value.fontSizeSp
            SubtitleRepository.updateConfig { it.copy(fontSizeSp = (cur + 2f).coerceAtMost(30f)) }
        }

        root.findViewById<View>(R.id.btn_minimize).setOnClickListener {
            toggleMinimize()
        }

        root.findViewById<View>(R.id.btn_close).setOnClickListener {
            stopSelf()
        }

        return root
    }

    private fun toggleMinimize() {
        isMinimized = !isMinimized
        subtitleScrollView?.visibility = if (isMinimized) View.GONE else View.VISIBLE
        overlayRootView?.findViewById<View>(R.id.bottom_bar)?.visibility = if (isMinimized) View.GONE else View.VISIBLE
        statusPill?.text = if (isMinimized) "MINI" else "LIVE"

        // Adjust window height when minimized vs expanded
        windowLayoutParams?.let { params ->
            params.height = if (isMinimized) WindowManager.LayoutParams.WRAP_CONTENT else 420
            windowManager?.updateViewLayout(overlayRootView, params)
        }
    }

    private fun updateOverlayStatus(status: String) {
        statusPill?.text = status
    }

    /**
     * Handles dragging the top bar to reposition the floating overlay window.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupMoveDragListener(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    params.x = initialX + deltaX
                    params.y = initialY - deltaY // Inverted for Gravity.BOTTOM

                    try {
                        windowManager?.updateViewLayout(overlayRootView, params)
                    } catch (e: Exception) {
                        Log.w(TAG, "Layout move update error", e)
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Handles dragging the bottom-right corner grip (⤡) to dynamically resize the overlay.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupResizeDragListener(view: View, params: WindowManager.LayoutParams) {
        var initialWidth = 0
        var initialHeight = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialWidth = params.width
                    initialHeight = params.height
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    // Constrain min/max dimensions
                    val newWidth = (initialWidth + deltaX).coerceIn(400, resources.displayMetrics.widthPixels)
                    val newHeight = (initialHeight + deltaY).coerceIn(200, 1400)

                    params.width = newWidth
                    params.height = newHeight

                    try {
                        windowManager?.updateViewLayout(overlayRootView, params)
                    } catch (e: Exception) {
                        Log.w(TAG, "Layout resize update error", e)
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Renders multiple sentences inside the scrollable container.
     */
    private fun observeSentenceState() {
        serviceScope.launch {
            SubtitleRepository.subtitleState.collectLatest { state ->
                renderSentences(state.recentSentences)
            }
        }
    }

    private fun renderSentences(sentences: List<SubtitleSentence>) {
        val container = sentenceListContainer ?: return
        val config = SubtitleRepository.subtitleConfig.value

        // Remove previous sentence views (keep live interim text view at bottom)
        container.removeViews(0, (container.childCount - 1).coerceAtLeast(0))

        for (sentence in sentences) {
            val sentenceView = createSentenceItemView(sentence, config)
            // Insert above the interim text view
            container.addView(sentenceView, container.childCount - 1)
        }

        // Auto-scroll to the bottom as new sentences arrive
        subtitleScrollView?.post {
            subtitleScrollView?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun createSentenceItemView(sentence: SubtitleSentence, config: SubtitleConfig): View {
        val itemLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 4, 0, 8)
        }

        // Thai translation (Primary)
        if (config.showThaiTranslation) {
            val thaiText = TextView(this).apply {
                text = sentence.thaiText
                setTextColor(config.thaiAccentColorHex.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, config.fontSizeSp)
                typeface = Typeface.DEFAULT_BOLD
                setShadowLayer(4f, 2f, 2f, Color.BLACK)
            }
            itemLayout.addView(thaiText)
        }

        // Original Japanese text (Secondary)
        if (config.showOriginalJapanese) {
            val jaText = TextView(this).apply {
                text = sentence.japaneseText
                setTextColor(Color.argb(200, 241, 245, 249))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, (config.fontSizeSp * 0.78f).coerceAtLeast(11f))
                setShadowLayer(2f, 1f, 1f, Color.BLACK)
                setPadding(0, 1, 0, 0)
            }
            itemLayout.addView(jaText)
        }

        return itemLayout
    }

    private fun observeConfigChanges() {
        serviceScope.launch {
            SubtitleRepository.subtitleConfig.collectLatest { config ->
                // Re-render sentences with new font size and colors
                renderSentences(SubtitleRepository.subtitleState.value.recentSentences)

                cardContainer?.background = GradientDrawable().apply {
                    setColor(Color.argb((255 * config.backgroundOpacity).toInt(), 15, 23, 42))
                    cornerRadius = 24f
                    setStroke(2, Color.argb(130, 96, 165, 250))
                }
            }
        }
    }

    /**
     * Simulates natural multi-sentence speech demo to verify multi-line scrolling
     * and fluent translation without needing an active external video player.
     */
    fun simulateSpeechDemo() {
        serviceScope.launch {
            val sampleDialogues = listOf(
                "こんにちは！動画の日本語音声をリアルタイムで認識しています。" to "สวัสดีครับ! กำลังจำแนกเสียงภาษาญี่ปุ่นในวิดีโอแบบเรียลไทม์",
                "音声の区切りを自然に待ってから翻訳するため、タイ語の翻訳がとても流暢になります。" to "ระบบจะรอจุดสิ้นสุดของประโยคตามธรรมชาติก่อนแปล ทำให้การแปลเป็นภาษาไทยมีความสละสลวยอย่างมาก",
                "フローティングウィンドウの右下をドラッグすると、画面サイズを自由に拡大縮小できます。" to "คุณสามารถลากที่มุมขวาล่างของหน้าต่างลอยเพื่อปรับขนาดหน้าจอได้อย่างอิสระ",
                "過去の会話もスクロールしてさかのぼって読むことができます。" to "คุณยังสามารถเลื่อนดูประโยคบทสนทนาก่อนหน้านี้ได้อย่างต่อเนื่อง"
            )

            for ((ja, th) in sampleDialogues) {
                // Stream interim fragments
                val words = ja.chunked(maxOf(1, ja.length / 4))
                var current = ""
                for (w in words) {
                    current += w
                    liveInterimTextView?.text = "▸ $current"
                    liveInterimTextView?.visibility = View.VISIBLE
                    delay(300)
                }

                liveInterimTextView?.visibility = View.GONE
                liveInterimTextView?.text = ""

                SubtitleRepository.addSentence(ja, th)
                delay(1800)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()

        audioCaptureManager?.stopCapture()
        sttEngine?.release()
        translationManager?.close()
        sentenceBuffer?.reset()

        if (overlayRootView != null) {
            try {
                windowManager?.removeView(overlayRootView)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing overlay view", e)
            }
            overlayRootView = null
        }

        SubtitleRepository.updateState {
            it.copy(
                isRunning = false,
                audioLevel = 0f,
                statusMessage = "Service Stopped"
            )
        }
        Log.i(TAG, "Floating Subtitle Service destroyed")
    }
}

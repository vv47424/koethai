package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SubtitleRepository
import com.example.model.ModelStatus
import com.example.model.SubtitleConfig
import com.example.model.SubtitleState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSubtitleScreen(
    subtitleState: SubtitleState,
    subtitleConfig: SubtitleConfig,
    modelStatus: ModelStatus,
    hasOverlayPermission: Boolean,
    hasAudioPermission: Boolean,
    hasNotificationPermission: Boolean,
    isMediaProjectionReady: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestMediaProjection: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onSimulateSpeech: () -> Unit,
    onDownloadMlKitModel: (String) -> Unit,
    onPickVoskZip: () -> Unit,
    onRefreshStatus: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    // "History" tab completely removed
    val tabs = listOf("Subtitles", "Offline Models", "Appearance")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Header (Removed "Offline Only" text label and "100%" indicator)
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF312E81)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = "App Icon",
                            tint = Color(0xFFFCD34D),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "KoeThai Subtitles",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        // Removed "100%"
                        Text(
                            text = "Offline • JA ➔ TH Audio Translation",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
            // Removed actions = { Box { Text("OFFLINE ONLY") } }
        )

        // Navigation Tabs
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("tab_$index")
                )
            }
        }

        // Tab Content
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> LiveControlTab(
                    subtitleState = subtitleState,
                    hasOverlayPermission = hasOverlayPermission,
                    hasAudioPermission = hasAudioPermission,
                    hasNotificationPermission = hasNotificationPermission,
                    isMediaProjectionReady = isMediaProjectionReady,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onRequestAudioPermission = onRequestAudioPermission,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onRequestMediaProjection = onRequestMediaProjection,
                    onStartService = onStartService,
                    onStopService = onStopService,
                    onSimulateSpeech = onSimulateSpeech
                )
                1 -> OfflineModelsTab(
                    modelStatus = modelStatus,
                    onDownloadMlKitModel = onDownloadMlKitModel,
                    onPickVoskZip = onPickVoskZip,
                    onRefreshStatus = onRefreshStatus
                )
                2 -> AppearanceSettingsTab(
                    config = subtitleConfig
                )
            }
        }
    }
}

@Composable
private fun LiveControlTab(
    subtitleState: SubtitleState,
    hasOverlayPermission: Boolean,
    hasAudioPermission: Boolean,
    hasNotificationPermission: Boolean,
    isMediaProjectionReady: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestMediaProjection: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onSimulateSpeech: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Main Action Card (Start / Stop Floating Subtitles)
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (subtitleState.isRunning) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = if (subtitleState.isRunning) "Floating Subtitles Active" else "Subtitle Overlay Inactive",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (subtitleState.isRunning) Color(0xFF38BDF8) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (subtitleState.isRunning) "Listening & translating full sentences..." else "Ready to capture internal app audio",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }

                        // Status Dot
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (subtitleState.isRunning) Color(0xFF10B981) else Color.Gray)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Audio Level Meter
                    Text(
                        text = "Internal Audio Energy: ${(subtitleState.audioLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { subtitleState.audioLevel },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF38BDF8),
                        trackColor = Color.DarkGray
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        if (!subtitleState.isRunning) {
                            Button(
                                onClick = {
                                    if (!isMediaProjectionReady) {
                                        onRequestMediaProjection()
                                    } else {
                                        onStartService()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("start_subtitles_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (!isMediaProjectionReady) "Capture Audio & Start" else "Start Floating Subtitles")
                            }
                        } else {
                            Button(
                                onClick = onStopService,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("stop_subtitles_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop Subtitles")
                            }
                        }
                    }
                }
            }
        }

        // Multi-Sentence Subtitle Live Preview Box
        item {
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Live Subtitle Stream",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )

                        // Demo Simulator button
                        OutlinedButton(
                            onClick = onSimulateSpeech,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("simulate_speech_button")
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Simulate Sentence", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Multi-Sentence Container Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F172A))
                            .padding(14.dp)
                    ) {
                        Column {
                            if (subtitleState.recentSentences.isEmpty()) {
                                Text(
                                    text = "Waiting for complete Japanese sentences...",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            } else {
                                subtitleState.recentSentences.takeLast(4).forEach { item ->
                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                        Text(
                                            text = item.thaiText,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFCD34D)
                                        )
                                        Text(
                                            text = item.japaneseText,
                                            fontSize = 13.sp,
                                            color = Color(0xFFCBD5E1)
                                        )
                                    }
                                }
                            }

                            // Live interim preview
                            if (subtitleState.currentInterimJapanese.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "▸ ${subtitleState.currentInterimJapanese}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF38BDF8),
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }
                }
            }
        }

        // Permissions Checklist Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Required Permissions",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    PermissionRow(
                        title = "Draw Over Other Apps",
                        description = "Display floating subtitles over video players",
                        isGranted = hasOverlayPermission,
                        onRequest = onRequestOverlayPermission
                    )

                    PermissionRow(
                        title = "Audio Recording",
                        description = "Required to capture internal playback via AudioPlaybackCapture",
                        isGranted = hasAudioPermission,
                        onRequest = onRequestAudioPermission
                    )

                    PermissionRow(
                        title = "Internal Audio Capture Consent",
                        description = "System dialog consent to route audio from other applications",
                        isGranted = isMediaProjectionReady,
                        onRequest = onRequestMediaProjection
                    )
                }
            }
        }

        // Resizable & Multi-Sentence Feature Guide
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New Features: Resizable & Fluent Subtitles",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Resize Floating Window: Drag the bottom-right corner grip (⤡) to adjust the width and height to fit your video.\n" +
                               "• Multi-Sentence History: Scroll up/down inside the subtitle box to read preceding dialogue.\n" +
                               "• Fluent Sentence Translation: Speech is buffered until a natural pause or punctuation occurs, generating fluent and natural Thai.",
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isGranted) Color(0xFF10B981) else Color(0xFFF59E0B),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!isGranted) {
            Button(
                onClick = onRequest,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Grant", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun OfflineModelsTab(
    modelStatus: ModelStatus,
    onDownloadMlKitModel: (String) -> Unit,
    onPickVoskZip: () -> Unit,
    onRefreshStatus: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ML Kit On-Device Translation",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        IconButton(onClick = onRefreshStatus) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Status")
                        }
                    }

                    Text(
                        text = "Zero cloud latency. Models run completely locally on-device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ModelDownloadRow(
                        language = "Japanese (日本語)",
                        isDownloaded = modelStatus.isJapaneseModelDownloaded,
                        isDownloading = modelStatus.isDownloadingJapanese,
                        onDownload = { onDownloadMlKitModel("ja") }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ModelDownloadRow(
                        language = "Thai (ภาษาไทย)",
                        isDownloaded = modelStatus.isThaiModelDownloaded,
                        isDownloading = modelStatus.isDownloadingThai,
                        onDownload = { onDownloadMlKitModel("th") }
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Audiotrack, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Vosk Japanese STT Model",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (modelStatus.isVoskModelInstalled) Color(0xFF065F46) else Color(0xFF7F1D1D)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (modelStatus.isVoskModelInstalled) "INSTALLED & ACTIVE" else "NOT INSTALLED",
                                color = if (modelStatus.isVoskModelInstalled) Color(0xFFA7F3D0) else Color(0xFFFECACA),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Path: ${modelStatus.voskModelPath.ifBlank { "app/src/main/assets/models/vosk-model-small-ja" }}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onPickVoskZip,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        // Generic x.xx placeholder
                        Text("Import Model Zip (vosk-model-small-ja-x.xx.zip)")
                    }
                }
            }
        }

        item {
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Step-by-Step Offline Model Setup",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Generic placeholder x.xx
                    Text(
                        text = "Option A: Direct In-App Import (Recommended)\n" +
                               "1. Download 'vosk-model-small-ja-x.xx.zip' from https://alphacephei.com/vosk/models on your phone.\n" +
                               "2. Tap 'Import Model Zip' above and select the file. KoeThai will automatically unpack it!\n\n" +
                               "Option B: Package in Assets (For Build)\n" +
                               "1. Unzip 'vosk-model-small-ja-x.xx.zip'.\n" +
                               "2. Rename folder to 'vosk-model-small-ja'.\n" +
                               "3. Place folder into: app/src/main/assets/models/vosk-model-small-ja/\n" +
                               "4. The app extracts it automatically on first launch.",
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Vosk Models", "https://alphacephei.com/vosk/models"))
                            Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Alphacephei Models URL")
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelDownloadRow(
    language: String,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = language, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                text = if (isDownloaded) "Downloaded • Offline Ready" else "Requires one-time download",
                fontSize = 11.sp,
                color = if (isDownloaded) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isDownloading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else if (isDownloaded) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Ready", tint = Color(0xFF10B981))
        } else {
            Button(
                onClick = onDownload,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Download", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AppearanceSettingsTab(
    config: SubtitleConfig
) {
    var fontSize by remember { mutableStateOf(config.fontSizeSp) }
    var opacity by remember { mutableStateOf(config.backgroundOpacity) }
    var showJa by remember { mutableStateOf(config.showOriginalJapanese) }
    var showTh by remember { mutableStateOf(config.showThaiTranslation) }
    var selectedColor by remember { mutableStateOf(config.thaiAccentColorHex) }

    val colorOptions = listOf(
        0xFFFCD34DL to "Amber Gold",
        0xFFFFFFFFL to "Pure White",
        0xFF38BDF8L to "Cyber Cyan",
        0xFF34D399L to "Emerald Green"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Subtitle Appearance",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(text = "Subtitle Font Size: ${fontSize.toInt()} sp", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = fontSize,
                        onValueChange = {
                            fontSize = it
                            SubtitleRepository.updateConfig { c -> c.copy(fontSizeSp = it) }
                        },
                        valueRange = 12f..30f,
                        steps = 8
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Overlay Background Opacity: ${(opacity * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = opacity,
                        onValueChange = {
                            opacity = it
                            SubtitleRepository.updateConfig { c -> c.copy(backgroundOpacity = it) }
                        },
                        valueRange = 0.3f..1.0f
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = "Thai Subtitle Highlight Color", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        colorOptions.forEach { (hex, label) ->
                            FilterChip(
                                selected = selectedColor == hex,
                                onClick = {
                                    selectedColor = hex
                                    SubtitleRepository.updateConfig { c -> c.copy(thaiAccentColorHex = hex) }
                                },
                                label = { Text(label, fontSize = 11.sp) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(hex))
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Show Original Japanese Text", fontWeight = FontWeight.SemiBold)
                            Text(text = "Display Japanese transcription alongside Thai", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = showJa,
                            onCheckedChange = {
                                showJa = it
                                SubtitleRepository.updateConfig { c -> c.copy(showOriginalJapanese = it) }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Show Thai Translation", fontWeight = FontWeight.SemiBold)
                            Text(text = "Display translated Thai subtitles", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = showTh,
                            onCheckedChange = {
                                showTh = it
                                SubtitleRepository.updateConfig { c -> c.copy(showThaiTranslation = it) }
                            }
                        )
                    }
                }
            }
        }
    }
}

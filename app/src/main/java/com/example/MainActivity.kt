package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ConnectionSettings
import com.example.data.VoiceLog
import com.example.ui.AppState
import com.example.ui.PatriciaViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MicrophoneIcon(
    modifier: Modifier = Modifier, 
    active: Boolean, 
    color: Color
) {
    Box(
        modifier = modifier.size(36.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Grille Capsule
            Box(
                modifier = Modifier
                    .size(width = 12.dp, height = 20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) color else Color.Transparent)
                    .border(2.dp, color, RoundedCornerShape(6.dp))
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            // Stand U-shape bar
            Box(
                modifier = Modifier
                    .size(width = 18.dp, height = 6.dp)
                    .border(
                        BorderStroke(2.dp, color),
                        shape = RoundedCornerShape(bottomStart = 9.dp, bottomEnd = 9.dp)
                    )
            )
            
            // Base post
            Box(
                modifier = Modifier
                    .size(width = 2.dp, height = 4.dp)
                    .background(color)
            )
            
            // Base foot
            Box(
                modifier = Modifier
                    .size(width = 10.dp, height = 2.dp)
                    .background(color)
            )
        }
        
        // Elegant diagonal slash if muted / cold
        if (!active) {
            Box(
                modifier = Modifier
                    .size(width = 28.dp, height = 2.dp)
                    .rotate(45f)
                    .background(color)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: PatriciaViewModel = viewModel()
) {
    val context = LocalContext.current

    // State collections
    val settingsState by viewModel.settingsState.collectAsState()
    val logs by viewModel.logsState.collectAsState()
    val appState by viewModel.appState.collectAsState()
    val terminalOutput by viewModel.terminalOutput.collectAsState()
    val isRecordingActive by viewModel.isRecordingActive.collectAsState()
    val lastLatencyMs by viewModel.lastLatencyMs.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("TRANSCRIPT") } // "TRANSCRIPT" or "CONSOLE"

    // Audio Permission Handling
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "Microphone access verified", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permission denied. Capture cannot start.", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(PolishBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = PolishBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            
            // 🧱 1. Header (Dynamic System Badging)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: Custom Branded Labels
                Column {
                    Text(
                        text = "SOVEREIGN LINK",
                        color = PolishAccentBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "LightHouse Beacon",
                        color = PolishTextWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp
                    )
                }

                // Right: Interactive Connection Badges and Settings Access
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Status Beacon
                    val badgeColor by animateColorAsState(
                        targetValue = when (appState) {
                            AppState.LISTENING -> PolishSageGreen
                            AppState.CONNECTING_SSH -> Color(0xFFFFB300)
                            AppState.RUNNING_CMD -> Color(0xFF00E5FF)
                            AppState.SPEAKING -> Color(0xFFCE93D8)
                            AppState.ERROR -> Color(0xFFEF5350)
                            AppState.IDLE -> PolishSageGreen.copy(alpha = 0.6f)
                        }, label = "badgeColor"
                    )

                    val badgeLabel = when (appState) {
                        AppState.LISTENING -> "LISTENING"
                        AppState.CONNECTING_SSH -> "SSH DIAL"
                        AppState.RUNNING_CMD -> "RUN CMD"
                        AppState.SPEAKING -> "BEACON TTS"
                        AppState.ERROR -> "DISRUPTED"
                        AppState.IDLE -> "HOST OPEN"
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(PolishWidget)
                            .border(1.dp, PolishBorder, RoundedCornerShape(100.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = badgeLabel,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Settings Button
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PolishWidget)
                            .border(1.dp, PolishBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Connection Settings",
                            tint = PolishAccentBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 🧱 2. Center Console Container Card
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(PolishSurface)
                    .border(1.dp, PolishBorder.copy(alpha = 0.4f), RoundedCornerShape(32.dp))
                    .padding(16.dp)
            ) {
                // Tab selector between Transcript & Console Logs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PolishWidget)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TabItem(
                        title = "TRANSCRIPT",
                        isSelected = selectedTab == "TRANSCRIPT",
                        onClick = { selectedTab = "TRANSCRIPT" }
                    )
                    TabItem(
                        title = "LIVE CONSOLE",
                        isSelected = selectedTab == "CONSOLE",
                        onClick = { selectedTab = "CONSOLE" }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (!hasAudioPermission) {
                        // Request Permission Banner
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MicrophoneIcon(
                                active = false,
                                color = PolishTextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Microphone Permission Required",
                                color = PolishTextWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Lighthouse Beacon parses the audio from your Ray-Ban glasses microphone to translate command strings downstream.",
                                color = PolishTextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PolishAccentBlue,
                                    contentColor = PolishDeepBlue
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Grant Microphone Access", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (selectedTab == "TRANSCRIPT") {
                        // Transcript History list
                        val listState = rememberLazyListState()
                        LaunchedEffect(logs.size) {
                            if (logs.isNotEmpty()) {
                                listState.animateScrollToItem(0)
                            }
                        }

                        if (logs.isEmpty()) {
                            // Empty State
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "🗼 Beacon Hub Silent",
                                    fontSize = 16.sp,
                                    color = PolishAccentBlue,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Ready to receive speech streams. Press the primary trigger button below to connect.",
                                    fontSize = 12.sp,
                                    color = PolishTextMuted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(logs) { log ->
                                    InteractionBubble(log = log)
                                }
                            }
                        }
                    } else {
                        // Continuous Terminal Output
                        val scrollState = rememberLazyListState()
                        LaunchedEffect(terminalOutput) {
                            if (terminalOutput.isNotBlank()) {
                                scrollState.scrollToItem(0)
                            }
                        }

                        LazyColumn(
                            state = scrollState,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(PolishTerminalBg)
                                .padding(12.dp)
                        ) {
                            item {
                                Text(
                                    text = terminalOutput,
                                    color = PolishSageGreen,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            // 🧱 3. Latency & Device Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Latency box
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(PolishWidget)
                        .border(1.dp, PolishBorder, RoundedCornerShape(24.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "LATENCY",
                        color = PolishTextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (lastLatencyMs > 0) "${lastLatencyMs}ms" else "-- ms",
                        color = PolishAccentBlue,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Device Target Connection details
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(PolishWidget)
                        .border(1.dp, PolishBorder, RoundedCornerShape(24.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "SOVEREIGN LINK",
                        color = PolishTextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = settingsState?.let { "${it.username}@${it.host}" } ?: "Config Required",
                        color = PolishTextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 🧱 4. Bottom Controls (Giant Microphone Loop Switch)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsating Acoustic Wave Effect
                    if (isRecordingActive) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulseRing")
                        val animScale by infiniteTransition.animateFloat(
                            initialValue = 0.95f,
                            targetValue = 1.6f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ), label = "animScale"
                        )
                        val animAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.6f,
                            targetValue = 0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ), label = "animAlpha"
                        )

                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .scale(animScale)
                                .clip(CircleShape)
                                .background(PolishAccentBlue.copy(alpha = animAlpha))
                        )
                    }

                    // Primary Toggle Button
                    IconButton(
                        onClick = {
                            if (!hasAudioPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                if (isRecordingActive) {
                                    viewModel.stopListeningSession()
                                } else {
                                    viewModel.startListeningSession()
                                }
                            }
                        },
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(if (isRecordingActive) PolishAccentBlue else PolishWidget)
                            .border(1.dp, PolishBorder, CircleShape)
                    ) {
                        MicrophoneIcon(
                            active = isRecordingActive,
                            color = if (isRecordingActive) PolishDeepBlue else PolishAccentBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Microphone Status Badge text
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isRecordingActive) PolishSageGreen else Color(0xFFEF5350))
                    )
                    Text(
                        text = if (isRecordingActive) "MIC ACTIVE: RAY-BAN V2" else "MIC MUTED / COLD",
                        color = PolishTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }
    }

    // ⚙️ Connection Settings Modal Overlay
    if (showSettingsDialog) {
        settingsState?.let { activeSettings ->
            SettingsOverlay(
                initialSettings = activeSettings,
                onDismiss = { showSettingsDialog = false },
                onSave = { updated ->
                    viewModel.saveSettings(updated)
                    showSettingsDialog = false
                },
                onClearHistory = {
                    viewModel.clearHistory()
                    showSettingsDialog = false
                }
            )
        }
    }
}

@Composable
fun RowScope.TabItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val tabBg by animateColorAsState(
        targetValue = if (isSelected) PolishSurface else Color.Transparent,
        label = "tabBg"
    )
    val tabBorder = if (isSelected) BorderStroke(1.dp, PolishBorder) else null

    Box(
        modifier = Modifier
            .weight(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tabBg)
            .then(if (tabBorder != null) Modifier.border(tabBorder, RoundedCornerShape(10.dp)) else Modifier)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) PolishTextWhite else PolishTextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
fun InteractionBubble(log: VoiceLog) {
    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))

    Column(modifier = Modifier.fillMaxWidth()) {
        
        // Spoken Text (User)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "YOU VOICE INPUT",
                    color = PolishAccentBlue,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                )
                Text(
                    text = timeStr,
                    color = PolishTextMuted,
                    fontSize = 8.sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                    .background(PolishHighlightBlue)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .widthIn(max = 280.dp)
            ) {
                Text(
                    text = log.textSent,
                    color = PolishTextWhite,
                    fontSize = 14.sp,
                    lineHeight = 19.sp
                )
            }
        }

        // Response text (Patricia / System response)
        when (log.status) {
            "CONNECTING" -> {
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp, bottom = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PolishWidget)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 1.5.dp,
                            color = PolishAccentBlue
                        )
                        Text(
                            text = "SSH Command Dialing System...",
                            color = PolishAccentBlue,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            "ERROR" -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "SYSTEM GATEWAY FAULT",
                        color = Color(0xFFEF5350),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 0.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                            .background(Color(0xFF5C2D2F))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .widthIn(max = 280.dp)
                    ) {
                        Text(
                            text = log.errorDetails ?: "SSH stream socket dropped.",
                            color = Color(0xFFFFCDD2),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            "SUCCESS" -> {
                log.responseReceived?.let { response ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "BEACON RESPONSE",
                            color = Color(0xFFD6BEE4),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 0.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                                .background(PolishPlum)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .widthIn(max = 280.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "🦉", fontSize = 16.sp)
                                Text(
                                    text = response,
                                    color = PolishTextWhite,
                                    fontSize = 14.sp,
                                    lineHeight = 19.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsOverlay(
    initialSettings: ConnectionSettings,
    onDismiss: () -> Unit,
    onSave: (ConnectionSettings) -> Unit,
    onClearHistory: () -> Unit
) {
    var host by remember { mutableStateOf(initialSettings.host) }
    var username by remember { mutableStateOf(initialSettings.username) }
    var port by remember { mutableStateOf(initialSettings.port.toString()) }
    var authType by remember { mutableStateOf(initialSettings.authType) }
    var password by remember { mutableStateOf(initialSettings.password) }
    var privateKey by remember { mutableStateOf(initialSettings.privateKey) }
    var commandTemplate by remember { mutableStateOf(initialSettings.commandTemplate) }
    var autoReadTts by remember { mutableStateOf(initialSettings.autoReadTts) }
    var continuousLoop by remember { mutableStateOf(initialSettings.continuousLoop) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, PolishBorder, RoundedCornerShape(28.dp)),
            colors = CardDefaults.cardColors(
                containerColor = PolishSurface,
                contentColor = PolishTextWhite
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transmission Specs",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PolishAccentBlue
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close specifications panel",
                            tint = PolishTextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Host IP field
                SpecTextField(
                    label = "Server Node IP (Tailscale / Host)",
                    value = host,
                    onValueChange = { host = it }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Username ID
                    Box(modifier = Modifier.weight(1.5f)) {
                        SpecTextField(
                            label = "SSH User",
                            value = username,
                            onValueChange = { username = it }
                        )
                    }
                    // Port
                    Box(modifier = Modifier.weight(0.8f)) {
                        SpecTextField(
                            label = "Port ID",
                            value = port,
                            onValueChange = { port = it },
                            keyboardType = KeyboardType.Number
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Auth Selection Toggle
                Text(
                    text = "SECURITY CREDENTIAL METHOD",
                    color = PolishTextLabel,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PolishWidget)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (authType == "PASSWORD") PolishSurface else Color.Transparent)
                            .clickable { authType = "PASSWORD" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "PASSWORD credentials",
                            color = if (authType == "PASSWORD") PolishAccentBlue else PolishTextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (authType == "PRIVATE_KEY") PolishSurface else Color.Transparent)
                            .clickable { authType = "PRIVATE_KEY" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "SSH Private Key",
                            color = if (authType == "PRIVATE_KEY") PolishAccentBlue else PolishTextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (authType == "PASSWORD") {
                    SpecTextField(
                        label = "SSH Node Password",
                        value = password,
                        onValueChange = { password = it },
                        isSecret = true
                    )
                } else {
                    SpecTextField(
                        label = "Private Key Stream (PEM format)",
                        value = privateKey,
                        onValueChange = { privateKey = it },
                        singleLine = false,
                        modifier = Modifier.height(100.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Command string formatter template
                SpecTextField(
                    label = "Command Template (%s injection payload)",
                    value = commandTemplate,
                    onValueChange = { commandTemplate = it }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Tweak options
                Text(
                    text = "TRANSMISSION PREFERENCES",
                    color = PolishTextLabel,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto read response (TTS)", fontSize = 12.sp, color = PolishTextWhite)
                    Switch(
                        checked = autoReadTts,
                        onCheckedChange = { autoReadTts = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PolishAccentBlue,
                            checkedTrackColor = PolishHighlightBlue,
                            uncheckedThumbColor = PolishTextMuted,
                            uncheckedTrackColor = PolishWidget
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Continuous listening loop", fontSize = 12.sp, color = PolishTextWhite)
                    Switch(
                        checked = continuousLoop,
                        onCheckedChange = { continuousLoop = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PolishAccentBlue,
                            checkedTrackColor = PolishHighlightBlue,
                            uncheckedThumbColor = PolishTextMuted,
                            uncheckedTrackColor = PolishWidget
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions Group
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onClearHistory,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Clear Logs", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val parsedPort = port.toIntOrNull() ?: 22
                            onSave(
                                ConnectionSettings(
                                    host = host,
                                    username = username,
                                    port = parsedPort,
                                    authType = authType,
                                    password = password,
                                    privateKey = privateKey,
                                    commandTemplate = commandTemplate,
                                    autoReadTts = autoReadTts,
                                    continuousLoop = continuousLoop
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PolishAccentBlue,
                            contentColor = PolishDeepBlue
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Apply Specs", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    isSecret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(Locale.getDefault()),
            fontSize = 8.5.sp,
            color = PolishTextLabel,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp),
            letterSpacing = 0.3.sp
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PolishBorder, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = PolishWidget,
                unfocusedContainerColor = PolishWidget,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = PolishTextWhite,
                unfocusedTextColor = PolishTextWhite,
                cursorColor = PolishAccentBlue
            ),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 11.sp,
                fontFamily = if (!singleLine) FontFamily.Monospace else FontFamily.SansSerif
            )
        )
    }
}

package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.EmergencyContact
import com.example.data.IncidentReport
import java.text.SimpleDateFormat
import java.util.*

// Custom Color Constants matching the Cosmic Slate and Safety Crimson theme
val SlateBlack = Color(0xFF0A0C10)
val DeepSlate = Color(0xFF131720)
val SurfaceGray = Color(0xFF1E2430)
val BorderGray = Color(0xFF2C3545)
val CrimsonRed = Color(0xFFFF3366)
val SafetyGreen = Color(0xFF00E676)
val CyberOrange = Color(0xFFFFA000)
val IceWhite = Color(0xFFECEFF1)
val MutedSlate = Color(0xFF78909C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KavachApp(viewModel: KavachViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val codePhrase by viewModel.codePhrase.collectAsStateWithLifecycle()
    val currentTranscript by viewModel.currentTranscript.collectAsStateWithLifecycle()
    val countdown by viewModel.countdown.collectAsStateWithLifecycle()
    val defenseStepText by viewModel.defenseStepText.collectAsStateWithLifecycle()
    val contactsNotifiedLogs by viewModel.contactsNotifiedLogs.collectAsStateWithLifecycle()
    val isGeneratingReport by viewModel.isGeneratingReport.collectAsStateWithLifecycle()
    val currentReportDraft by viewModel.currentReportDraft.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val selectedSafeZone by viewModel.selectedSafeZone.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val isSosQueued by viewModel.isSosQueued.collectAsStateWithLifecycle()
    val commsAgentLog by viewModel.commsAgentLog.collectAsStateWithLifecycle()

    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()

    var showAddContactDialog by remember { mutableStateOf(false) }
    var showHistoricalReportSheet by remember { mutableStateOf(false) }
    var selectedHistoricalReport by remember { mutableStateOf<IncidentReport?>(null) }
    var micPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        micPermissionGranted = isGranted
        if (isGranted) {
            viewModel.toggleSpeechListening(true)
        }
    }

    // Gradient background
    val appBackgroundBrush = Brush.verticalGradient(
        colors = listOf(SlateBlack, DeepSlate)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appBackgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        when (uiState) {
            KavachState.MONITORING -> {
                MonitoringScreen(
                    codePhrase = codePhrase,
                    currentTranscript = currentTranscript,
                    isListening = isListening,
                    selectedSafeZone = selectedSafeZone,
                    safeZones = viewModel.safeZones,
                    contacts = contacts,
                    onToggleListening = {
                        if (micPermissionGranted) {
                            viewModel.toggleSpeechListening(!isListening)
                        } else {
                            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onTriggerManualSafety = {
                        viewModel.triggerSafety("MANUAL_SLIDE", "Manual activation slide triggered.")
                    },
                    onAddContactClick = { showAddContactDialog = true },
                    onDeleteContact = { id -> viewModel.deleteEmergencyContact(id) },
                    onSafeZoneChange = { viewModel.changeSafeZone(it) },
                    onCodePhraseChange = { viewModel.updateCodePhrase(it) },
                    onSimulatePhraseTrigger = {
                        viewModel.simulateSpeechInput(codePhrase)
                    },
                    onViewHistoryClick = { showHistoricalReportSheet = true }
                )
            }
            KavachState.CHECKING_IN -> {
                CheckInScreen(
                    countdown = countdown,
                    currentTranscript = currentTranscript,
                    onSafeResponse = { viewModel.triggerDeescalate() },
                    onDangerResponse = { viewModel.triggerDefense() },
                    onSimulateSafeVoice = { viewModel.simulateSpeechInput("I'm safe") },
                    onSimulateDistressVoice = { viewModel.simulateSpeechInput("Help me! I am in danger.") }
                )
            }
            KavachState.ACTIVE_DEFENSE -> {
                ActiveDefenseScreen(
                    defenseStepText = defenseStepText,
                    selectedSafeZone = selectedSafeZone,
                    contactsNotifiedLogs = contactsNotifiedLogs,
                    isOffline = isOffline,
                    isSosQueued = isSosQueued,
                    commsAgentLog = commsAgentLog,
                    onResolveClick = { viewModel.triggerResolve() }
                )
            }
            KavachState.RESOLVED -> {
                ResolvedScreen(
                    isGenerating = isGeneratingReport,
                    reportText = currentReportDraft,
                    onFinishClick = { viewModel.resetToHub() }
                )
            }
        }

        // Dialogs & Bottom Sheets
        if (showAddContactDialog) {
            AddContactDialog(
                onDismiss = { showAddContactDialog = false },
                onAdd = { name, phone, rel ->
                    viewModel.addEmergencyContact(name, phone, rel)
                    showAddContactDialog = false
                }
            )
        }

        // Global Overlay for Offline Mode Toggle (Mode Handoff)
        Box(modifier = Modifier.fillMaxSize().padding(top = 16.dp, end = 16.dp), contentAlignment = Alignment.TopEnd) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isOffline) "DARK SURVIVAL (OFF)" else "GHOST OPERATOR (ON)",
                    color = if (isOffline) CyberOrange else SafetyGreen,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(end = 8.dp)
                )
                Switch(
                    checked = !isOffline,
                    onCheckedChange = { viewModel.toggleOfflineMode(!it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DeepSlate,
                        checkedTrackColor = SafetyGreen,
                        uncheckedThumbColor = DeepSlate,
                        uncheckedTrackColor = CyberOrange
                    )
                )
            }
        }

        if (showHistoricalReportSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showHistoricalReportSheet = false
                    selectedHistoricalReport = null
                },
                containerColor = DeepSlate
            ) {
                HistoricalReportsSheetContent(
                    reports = reports,
                    selectedReport = selectedHistoricalReport,
                    onSelectReport = { selectedHistoricalReport = it },
                    onBackToList = { selectedHistoricalReport = null }
                )
            }
        }
    }
}

// ----------------------------------------------------
// MONITORING SCREEN
// ----------------------------------------------------
@Composable
fun MonitoringScreen(
    codePhrase: String,
    currentTranscript: String,
    isListening: Boolean,
    selectedSafeZone: String,
    safeZones: List<String>,
    contacts: List<EmergencyContact>,
    onToggleListening: () -> Unit,
    onTriggerManualSafety: () -> Unit,
    onAddContactClick: () -> Unit,
    onDeleteContact: (Int) -> Unit,
    onSafeZoneChange: (String) -> Unit,
    onCodePhraseChange: (String) -> Unit,
    onSimulatePhraseTrigger: () -> Unit,
    onViewHistoryClick: () -> Unit
) {
    var expandedZoneMenu by remember { mutableStateOf(false) }
    var editingPhrase by remember { mutableStateOf(false) }
    var phraseInput by remember { mutableStateOf(codePhrase) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 72.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Identity Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "KAVACH",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 3.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = CrimsonRed
                    )
                    Text(
                        text = "ZERO-UI SAFETY GUARDIAN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MutedSlate
                    )
                }

                IconButton(
                    onClick = onViewHistoryClick,
                    modifier = Modifier
                        .testTag("history_button")
                        .background(SurfaceGray, CircleShape)
                        .border(1.dp, BorderGray, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Incident History",
                        tint = IceWhite
                    )
                }
            }
        }

        // Animated Radar/Microphone Status Visualizer
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseRadius1 by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = twinSpec(1500),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "pulse_inner"
                )
                val pulseAlpha1 by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = twinSpec(1500),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "alpha_inner"
                )

                // Background Pulse Rings when active
                if (isListening) {
                    Canvas(modifier = Modifier.size(180.dp)) {
                        drawCircle(
                            color = CrimsonRed.copy(alpha = 0.15f * pulseAlpha1),
                            radius = size.minDimension / 2 * pulseRadius1,
                            style = Stroke(width = 4.dp.toPx())
                        )
                        drawCircle(
                            color = CrimsonRed.copy(alpha = 0.05f * pulseAlpha1),
                            radius = size.minDimension / 2 * pulseRadius1 * 1.3f,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                // Core Listening Node Button
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = if (isListening) listOf(CrimsonRed.copy(alpha = 0.8f), SlateBlack)
                                else listOf(SurfaceGray, SlateBlack)
                            )
                        )
                        .border(
                            2.dp,
                            if (isListening) CrimsonRed else BorderGray,
                            CircleShape
                        )
                        .clickable { onToggleListening() }
                        .testTag("microphone_status_node"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = "Toggle Guardian Listening",
                        modifier = Modifier.size(40.dp),
                        tint = if (isListening) IceWhite else MutedSlate
                    )
                }

                // Status Indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(SurfaceGray, RoundedCornerShape(20.dp))
                        .border(1.dp, BorderGray, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, java.lang.Integer.max(6, 6).dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (isListening) CrimsonRed else MutedSlate, CircleShape)
                        )
                        Text(
                            text = if (isListening) "SECURED & LISTENING" else "GUARDIAN STANDBY",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isListening) IceWhite else MutedSlate
                        )
                    }
                }
            }
        }

        // Tactical Trigger Slider
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CrimsonRed.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                    .border(1.dp, CrimsonRed.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "EMERGENCY MAN OVERRIDE",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = CrimsonRed
                    )
                    
                    Button(
                        onClick = onTriggerManualSafety,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("manual_sos_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "TRIGGER CODE RED SHIELD",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
                            )
                            Icon(Icons.AutoMirrored.Default.ArrowForward, contentDescription = "Trigger Safety")
                        }
                    }
                }
            }
        }

        // Active Code Phrase Configuration
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DeepSlate),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderGray)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Hearing, contentDescription = "Wake Word", tint = CrimsonRed)
                            Text(
                                "Ambient Code Phrase",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = IceWhite
                            )
                        }

                        IconButton(
                            onClick = {
                                if (editingPhrase) {
                                    onCodePhraseChange(phraseInput)
                                }
                                editingPhrase = !editingPhrase
                            },
                            modifier = Modifier.testTag("edit_phrase_button")
                        ) {
                            Icon(
                                imageVector = if (editingPhrase) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = if (editingPhrase) "Save Phrase" else "Edit Phrase",
                                tint = if (editingPhrase) SafetyGreen else IceWhite
                            )
                        }
                    }

                    if (editingPhrase) {
                        OutlinedTextField(
                            value = phraseInput,
                            onValueChange = { phraseInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CrimsonRed,
                                unfocusedBorderColor = BorderGray,
                                focusedTextColor = IceWhite,
                                unfocusedTextColor = IceWhite
                            )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SlateBlack, RoundedCornerShape(8.dp))
                                .border(1.dp, BorderGray, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "\"$codePhrase\"",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = CrimsonRed
                            )
                        }
                    }

                    // Simulated Trigger Shortcut for Demo Purposes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "No wake word is needed. Just speak the phrase naturally.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedSlate
                        )

                        TextButton(
                            onClick = onSimulatePhraseTrigger,
                            colors = ButtonDefaults.textButtonColors(contentColor = CrimsonRed),
                            modifier = Modifier.testTag("simulate_phrase_button")
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Simulate", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Simulate Ambient", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    // Transcript log
                    if (isListening && currentTranscript.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Heard: \"$currentTranscript\"",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MutedSlate,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Autonomous Safe Zone Routing Config
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DeepSlate),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderGray)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Security, contentDescription = "Defense Plan", tint = CrimsonRed)
                        Text(
                            "Autonomous Safe Zone Route",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = IceWhite
                        )
                    }

                    Box {
                        OutlinedCard(
                            onClick = { expandedZoneMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.outlinedCardColors(containerColor = SlateBlack),
                            border = BorderStroke(1.dp, BorderGray)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(selectedSafeZone, color = IceWhite, style = MaterialTheme.typography.bodyMedium)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Expand Safe Zones", tint = IceWhite)
                            }
                        }

                        DropdownMenu(
                            expanded = expandedZoneMenu,
                            onDismissRequest = { expandedZoneMenu = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DeepSlate)
                                .border(1.dp, BorderGray)
                        ) {
                            safeZones.forEach { zone ->
                                DropdownMenuItem(
                                    text = { Text(zone, color = IceWhite) },
                                    onClick = {
                                        onSafeZoneChange(zone)
                                        expandedZoneMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        text = "Guardian automatically routes and whispers directions to this safe spot if threat is detected.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedSlate
                    )
                }
            }
        }

        // Emergency Contacts Setup Panel
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DeepSlate),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderGray)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Contacts, contentDescription = "Emergency Contacts", tint = CrimsonRed)
                            Text(
                                "SOS Contacts",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = IceWhite
                            )
                        }

                        IconButton(
                            onClick = onAddContactClick,
                            modifier = Modifier
                                .size(28.dp)
                                .background(CrimsonRed.copy(alpha = 0.2f), CircleShape)
                                .testTag("add_contact_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Contact", tint = CrimsonRed, modifier = Modifier.size(16.dp))
                        }
                    }

                    if (contacts.isEmpty()) {
                        Text(
                            "No contacts added. Tap the '+' button above to register an emergency responder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedSlate
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            contacts.forEach { contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SlateBlack, RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                contact.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = IceWhite
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .background(CrimsonRed.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(contact.relation.uppercase(), style = MaterialTheme.typography.labelSmall, color = CrimsonRed)
                                            }
                                        }
                                        Text(contact.phone, style = MaterialTheme.typography.bodySmall, color = MutedSlate)
                                    }

                                    IconButton(
                                        onClick = { onDeleteContact(contact.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove Contact", tint = CrimsonRed.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// CHECK-IN SCREEN
// ----------------------------------------------------
@Composable
fun CheckInScreen(
    countdown: Int,
    currentTranscript: String,
    onSafeResponse: () -> Unit,
    onDangerResponse: () -> Unit,
    onSimulateSafeVoice: () -> Unit,
    onSimulateDistressVoice: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val ambientRingScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = twinSpec(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientRingScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Large Warning Pulse Node
        Box(
            modifier = Modifier.size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = CyberOrange.copy(alpha = 0.1f),
                    radius = (size.minDimension / 2) * ambientRingScale
                )
            }

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(SlateBlack, CircleShape)
                    .border(4.dp, CyberOrange, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = countdown.toString(),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = CyberOrange
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "GUARDIAN CHECK-IN",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp),
            color = CyberOrange
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Speak clearly to answer, or tap below to confirm status. Escapes automatically if silence expires.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = IceWhite,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        if (currentTranscript.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Heard: \"$currentTranscript\"",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = MutedSlate
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Quick responses for demoing easily
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = onSimulateSafeVoice,
                colors = ButtonDefaults.textButtonColors(contentColor = SafetyGreen),
                modifier = Modifier
                    .weight(1f)
                    .testTag("simulate_voice_safe_button")
            ) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = "Simulate Safe Voice", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Voice \"Safe\"", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }

            TextButton(
                onClick = onSimulateDistressVoice,
                colors = ButtonDefaults.textButtonColors(contentColor = CrimsonRed),
                modifier = Modifier
                    .weight(1f)
                    .testTag("simulate_voice_distress_button")
            ) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = "Simulate Distress Voice", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Voice \"Distress\"", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Primary Large Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onSafeResponse,
                colors = ButtonDefaults.buttonColors(containerColor = SafetyGreen),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("safe_checkin_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Check, contentDescription = "Safe", tint = SlateBlack)
                    Text("I'M SAFE", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = SlateBlack))
                }
            }

            Button(
                onClick = onDangerResponse,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("distress_checkin_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = "Unsafe")
                    Text("DANGER SOS", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
                }
            }
        }
    }
}

// ----------------------------------------------------
// ACTIVE DEFENSE SCREEN (DEAD-SCREEN ILLUSION)
// ----------------------------------------------------
@Composable
fun ActiveDefenseScreen(
    defenseStepText: String,
    selectedSafeZone: String,
    contactsNotifiedLogs: List<String>,
    isOffline: Boolean,
    isSosQueued: Boolean,
    commsAgentLog: String?,
    onResolveClick: () -> Unit
) {
    var showTacticalHUD by remember { mutableStateOf(false) }

    if (!showTacticalHUD && isOffline) {
        // DARK SURVIVAL: DEAD SCREEN ILLUSION
        // Completely black screen with no backlight elements.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            showTacticalHUD = true
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Invisible, ultra-low alpha instruction to guide the presenter
            Text(
                "Double tap anywhere to reveal Tactical HUD (or restore network)",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.05f)
            )
        }
    } else {
        // GHOST OPERATOR (OR HUD VIEW OF DARK SURVIVAL)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 72.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header with pulsing Warning Light
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (isOffline) "DARK SURVIVAL ENGAGED" else "GHOST OPERATOR ENGAGED",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                        color = CrimsonRed
                    )
                    Text(
                        text = if (isOffline) "ON-DEVICE GEMMA 4 ACTIVE" else "AUTONOMOUS ORCHESTRATION",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MutedSlate
                    )
                }

                val infiniteTransition = rememberInfiniteTransition(label = "warning")
                val warningAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = twinSpec(600),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "warningAlpha"
                )

                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .alpha(warningAlpha)
                        .background(CrimsonRed, CircleShape)
                )
            }

            // Radar compass navigation illustration
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(SlateBlack, CircleShape)
                    .border(2.dp, CrimsonRed.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "radar_spin")
                val rotationAngle by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(4000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "rotation"
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw target direction indicator
                    drawCircle(
                        color = CrimsonRed.copy(alpha = 0.08f),
                        radius = size.minDimension / 2
                    )
                }

                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Safe Zone Indicator",
                    modifier = Modifier
                        .size(48.dp)
                        .animateContentSize(),
                    tint = CrimsonRed
                )
            }

            // Whisper Directions Block
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceGray),
                border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "CURRENT WHISPER DIRECTIONS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = CrimsonRed
                    )

                    Text(
                        text = "\"$defenseStepText\"",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        ),
                        color = IceWhite
                    )

                    Text(
                        text = "Target: $selectedSafeZone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSlate
                    )
                }
            }

            // Notified Contacts Logs Console
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateBlack),
                border = BorderStroke(1.dp, BorderGray),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "DISPATCH TRANSMISSION OUTBOUND",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = SafetyGreen
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isSosQueued) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DeepSlate, RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.HourglassTop, contentDescription = "SOS Queued", tint = CyberOrange, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "SOS Beacon QUEUED. Will transmit when signal returns.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = CyberOrange
                                    )
                                }
                            }
                        }

                        if (commsAgentLog != null) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DeepSlate, RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.PhoneInTalk, contentDescription = "Comms Call", tint = SafetyGreen, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = commsAgentLog,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = IceWhite
                                    )
                                }
                            }
                        }

                        items(contactsNotifiedLogs) { log ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DeepSlate, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Sms, contentDescription = "SMS Outbound", tint = if (isOffline) CyberOrange else SafetyGreen, modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (isOffline) log.replace("Sent SOS SMS", "Queued SOS SMS") else log,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = if (isOffline) CyberOrange else IceWhite
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showTacticalHUD = false },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MutedSlate),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = "Hide Screen")
                    Spacer(Modifier.width(8.dp))
                    Text("LOCK VIEW", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }

                Button(
                    onClick = onResolveClick,
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyGreen),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("safe_resolve_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.ThumbUp, contentDescription = "Resolved", tint = SlateBlack)
                        Text("I'M SAFE NOW", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black, color = SlateBlack))
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// RESOLVED SCREEN (SCRIBE WRITING THE REPORT)
// ----------------------------------------------------
@Composable
fun ResolvedScreen(
    isGenerating: Boolean,
    reportText: String,
    onFinishClick: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 72.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (isGenerating) {
            // Scribe loading state
            Spacer(modifier = Modifier.weight(1f))

            CircularProgressIndicator(
                color = CrimsonRed,
                modifier = Modifier.size(60.dp),
                strokeWidth = 6.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "SCRIBE IS ASSEMBLING AUDIT TRAIL...",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp),
                color = CrimsonRed
            )

            Text(
                text = "Synthesizing captured audio transcripts, notified emergency networks, and safety routing into a professional security report via Gemini AI.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedSlate,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.weight(1f))
        } else {
            // Report Display State
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "INCIDENT AUDITED",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                        color = SafetyGreen
                    )
                    Text(
                        text = "SCRIBE REPORT GENERATED",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MutedSlate
                    )
                }

                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(reportText))
                        // Show brief feedback toast
                    },
                    modifier = Modifier.testTag("copy_report_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Report Content", tint = IceWhite)
                }
            }

            // Structured Scribe Legal Document Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceGray),
                border = BorderStroke(1.dp, BorderGray),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = reportText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 18.sp
                            ),
                            color = IceWhite
                        )
                    }
                }
            }

            Button(
                onClick = onFinishClick,
                colors = ButtonDefaults.buttonColors(containerColor = SafetyGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("save_and_finish_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Home, contentDescription = "Exit to Hub", tint = SlateBlack)
                    Text(
                        "SAVE & RETURN TO SAFETY HUB",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black, color = SlateBlack)
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------
// HISTORICAL REPORTS BOTTOM SHEET CONTENT
// ----------------------------------------------------
@Composable
fun HistoricalReportsSheetContent(
    reports: List<IncidentReport>,
    selectedReport: IncidentReport?,
    onSelectReport: (IncidentReport) -> Unit,
    onBackToList: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .background(SlateBlack)
            .padding(16.dp)
    ) {
        if (selectedReport == null) {
            // Display List of Historic Reports
            Text(
                "SECURED INCIDENT AUDITS",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                color = CrimsonRed,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (reports.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No incident reports logged yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSlate
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(reports) { report ->
                        val dateString = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
                            .format(Date(report.timestamp))

                        Card(
                            onClick = { onSelectReport(report) },
                            colors = CardDefaults.cardColors(containerColor = DeepSlate),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderGray)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(CrimsonRed.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            report.triggerType.uppercase(),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = CrimsonRed
                                        )
                                    }
                                    Text(
                                        dateString,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MutedSlate
                                    )
                                }

                                Text(
                                    "Heard: \"${report.capturedTranscript}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IceWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    "Safe Route: ${report.safetyRouteTaken}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SafetyGreen
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // View Detail of Selected Historical Report
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBackToList) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = IceWhite)
                }

                Text(
                    "INCIDENT RECORD",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = IceWhite
                )

                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(selectedReport.generatedReport))
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Report Content", tint = IceWhite)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceGray),
                border = BorderStroke(1.dp, BorderGray),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    item {
                        Text(
                            text = selectedReport.generatedReport,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 18.sp
                            ),
                            color = IceWhite
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// ADD CONTACT DIALOG
// ----------------------------------------------------
@Composable
fun AddContactDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("Guardian") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Register Responder",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = IceWhite
            )
        },
        containerColor = DeepSlate,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = MutedSlate) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonRed,
                        unfocusedBorderColor = BorderGray,
                        focusedTextColor = IceWhite,
                        unfocusedTextColor = IceWhite
                    )
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number", color = MutedSlate) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonRed,
                        unfocusedBorderColor = BorderGray,
                        focusedTextColor = IceWhite,
                        unfocusedTextColor = IceWhite
                    )
                )

                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text("Relationship (e.g. Mom, Guard)", color = MutedSlate) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonRed,
                        unfocusedBorderColor = BorderGray,
                        focusedTextColor = IceWhite,
                        unfocusedTextColor = IceWhite
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotEmpty() && phone.isNotEmpty()) onAdd(name, phone, relation) },
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
            ) {
                Text("REGISTER")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MutedSlate)
            ) {
                Text("CANCEL")
            }
        }
    )
}

// Utility Tween Spec for Animations
private fun <T> twinSpec(duration: Int): TweenSpec<T> {
    return tween(durationMillis = duration, easing = LinearEasing)
}

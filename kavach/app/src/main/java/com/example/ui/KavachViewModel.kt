package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiClient
import com.example.data.AppDatabase
import com.example.data.EmergencyContact
import com.example.data.IncidentReport
import com.example.data.IncidentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class KavachState {
    MONITORING,
    CHECKING_IN,
    ACTIVE_DEFENSE,
    RESOLVED
}

class KavachViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val db = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = IncidentRepository(db.incidentDao())

    // UI States
    private val _uiState = MutableStateFlow(KavachState.MONITORING)
    val uiState: StateFlow<KavachState> = _uiState.asStateFlow()

    private val _codePhrase = MutableStateFlow("battery at 2 percent")
    val codePhrase: StateFlow<String> = _codePhrase.asStateFlow()

    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript.asStateFlow()

    private val _countdown = MutableStateFlow(10)
    val countdown: StateFlow<Int> = _countdown.asStateFlow()

    private val _defenseStepText = MutableStateFlow("Initializing safety route...")
    val defenseStepText: StateFlow<String> = _defenseStepText.asStateFlow()

    private val _contactsNotifiedLogs = MutableStateFlow<List<String>>(emptyList())
    val contactsNotifiedLogs: StateFlow<List<String>> = _contactsNotifiedLogs.asStateFlow()

    private val _isGeneratingReport = MutableStateFlow(false)
    val isGeneratingReport: StateFlow<Boolean> = _isGeneratingReport.asStateFlow()

    private val _currentReportDraft = MutableStateFlow("")
    val currentReportDraft: StateFlow<String> = _currentReportDraft.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _isSosQueued = MutableStateFlow(false)
    val isSosQueued: StateFlow<Boolean> = _isSosQueued.asStateFlow()

    private val _commsAgentLog = MutableStateFlow<String?>(null)
    val commsAgentLog: StateFlow<String?> = _commsAgentLog.asStateFlow()

    private val _selectedSafeZone = MutableStateFlow("CVS 24/7 Pharmacy (0.2 mi)")
    val selectedSafeZone: StateFlow<String> = _selectedSafeZone.asStateFlow()

    // Room Database Observables
    val reports: StateFlow<List<IncidentReport>> = repository.allReports
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contacts: StateFlow<List<EmergencyContact>> = repository.allContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // TTS Engine
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    // Speech Recognizer
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: Intent? = null

    // Job Timers
    private var checkInTimerJob: Job? = null
    private var defenseWhisperJob: Job? = null
    private var transcriptTimerJob: Job? = null
    private var sirenJob: Job? = null

    // Siren
    private var toneGenerator: ToneGenerator? = null

    // Safe Zones Mock Configuration
    val safeZones = listOf(
        "CVS 24/7 Pharmacy (0.2 mi)",
        "Campus Safety Dispatch (0.4 mi)",
        "Metro Transit Central (0.5 mi)",
        "24-Hour Diner & Cafe (0.1 mi)"
    )

    // Dynamic Defense Whisper Scripts based on Safe Zone
    private val whisperScripts = mapOf(
        "CVS 24/7 Pharmacy (0.2 mi)" to listOf(
            "Kavach activated. Screen is fully secured. I am whispering your safety route now.",
            "Walk straight for 50 meters toward the brighter street lights. Keep your earpiece in.",
            "Turn right in 20 meters. CVS is on your left next to the public plaza.",
            "CVS Entrance is 10 meters ahead. Step inside the lobby where it is bright and populated.",
            "You have arrived at the safe zone. Tap the green button on your screen to complete."
        ),
        "Campus Safety Dispatch (0.4 mi)" to listOf(
            "Kavach activated. Screen is secured. Escaping toward the Campus Dispatch Center.",
            "Walk straight for 100 meters. Follow the blue campus light towers.",
            "Cross the street safely at the pedestrian lane. Dispatch lobby is on your right.",
            "Campus Security door is 10 meters ahead. Officers are on duty.",
            "Safe zone reached. De-escalate when secure by double-tapping."
        ),
        "Metro Transit Central (0.5 mi)" to listOf(
            "Kavach activated. Secure screen mode engaged. Heading to the Metro station.",
            "Proceed south for 150 meters. Keep a normal walking pace.",
            "Take a left at the main intersection. The metro stairs are well-lit.",
            "Metro station turnstiles are 15 meters ahead. Stay near the service booth.",
            "Arrived at the Metro safe spot. Tap safe when ready."
        ),
        "24-Hour Diner & Cafe (0.1 mi)" to listOf(
            "Kavach activated. Secure screen mode engaged. Escaping to the Diner.",
            "Walk straight for 30 meters. Turn right at the corner.",
            "Diner entrance is right there. It is brightly lit and staff are present.",
            "Take a seat inside the booth. Take your time.",
            "Arrived at safe zone. Clear safety alert."
        )
    )

    init {
        // Initialize Siren
        toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)

        // Initialize TextToSpeech
        tts = TextToSpeech(application, this)

        // Initialize SpeechRecognizer if supported
        Handler(Looper.getMainLooper()).post {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(application)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(application)
                    setupSpeechListener()
                }
            } catch (e: Exception) {
                Log.e("Kavach", "Speech recognition init error: ${e.message}")
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setPitch(0.95f) // Warm, reassuring, neutral low tone
            tts?.setSpeechRate(0.85f) // Spoken slowly and clearly
            isTtsReady = true
        }
    }

    private fun setupSpeechListener() {
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsd: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                // Auto-restart listening if we are still active in MONITORING state
                if (_uiState.value == KavachState.MONITORING && _isListening.value) {
                    startRealSpeechRecognizer()
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { processSpeechText(it) }
                if (_uiState.value == KavachState.MONITORING && _isListening.value) {
                    startRealSpeechRecognizer()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { processSpeechText(it) }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun toggleSpeechListening(enabled: Boolean) {
        _isListening.value = enabled
        if (enabled) {
            startRealSpeechRecognizer()
        } else {
            speechRecognizer?.stopListening()
        }
    }

    private fun startRealSpeechRecognizer() {
        try {
            speechRecognizer?.startListening(speechIntent)
        } catch (e: Exception) {
            Log.e("Kavach", "Failed to start speech recognizer: ${e.message}")
        }
    }

    private fun processSpeechText(text: String) {
        _currentTranscript.value = text
        val trigger = _codePhrase.value.lowercase().trim()
        val speechLower = text.lowercase().trim()

        if (_uiState.value == KavachState.MONITORING) {
            if (speechLower.contains(trigger)) {
                triggerSafety("VOICE", text)
            }
        } else if (_uiState.value == KavachState.CHECKING_IN) {
            if (speechLower.contains("safe") || speechLower.contains("i'm safe")) {
                triggerDeescalate()
            } else if (speechLower.contains("help") || speechLower.contains("danger") || speechLower.contains("distress")) {
                triggerDefense()
            }
        }
    }

    // Speech Simulator (for testing/demoing easily)
    fun simulateSpeechInput(text: String) {
        processSpeechText(text)
    }

    // State Machine Transitions
    fun triggerSafety(type: String, transcript: String = "Code phrase spoken silently.") {
        // Stop any monitoring recognizer
        speechRecognizer?.stopListening()

        _currentTranscript.value = transcript
        _uiState.value = KavachState.CHECKING_IN
        _countdown.value = 10

        // Play alert audio and TTS check-in
        speak("Guardian checking in. Are you safe?")

        // Start 10-second countdown
        checkInTimerJob?.cancel()
        checkInTimerJob = viewModelScope.launch {
            while (_countdown.value > 0 && _uiState.value == KavachState.CHECKING_IN) {
                delay(1000)
                _countdown.value -= 1
            }
            // If timer runs out, automatically escalate to ACTIVE_DEFENSE
            if (_uiState.value == KavachState.CHECKING_IN) {
                triggerDefense()
            }
        }
    }

    fun triggerDeescalate() {
        checkInTimerJob?.cancel()
        _uiState.value = KavachState.MONITORING
        _currentTranscript.value = ""
        speak("De-escalated. Monitoring resumed.")
        if (_isListening.value) {
            startRealSpeechRecognizer()
        }
    }

    fun triggerDefense() {
        checkInTimerJob?.cancel()
        _uiState.value = KavachState.ACTIVE_DEFENSE

        if (_isOffline.value) {
            triggerDarkSurvival()
        } else {
            triggerGhostOperator()
        }
    }

    private fun triggerGhostOperator() {
        _isSosQueued.value = false
        stopSiren()
        
        // Dispatch notifications to contacts
        viewModelScope.launch {
            notifyContacts()
        }
        
        // Simulate Comms Agent Voice call
        val primaryContact = contacts.value.firstOrNull()?.name ?: "Teammate SOS"
        _commsAgentLog.value = "Calling $primaryContact via Comms Agent..."
        speak("Connecting Live Comms Agent to $primaryContact.")

        // Start Whisper Loop for routing
        startDefenseWhisperLoop()
    }

    private fun triggerDarkSurvival() {
        defenseWhisperJob?.cancel()
        _commsAgentLog.value = null
        _isSosQueued.value = true
        _defenseStepText.value = "Offline Mode: Ghost Camouflage Active. SOS Queued."
        speak("Signal lost. Dark Survival mode active.")
        startSiren()
    }

    fun toggleOfflineMode(offline: Boolean) {
        _isOffline.value = offline
        if (_uiState.value == KavachState.ACTIVE_DEFENSE) {
            if (offline) {
                triggerDarkSurvival()
            } else {
                triggerGhostOperator()
            }
        }
    }

    private fun startSiren() {
        sirenJob?.cancel()
        sirenJob = viewModelScope.launch {
            while (_uiState.value == KavachState.ACTIVE_DEFENSE && _isOffline.value) {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 2000)
                delay(2500)
            }
        }
    }

    private fun stopSiren() {
        sirenJob?.cancel()
        toneGenerator?.stopTone()
    }

    private fun startDefenseWhisperLoop() {
        val steps = whisperScripts[_selectedSafeZone.value] ?: listOf("Arriving at safety...")
        var stepIndex = 0
        _defenseStepText.value = steps[0]

        defenseWhisperJob?.cancel()
        defenseWhisperJob = viewModelScope.launch {
            while (_uiState.value == KavachState.ACTIVE_DEFENSE) {
                val currentText = steps[stepIndex]
                _defenseStepText.value = currentText
                speak(currentText)

                // Advance steps slowly
                delay(12000)
                if (stepIndex < steps.lastIndex) {
                    stepIndex++
                }
            }
        }
    }

    private suspend fun notifyContacts() {
        val currentContacts = contacts.value
        val list = mutableListOf<String>()
        val googleMapsLink = "https://maps.google.com/?q=37.7749,-122.4194" // Mock location
        
        currentContacts.forEach { contact ->
            val logMsg = "Sent SOS SMS to ${contact.name} (${contact.phone}): \"Kavach SOS alert! I feel unsafe. Tracking path to safe zone. Live location: $googleMapsLink\""
            list.add(logMsg)
        }
        
        if (list.isEmpty()) {
            list.add("Sent SOS SMS to Teammate SOS (+15550199): \"Kavach SOS alert! I feel unsafe. Live location: $googleMapsLink\"")
        }
        
        _contactsNotifiedLogs.value = list
    }

    fun triggerResolve() {
        defenseWhisperJob?.cancel()
        stopSiren()
        _commsAgentLog.value = null
        _isSosQueued.value = false
        _uiState.value = KavachState.RESOLVED
        speak("Incident resolved. Host is secure. Writing report.")

        // Run Scribe via Gemini API to write the report
        viewModelScope.launch {
            _isGeneratingReport.value = true
            val routeDesc = "Escaped safely using whisper audio path to ${_selectedSafeZone.value}."
            val contactsText = contacts.value.joinToString { "${it.name} (${it.phone})" }
                .ifEmpty { "Teammate SOS (+15550199)" }

            val generatedReport = withContext(Dispatchers.IO) {
                GeminiClient.generateIncidentReport(
                    triggerType = if (_currentTranscript.value.isEmpty()) "BUTTON" else "VOICE",
                    transcript = _currentTranscript.value.ifEmpty { "Manual defense slide trigger activated." },
                    contactsNotified = contactsText,
                    routeTaken = routeDesc
                )
            }

            _currentReportDraft.value = generatedReport
            _isGeneratingReport.value = false

            // Save report to Room Database
            val report = IncidentReport(
                triggerType = if (_currentTranscript.value.isEmpty()) "MANUAL_BUTTON" else "VOICE_CODE",
                codePhraseTriggered = _codePhrase.value,
                capturedTranscript = _currentTranscript.value.ifEmpty { "Manual silent shield active." },
                contactsNotified = contactsText,
                safetyRouteTaken = _selectedSafeZone.value,
                generatedReport = generatedReport
            )
            repository.insertReport(report)
        }
    }

    fun resetToHub() {
        stopSiren()
        _uiState.value = KavachState.MONITORING
        _currentTranscript.value = ""
        _currentReportDraft.value = ""
        _contactsNotifiedLogs.value = emptyList()
        _commsAgentLog.value = null
        _isSosQueued.value = false
        if (_isListening.value) {
            startRealSpeechRecognizer()
        }
    }

    // Config Helpers
    fun updateCodePhrase(phrase: String) {
        _codePhrase.value = phrase
    }

    fun changeSafeZone(zoneName: String) {
        _selectedSafeZone.value = zoneName
    }

    fun addEmergencyContact(name: String, phone: String, relation: String) {
        viewModelScope.launch {
            repository.insertContact(EmergencyContact(name = name, phone = phone, relation = relation))
        }
    }

    fun deleteEmergencyContact(id: Int) {
        viewModelScope.launch {
            repository.deleteContactById(id)
        }
    }

    private fun speak(text: String) {
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "KavachSpeechID")
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
        checkInTimerJob?.cancel()
        defenseWhisperJob?.cancel()
        transcriptTimerJob?.cancel()
        sirenJob?.cancel()
        toneGenerator?.release()
    }
}

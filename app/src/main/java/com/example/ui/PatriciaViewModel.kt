package com.example.ui

import android.app.Application
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ConnectionSettings
import com.example.data.PatriciaRepository
import com.example.data.VoiceLog
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
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
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.Properties

enum class AppState {
    IDLE,
    WARMING,
    LISTENING,
    CONNECTING_SSH,
    RUNNING_CMD,
    SPEAKING,
    ERROR
}

class PatriciaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PatriciaRepository
    val settingsState: StateFlow<ConnectionSettings?>
    val logsState: StateFlow<List<VoiceLog>>

    private val _appState = MutableStateFlow(AppState.IDLE)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _terminalOutput = MutableStateFlow("🦉 Patricia Sovereign Capture — Console Ready\n")
    val terminalOutput: StateFlow<String> = _terminalOutput.asStateFlow()

    private val _isRecordingActive = MutableStateFlow(false)
    val isRecordingActive: StateFlow<Boolean> = _isRecordingActive.asStateFlow()

    private val _lastLatencyMs = MutableStateFlow<Long>(0L)
    val lastLatencyMs: StateFlow<Long> = _lastLatencyMs.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var sshJob: Job? = null
    private var warmupJob: Job? = null
    private var activeSession: Session? = null  // tracks live SSH session for clean teardown

    // Mirror of Settings
    private var currentSettings: ConnectionSettings = ConnectionSettings()

    // Resolved host after warmup auto-detection (local vs Tailscale)
    private var resolvedHost: String = currentSettings.host

    init {
        val db = AppDatabase.getDatabase(application)
        repository = PatriciaRepository(db)

        settingsState = repository.settingsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConnectionSettings()
        )

        logsState = repository.allLogsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Keep local memory model synchronization
        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                if (settings != null) {
                    currentSettings = settings
                }
            }
        }

        initTextToSpeech()

        // Immediate radio-chirp — fires in <100ms, no network, signals "system alive"
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
                delay(500)
                toneGen.release()
            } catch (_: Exception) {}
        }

        // Pre-warm the model the moment the app opens so the first real query hits a warm session
        warmupJob = viewModelScope.launch {
            delay(800) // let settings load first
            warmupBeaconSilently()
        }
    }

    private fun warmupBeaconSilently() {
        warmupJob = viewModelScope.launch {
            _appState.value = AppState.WARMING
            appendTerminal("\n⚡ Beacon waking...")

            // Try hosts in order: configured host first, then Tailscale fallback
            val s = currentSettings
            val hostsToTry = listOfNotNull(
                s.host,
                if (s.host != "100.124.172.9") "100.124.172.9" else null
            )

            val greeting = withContext(Dispatchers.IO) {
                var result: String? = null
                for (host in hostsToTry) {
                    try {
                        val jsch = JSch()
                        val session = jsch.getSession(s.username, host, s.port)
                        session.timeout = 0
                        val config = Properties()
                        config["StrictHostKeyChecking"] = "no"
                        config["PreferredAuthentications"] = if (s.authType == "PRIVATE_KEY") "publickey" else "password"
                        session.setConfig(config)
                        if (s.authType == "PASSWORD" && s.password.isNotBlank()) session.setPassword(s.password)
                        session.connect(2500) // short connect timeout for host detection
                        activeSession = session

                        val channel = session.openChannel("exec") as ChannelExec
                        val msg = "greet the user, you are online and ready"
                        channel.setCommand(s.commandTemplate.replace("%s", msg.replace("'", "'\\''") ))
                        val out = ByteArrayOutputStream()
                        channel.setOutputStream(out)
                        channel.setErrStream(ByteArrayOutputStream())
                        channel.connect(2500)

                        var counter = 0
                        while (!channel.isClosed && counter < 900) { delay(100); counter++ }
                        channel.disconnect()
                        session.disconnect()
                        activeSession = null

                        resolvedHost = host // lock in the working host
                        result = parsePatriciaResponse(out.toString("UTF-8").trim())
                        appendTerminal(" [${host}]")
                        break
                    } catch (e: Exception) {
                        appendTerminal("\n  ↳ $host unreachable")
                    }
                }
                result
            }

            if (greeting != null && greeting.isNotBlank()) {
                appendTerminal("\n🦉 $greeting")
                // Speak the greeting — agent is "aware" user is present
                if (textToSpeech != null) {
                    _appState.value = AppState.SPEAKING
                    textToSpeech?.speak(greeting, TextToSpeech.QUEUE_FLUSH, null, "beacon_greeting")
                } else {
                    _appState.value = AppState.IDLE
                }
            } else {
                appendTerminal("\n  No route to Beacon — launch Tailscale?")
                _appState.value = AppState.ERROR
                // Auto-open Tailscale so user just needs to tap Connect
                try {
                    val ctx = getApplication<Application>()
                    val tsIntent = ctx.packageManager.getLaunchIntentForPackage("com.tailscale.ipn")
                        ?: ctx.packageManager.getLaunchIntentForPackage("com.tailscale.android")
                    tsIntent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    tsIntent?.let { ctx.startActivity(it) }
                } catch (e: Exception) { /* Tailscale not installed */ }
            }
        }
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(getApplication()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.getDefault()
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _appState.value = AppState.SPEAKING
                    }

                    override fun onDone(utteranceId: String?) {
                        viewModelScope.launch(Dispatchers.Main) {
                            _appState.value = AppState.IDLE
                            if (currentSettings.continuousLoop && _isRecordingActive.value) {
                                delay(600)
                                startSpeechRecognizerInternal()
                            }
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        viewModelScope.launch(Dispatchers.Main) {
                            _appState.value = AppState.IDLE
                            appendTerminal("\n⚠️ TTS stream failed.")
                            if (currentSettings.continuousLoop && _isRecordingActive.value) {
                                delay(600)
                                startSpeechRecognizerInternal()
                            }
                        }
                    }
                })
            } else {
                appendTerminal("\n⚠️ TTS initialization failure.")
            }
        }
    }

    fun saveSettings(settings: ConnectionSettings) {
        viewModelScope.launch {
            repository.saveSettings(settings)
            appendTerminal("\n⚙️ Settings saved. Destination: ${settings.username}@${settings.host}")
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearLogs()
            appendTerminal("\n🗑️ Cleared interaction database logs.")
        }
    }

    fun appendTerminal(text: String) {
        _terminalOutput.value = _terminalOutput.value + text
    }

    fun startListeningSession() {
        _isRecordingActive.value = true
        appendTerminal("\n🎙️ Starting capture loop...")
        startSpeechRecognizerInternal()
    }

    fun stopListeningSession() {
        _isRecordingActive.value = false
        _appState.value = AppState.IDLE
        appendTerminal("\n⏹️ Capture loop deactivated.")
        
        sshJob?.cancel()
        sshJob = null

        viewModelScope.launch(Dispatchers.Main) {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun startSpeechRecognizerInternal() {
        viewModelScope.launch(Dispatchers.Main) {
            if (!_isRecordingActive.value) return@launch

            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {}

            _appState.value = AppState.LISTENING
            appendTerminal("\n🎤 Listening...")

            val speechContext = getApplication<Application>()
            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Sensitivity tweaks for Bluetooth headset (Ray-Ban glasses microphone array)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            }

            val recognizer = SpeechRecognizer.createSpeechRecognizer(speechContext)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    // Speech ready
                }

                override fun onBeginningOfSpeech() {
                    appendTerminal("\n🗣️ Audio stream detected...")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val isSilenceTimeout = error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

                    if (currentSettings.continuousLoop && _isRecordingActive.value) {
                        // In loop mode: silence timeouts are normal — restart quietly, no error UI
                        if (!isSilenceTimeout) {
                            val errorMsg = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Audio record error"
                                SpeechRecognizer.ERROR_CLIENT -> "Client process error"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Record permissions missing"
                                SpeechRecognizer.ERROR_NETWORK -> "Network STT error"
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "STT connection lookup timeout"
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Mic or device engine busy"
                                SpeechRecognizer.ERROR_SERVER -> "Remote recognition server error"
                                else -> "Capture error code $error"
                            }
                            appendTerminal("\n⚠️ STT: $errorMsg")
                            _appState.value = AppState.ERROR
                        }
                        // Always restart the loop — silence timeout or real error
                        viewModelScope.launch(Dispatchers.Main) {
                            delay(500)
                            startSpeechRecognizerInternal()
                        }
                    } else {
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio record error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client process error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Record permissions missing"
                            SpeechRecognizer.ERROR_NETWORK -> "Network STT error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "STT connection lookup timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No voice detected"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Mic or device engine busy"
                            SpeechRecognizer.ERROR_SERVER -> "Remote recognition server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Voice timeout"
                            else -> "Capture error code $error"
                        }
                        appendTerminal("\n⚠️ STT: $errorMsg")
                        _isRecordingActive.value = false
                        _appState.value = AppState.IDLE
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val resultText = matches?.getOrNull(0)

                    if (!resultText.isNullOrBlank()) {
                        appendTerminal("\n📤 You: $resultText")
                        sendToJetsonSsh(resultText)
                    } else {
                        appendTerminal("\n⚠️ Received empty audio parse.")
                        _appState.value = AppState.IDLE
                        if (currentSettings.continuousLoop && _isRecordingActive.value) {
                            startSpeechRecognizerInternal()
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(speechIntent)
        }
    }

    private fun sendToJetsonSsh(text: String) {
        // Kill any warmup or prior query still in flight — prevents session file race on server
        warmupJob?.cancel()
        sshJob?.cancel()
        activeSession?.disconnect()
        activeSession = null
        val startTime = System.currentTimeMillis()
        sshJob = viewModelScope.launch {
            _appState.value = AppState.CONNECTING_SSH
            appendTerminal("\n🔗 Contacting Server @ ${currentSettings.host}...")

            val result = withContext(Dispatchers.IO) {
                var logId: Long = 0
                try {
                    logId = repository.insertLog(
                        VoiceLog(
                            textSent = text,
                            responseReceived = null,
                            status = "CONNECTING"
                        )
                    )

                    val jsch = JSch()

                    if (currentSettings.authType == "PRIVATE_KEY" && currentSettings.privateKey.isNotBlank()) {
                        jsch.addIdentity("jetson_key_file", currentSettings.privateKey.toByteArray(Charsets.UTF_8), null, null)
                    }

                    val session = jsch.getSession(currentSettings.username, resolvedHost, currentSettings.port)
                    session.timeout = 0 // no SO_TIMEOUT — command loop handles the 90s ceiling

                    val config = java.util.Properties()
                    config["StrictHostKeyChecking"] = "no"
                    config["PreferredAuthentications"] = if (currentSettings.authType == "PRIVATE_KEY") "publickey" else "password"
                    session.setConfig(config)

                    if (currentSettings.authType == "PASSWORD" && currentSettings.password.isNotBlank()) {
                        session.setPassword(currentSettings.password)
                    }

                    session.connect(5000)
                    activeSession = session

                    _appState.value = AppState.RUNNING_CMD
                    appendTerminal(" connected.")

                    val channel = session.openChannel("exec") as ChannelExec
                    val escapedText = text.replace("'", "'\\''")
                    val fullCommand = currentSettings.commandTemplate.replace("%s", escapedText)

                    channel.setCommand(fullCommand)

                    val stdOut = ByteArrayOutputStream()
                    val stdErr = ByteArrayOutputStream()
                    channel.setOutputStream(stdOut)
                    channel.setErrStream(stdErr)

                    channel.connect(5000)

                    var counter = 0
                    while (!channel.isClosed && counter < 900) { // 90 seconds command timeout
                        delay(100)
                        counter++
                    }

                    val responseOutput = stdOut.toString("UTF-8").trim()
                    val errorOutput = stdErr.toString("UTF-8").trim()

                    channel.disconnect()
                    session.disconnect()
                    activeSession = null

                    if (responseOutput.isEmpty() && errorOutput.isNotEmpty()) {
                        throw Exception(errorOutput)
                    }
                    if (responseOutput.isEmpty()) {
                        throw Exception("Empty agent response — model may still be starting")
                    }

                    val cleanResponse = parsePatriciaResponse(responseOutput)

                    repository.insertLog(
                        VoiceLog(
                            id = logId,
                            textSent = text,
                            responseReceived = cleanResponse,
                            status = "SUCCESS"
                        )
                    )

                    SshResult.Success(cleanResponse)

                } catch (e: Exception) {
                    val errorDetails = e.message ?: "SSH Socket Fail"
                    if (logId != 0L) {
                        repository.insertLog(
                            VoiceLog(
                                id = logId,
                                textSent = text,
                                responseReceived = null,
                                status = "ERROR",
                                errorDetails = errorDetails
                            )
                        )
                    }
                    SshResult.Failure(errorDetails)
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            _lastLatencyMs.value = elapsed

            when (result) {
                is SshResult.Success -> {
                    val response = result.response
                    if (response.isBlank()) {
                        // Empty TTS call silently hangs — catch it here and recover
                        appendTerminal("\n⚠️ Empty agent response — model may still be starting")
                        _appState.value = AppState.IDLE
                        if (currentSettings.continuousLoop && _isRecordingActive.value) {
                            delay(600)
                            startSpeechRecognizerInternal()
                        }
                        return@launch
                    }
                    appendTerminal("\n🦉 Patricia: $response")

                    if (currentSettings.autoReadTts && textToSpeech != null) {
                        _appState.value = AppState.SPEAKING
                        textToSpeech?.speak(response, TextToSpeech.QUEUE_FLUSH, null, "patricia_talk")
                    } else {
                        _appState.value = AppState.IDLE
                        if (currentSettings.continuousLoop && _isRecordingActive.value) {
                            delay(600)
                            startSpeechRecognizerInternal()
                        }
                    }
                }
                is SshResult.Failure -> {
                    appendTerminal("\n⚠️ SSH error: ${result.errorMessage}")
                    _appState.value = AppState.ERROR
                    
                    if (currentSettings.continuousLoop && _isRecordingActive.value) {
                        delay(1200)
                        startSpeechRecognizerInternal()
                    }
                }
            }
        }
    }

    private fun parsePatriciaResponse(jsonStr: String): String {
        val trimmed = jsonStr.trim()
        if (!trimmed.startsWith("{")) {
            return trimmed
        }
        return try {
            val json = JSONObject(trimmed)
            if (json.has("text")) {
                json.getString("text")
            } else if (json.has("result")) {
                val resultObj = json.getJSONObject("result")
                val payloads = resultObj.getJSONArray("payloads")
                payloads.getJSONObject(0).getString("text")
            } else {
                trimmed
            }
        } catch (e: Exception) {
            trimmed
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            speechRecognizer?.destroy()
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {}
    }

    sealed class SshResult {
        data class Success(val response: String) : SshResult()
        data class Failure(val errorMessage: String) : SshResult()
    }
}

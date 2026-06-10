package com.example.ui

import android.app.Application
import android.content.Intent
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

    // Mirror of Settings
    private var currentSettings: ConnectionSettings = ConnectionSettings()

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
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
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
                    _appState.value = AppState.ERROR

                    if (currentSettings.continuousLoop && _isRecordingActive.value) {
                        viewModelScope.launch(Dispatchers.Main) {
                            delay(1200)
                            startSpeechRecognizerInternal()
                        }
                    } else {
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
        sshJob?.cancel()
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

                    val session = jsch.getSession(currentSettings.username, currentSettings.host, currentSettings.port)
                    session.timeout = 5000

                    val config = java.util.Properties()
                    config["StrictHostKeyChecking"] = "no"
                    config["PreferredAuthentications"] = if (currentSettings.authType == "PRIVATE_KEY") "publickey" else "password"
                    session.setConfig(config)

                    if (currentSettings.authType == "PASSWORD" && currentSettings.password.isNotBlank()) {
                        session.setPassword(currentSettings.password)
                    }

                    session.connect(5000)

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

                    if (responseOutput.isEmpty() && errorOutput.isNotEmpty()) {
                        throw Exception(errorOutput)
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
                    appendTerminal("\n🦉 Patricia: ${result.response}")
                    
                    if (currentSettings.autoReadTts && textToSpeech != null) {
                        _appState.value = AppState.SPEAKING
                        textToSpeech?.speak(result.response, TextToSpeech.QUEUE_FLUSH, null, "patricia_talk")
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

package com.biomechanix.movementor.sme.ui.screens.recording

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Voice commands that can be recognized.
 */
enum class VoiceCommand {
    START,
    STOP,
    PAUSE,
    RESUME,
    NONE
}

/**
 * State of the voice command listener.
 */
data class VoiceListenerState(
    val isListening: Boolean = false,
    val isAvailable: Boolean = false,
    val lastCommand: VoiceCommand = VoiceCommand.NONE,
    val partialResult: String = "",
    val error: String? = null
)

/**
 * Listener for voice commands using Android SpeechRecognizer.
 * Supports continuous listening for hands-free recording control.
 */
class VoiceCommandListener(
    private val context: Context,
    private val onCommand: (VoiceCommand) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    private val _state = MutableStateFlow(VoiceListenerState())
    val state: StateFlow<VoiceListenerState> = _state.asStateFlow()

    // Command keywords (case-insensitive matching)
    private val startKeywords = listOf("start", "record", "go", "begin", "action")
    private val stopKeywords = listOf("stop", "end", "finish", "done", "cut")
    private val pauseKeywords = listOf("pause", "wait", "hold")
    private val resumeKeywords = listOf("resume", "continue", "play")

    init {
        val isAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        _state.value = _state.value.copy(isAvailable = isAvailable)
    }

    /**
     * Start listening for voice commands.
     */
    fun startListening() {
        if (!_state.value.isAvailable) {
            _state.value = _state.value.copy(error = "Speech recognition not available")
            return
        }

        if (isListening) return

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createRecognitionListener())
            }

            val intent = createRecognizerIntent()
            speechRecognizer?.startListening(intent)
            isListening = true
            _state.value = _state.value.copy(
                isListening = true,
                error = null,
                partialResult = ""
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                error = "Failed to start: ${e.message}",
                isListening = false
            )
        }
    }

    /**
     * Stop listening for voice commands.
     */
    fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _state.value = _state.value.copy(
            isListening = false,
            partialResult = ""
        )
    }

    /**
     * Release resources.
     */
    fun release() {
        stopListening()
    }

    private fun createRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // Enable continuous listening
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = _state.value.copy(isListening = true, error = null)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission denied"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> null // No speech detected, restart
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null // Timeout, restart
                    else -> "Unknown error"
                }

                // For no match or timeout, just restart listening
                if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    restartListening()
                } else {
                    _state.value = _state.value.copy(error = errorMessage)
                    // Try to restart after other errors too
                    restartListening()
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                processResults(matches)
                // Restart listening for continuous mode
                restartListening()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull() ?: ""
                _state.value = _state.value.copy(partialResult = partial)

                // Check for commands in partial results for faster response
                val command = parseCommand(partial)
                if (command != VoiceCommand.NONE) {
                    _state.value = _state.value.copy(lastCommand = command)
                    onCommand(command)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun processResults(matches: List<String>?) {
        if (matches.isNullOrEmpty()) return

        for (match in matches) {
            val command = parseCommand(match)
            if (command != VoiceCommand.NONE) {
                _state.value = _state.value.copy(lastCommand = command)
                onCommand(command)
                break
            }
        }
    }

    private fun parseCommand(text: String): VoiceCommand {
        val lowerText = text.lowercase().trim()

        return when {
            startKeywords.any { lowerText.contains(it) } -> VoiceCommand.START
            stopKeywords.any { lowerText.contains(it) } -> VoiceCommand.STOP
            pauseKeywords.any { lowerText.contains(it) } -> VoiceCommand.PAUSE
            resumeKeywords.any { lowerText.contains(it) } -> VoiceCommand.RESUME
            else -> VoiceCommand.NONE
        }
    }

    private fun restartListening() {
        if (!isListening) return

        // Small delay before restarting
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createRecognitionListener())
            }
            val intent = createRecognizerIntent()
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                error = "Restart failed: ${e.message}",
                isListening = false
            )
        }
    }
}

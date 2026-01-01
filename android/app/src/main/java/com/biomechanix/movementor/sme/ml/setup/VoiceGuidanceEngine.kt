package com.biomechanix.movementor.sme.ml.setup

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.biomechanix.movementor.sme.domain.model.setup.ActivityProfile
import com.biomechanix.movementor.sme.domain.model.setup.CameraView
import com.biomechanix.movementor.sme.domain.model.setup.MovementPlane
import com.biomechanix.movementor.sme.domain.model.setup.defaultDistanceMax
import com.biomechanix.movementor.sme.domain.model.setup.defaultDistanceMin
import com.biomechanix.movementor.sme.domain.model.setup.defaultHeightRatio
import com.biomechanix.movementor.sme.domain.model.setup.defaultView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Text-to-speech engine for camera setup voice guidance.
 * Provides real-time voice feedback to help users position their camera and themselves.
 */
@Singleton
class VoiceGuidanceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    // Cooldown tracking to prevent spam
    private var lastSpokenIssueType: String? = null
    private var lastSpeechTime = 0L
    private val speechCooldownMs = 3000L // Don't repeat same issue within 3 seconds
    private val minSpeechIntervalMs = 1500L // Minimum time between any speech

    // Queue for pending utterances
    private val utteranceQueue = mutableListOf<String>()
    private var currentUtteranceId: String? = null

    /**
     * Initialize the TTS engine.
     */
    fun initialize(): Flow<Boolean> = callbackFlow {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                        processQueue()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                        processQueue()
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _isSpeaking.value = false
                        processQueue()
                    }
                })

                _isInitialized.value = true
                trySend(true)
            } else {
                _isInitialized.value = false
                trySend(false)
            }
        }

        awaitClose {
            // Keep TTS alive for reuse
        }
    }

    /**
     * Enable or disable voice guidance.
     */
    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        if (!enabled) {
            stop()
        }
    }

    /**
     * Speak text immediately, interrupting any current speech.
     */
    fun speakImmediate(text: String) {
        if (!_isEnabled.value || !_isInitialized.value) return

        utteranceQueue.clear()
        val utteranceId = UUID.randomUUID().toString()
        currentUtteranceId = utteranceId
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        lastSpeechTime = System.currentTimeMillis()
    }

    /**
     * Add text to the speech queue.
     */
    fun speakQueued(text: String) {
        if (!_isEnabled.value || !_isInitialized.value) return

        if (_isSpeaking.value) {
            utteranceQueue.add(text)
        } else {
            val utteranceId = UUID.randomUUID().toString()
            currentUtteranceId = utteranceId
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
            lastSpeechTime = System.currentTimeMillis()
        }
    }

    /**
     * Process the next item in the queue.
     */
    private fun processQueue() {
        if (utteranceQueue.isNotEmpty() && _isEnabled.value) {
            val text = utteranceQueue.removeAt(0)
            val utteranceId = UUID.randomUUID().toString()
            currentUtteranceId = utteranceId
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            lastSpeechTime = System.currentTimeMillis()
        }
    }

    /**
     * Stop current speech and clear queue.
     */
    fun stop() {
        tts?.stop()
        utteranceQueue.clear()
        _isSpeaking.value = false
    }

    /**
     * Process analysis result and speak appropriate guidance.
     * Implements cooldown to prevent spamming the same message.
     */
    fun processAnalysisResult(result: KeypointAnalyzer.AnalysisResult) {
        if (!_isEnabled.value || !_isInitialized.value) return

        val now = System.currentTimeMillis()

        // Check if ready to record
        if (result.isReadyToRecord) {
            if (lastSpokenIssueType != "READY") {
                speakImmediate("Perfect! Your setup looks great. You're ready to record.")
                lastSpokenIssueType = "READY"
                lastSpeechTime = now
            }
            return
        }

        // Get highest priority issue
        val topIssue = result.primaryIssue ?: return
        val issueType = topIssue::class.simpleName ?: return

        // Check cooldowns
        if (now - lastSpeechTime < minSpeechIntervalMs) return
        if (issueType == lastSpokenIssueType && now - lastSpeechTime < speechCooldownMs) return

        // Generate and speak guidance
        val message = generateGuidanceMessage(topIssue)
        if (message != null) {
            speakImmediate(message)
            lastSpokenIssueType = issueType
            lastSpeechTime = now
        }
    }

    /**
     * Generate voice guidance message for a setup issue.
     */
    private fun generateGuidanceMessage(issue: SetupIssue): String? {
        return when (issue) {
            is SetupIssue.SubjectTooClose -> {
                "Step back from the camera. Your body is too close to fit in frame."
            }
            is SetupIssue.SubjectTooFar -> {
                "Move closer to the camera for better tracking accuracy."
            }
            is SetupIssue.BodyPartCutOff -> {
                when (issue.bodyPart.displayName) {
                    "head" -> "Your head is cut off. Lower the camera or step back."
                    "feet" -> "Your feet aren't visible. Raise the camera or step back."
                    "left arm", "right arm" -> "Your arms are getting cut off. Move to the center of the frame."
                    else -> "Part of your body is cut off. Adjust your position."
                }
            }
            is SetupIssue.WrongViewAngle -> {
                when (issue.recommendedView) {
                    CameraView.SIDE_LEFT, CameraView.SIDE_RIGHT ->
                        "For this exercise, turn 90 degrees so the camera sees your side profile."
                    CameraView.FRONT ->
                        "Face the camera directly for this exercise."
                    CameraView.DIAGONAL_45_LEFT, CameraView.DIAGONAL_45_RIGHT ->
                        "Turn about 45 degrees toward the camera."
                    else -> "Adjust your angle relative to the camera."
                }
            }
            is SetupIssue.PoorFraming -> {
                when (issue.direction) {
                    FramingDirection.TOO_LEFT -> "Move to your right to center yourself in the frame."
                    FramingDirection.TOO_RIGHT -> "Move to your left to center yourself in the frame."
                    FramingDirection.TOO_HIGH -> "The camera is pointing too low. Raise it slightly."
                    FramingDirection.TOO_LOW -> "The camera is pointing too high. Lower it slightly."
                }
            }
            is SetupIssue.KeypointNotVisible -> {
                "I can't see your ${issue.landmarkName}. Make sure it's visible to the camera."
            }
            is SetupIssue.KeypointLowConfidence -> {
                "Having trouble tracking your ${issue.landmarkName}. " +
                "Make sure the area is well lit and not obscured."
            }
            is SetupIssue.UnstableDetection -> {
                "Detection is unstable. Place the camera on a stable surface."
            }
            is SetupIssue.PoorLighting -> {
                "Lighting conditions are poor. Move to a well-lit area without strong backlight."
            }
        }
    }

    /**
     * Speak initial setup instructions for an activity profile.
     */
    fun speakSetupInstructions(
        profile: ActivityProfile?,
        movementPlane: MovementPlane
    ) {
        if (!_isEnabled.value || !_isInitialized.value) return

        val activityName = profile?.displayName ?: movementPlane.name.lowercase()
        val view = profile?.getEffectiveView() ?: movementPlane.defaultView
        val distanceMin = profile?.optimalDistanceMinMeters ?: movementPlane.defaultDistanceMin
        val distanceMax = profile?.optimalDistanceMaxMeters ?: movementPlane.defaultDistanceMax
        val heightRatio = profile?.getEffectiveCameraHeight() ?: movementPlane.defaultHeightRatio

        val distanceFeetMin = (distanceMin * 3.28084f).toInt()
        val distanceFeetMax = (distanceMax * 3.28084f).toInt()

        val instructions = buildString {
            append("Let's set up your camera for $activityName. ")

            when (view) {
                CameraView.SIDE_LEFT, CameraView.SIDE_RIGHT ->
                    append("This exercise needs a side view. ")
                CameraView.FRONT ->
                    append("This exercise needs a front view. ")
                CameraView.DIAGONAL_45_LEFT, CameraView.DIAGONAL_45_RIGHT ->
                    append("This exercise needs a diagonal view. ")
                else -> {}
            }

            append("Place your camera about $distanceFeetMin to $distanceFeetMax feet away. ")

            when {
                heightRatio < 0.3f -> append("Position the camera low, near the ground. ")
                heightRatio < 0.5f -> append("Position the camera at knee to waist height. ")
                heightRatio < 0.7f -> append("Position the camera at waist to chest height. ")
                else -> append("Position the camera at chest to head height. ")
            }

            append("Step into frame when you're ready and I'll check your position.")
        }

        speakImmediate(instructions)
    }

    /**
     * Speak ROM verification instruction.
     */
    fun speakRomInstruction(instruction: String) {
        if (!_isEnabled.value || !_isInitialized.value) return
        speakImmediate(instruction)
    }

    /**
     * Speak setup complete message.
     */
    fun speakSetupComplete() {
        if (!_isEnabled.value || !_isInitialized.value) return
        speakImmediate("Setup complete! All keypoints are visible and tracking well. You can start recording when ready.")
    }

    /**
     * Speak countdown for recording.
     */
    fun speakCountdown(seconds: Int) {
        if (!_isEnabled.value || !_isInitialized.value) return
        when (seconds) {
            3 -> speakImmediate("Three")
            2 -> speakImmediate("Two")
            1 -> speakImmediate("One")
            0 -> speakImmediate("Recording")
        }
    }

    /**
     * Release TTS resources.
     */
    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        _isInitialized.value = false
    }
}

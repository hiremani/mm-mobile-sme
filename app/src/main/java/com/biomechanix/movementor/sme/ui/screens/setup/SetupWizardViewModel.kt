package com.biomechanix.movementor.sme.ui.screens.setup

import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biomechanix.movementor.sme.camera.CameraManager
import com.biomechanix.movementor.sme.data.local.dao.CameraSetupConfigDao
import com.biomechanix.movementor.sme.data.local.entity.CameraSetupConfigEntity
import com.biomechanix.movementor.sme.data.local.entity.SyncStatus
import com.biomechanix.movementor.sme.domain.model.setup.ActivityProfile
import com.biomechanix.movementor.sme.domain.model.setup.ActivityProfiles
import com.biomechanix.movementor.sme.domain.model.setup.ARSetupData
import com.biomechanix.movementor.sme.domain.model.setup.BoundingBox
import com.biomechanix.movementor.sme.domain.model.setup.CameraPlacementInstructions
import com.biomechanix.movementor.sme.domain.model.setup.CameraPositionGuide
import com.biomechanix.movementor.sme.domain.model.setup.CameraView
import com.biomechanix.movementor.sme.domain.model.setup.ExerciseZone
import com.biomechanix.movementor.sme.domain.model.setup.FloorMarker
import com.biomechanix.movementor.sme.domain.model.setup.FloorMarkerType
import com.biomechanix.movementor.sme.domain.model.setup.HeightMarker
import com.biomechanix.movementor.sme.domain.model.setup.MovementPlane
import com.biomechanix.movementor.sme.domain.model.setup.NormalizedLandmark
import com.biomechanix.movementor.sme.domain.model.setup.ReferencePose
import com.biomechanix.movementor.sme.domain.model.setup.RomPose
import com.biomechanix.movementor.sme.domain.model.setup.SetupInstructions
import com.biomechanix.movementor.sme.domain.model.setup.SubjectPositioningInstructions
import com.biomechanix.movementor.sme.domain.model.setup.Vector3
import com.biomechanix.movementor.sme.domain.model.setup.VerificationItem
import com.biomechanix.movementor.sme.domain.model.setup.defaultDistanceMax
import com.biomechanix.movementor.sme.domain.model.setup.defaultDistanceMin
import com.biomechanix.movementor.sme.domain.model.setup.defaultHeightRatio
import com.biomechanix.movementor.sme.domain.model.setup.defaultView
import com.biomechanix.movementor.sme.ml.PoseDetector
import com.biomechanix.movementor.sme.ml.PoseResult
import com.biomechanix.movementor.sme.ml.setup.AdaptiveDistanceEstimator
import com.biomechanix.movementor.sme.ml.setup.KeypointAnalyzer
import com.biomechanix.movementor.sme.ml.setup.SetupIssue
import com.biomechanix.movementor.sme.ml.setup.VoiceGuidanceEngine
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Wizard step in the setup flow.
 */
enum class SetupWizardStep {
    /** Select activity type and view angle */
    ACTIVITY_SELECTION,

    /** Initial camera positioning instructions */
    CAMERA_POSITIONING,

    /** Real-time keypoint analysis and adjustments */
    KEYPOINT_VERIFICATION,

    /** ROM verification (perform key poses) */
    ROM_VERIFICATION,

    /** Setup complete, ready to record */
    SETUP_COMPLETE
}

/**
 * UI state for the setup wizard.
 */
data class SetupWizardUiState(
    // Wizard state
    val currentStep: SetupWizardStep = SetupWizardStep.ACTIVITY_SELECTION,
    val isLoading: Boolean = false,

    // Activity selection
    val selectedActivityId: String? = null,
    val selectedProfile: ActivityProfile? = null,
    val selectedMovementPlane: MovementPlane = MovementPlane.SAGITTAL,
    val customActivityName: String = "",
    val availableProfiles: List<ActivityProfile> = ActivityProfiles.getAll(),
    val profileCategories: Map<String, List<ActivityProfile>> = ActivityProfiles.getByCategory(),

    // Camera/pose state
    val currentPose: PoseResult? = null,
    val analysisResult: KeypointAnalyzer.AnalysisResult? = null,
    val distanceEstimate: AdaptiveDistanceEstimator.EstimationResult? = null,
    val setupScore: Float = 0f,
    val isReadyToRecord: Boolean = false,

    // Issues
    val currentIssues: List<SetupIssue> = emptyList(),
    val primaryIssue: SetupIssue? = null,

    // ROM verification
    val currentRomPoseIndex: Int = 0,
    val romPoses: List<RomPose> = emptyList(),
    val romPoseResults: Map<String, Boolean> = emptyMap(),

    // Voice guidance
    val voiceGuidanceEnabled: Boolean = true,
    val isSpeaking: Boolean = false,

    // Camera state
    val useFrontCamera: Boolean = true,
    val isPoseOverlayVisible: Boolean = true,

    // Error state
    val error: String? = null
)

/**
 * One-time events from the setup wizard.
 */
sealed class SetupWizardEvent {
    data class NavigateToRecording(val sessionId: String?) : SetupWizardEvent()
    data class SetupSaved(val configId: String) : SetupWizardEvent()
    data class Error(val message: String) : SetupWizardEvent()
    object SetupCancelled : SetupWizardEvent()
}

@HiltViewModel
class SetupWizardViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val cameraManager: CameraManager,
    private val poseDetector: PoseDetector,
    private val keypointAnalyzer: KeypointAnalyzer,
    private val distanceEstimator: AdaptiveDistanceEstimator,
    private val voiceGuidanceEngine: VoiceGuidanceEngine,
    private val cameraSetupConfigDao: CameraSetupConfigDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupWizardUiState())
    val uiState: StateFlow<SetupWizardUiState> = _uiState.asStateFlow()

    private val _events = Channel<SetupWizardEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val gson = Gson()

    // Parameters from navigation
    private val exerciseType: String? = savedStateHandle["exerciseType"]
    private val exerciseName: String? = savedStateHandle["exerciseName"]
    private val sessionId: String? = savedStateHandle["sessionId"]

    init {
        initializeComponents()
        observePoseUpdates()
        observeVoiceState()

        // Pre-select activity if provided
        if (exerciseType != null) {
            val profile = ActivityProfiles.getById(exerciseType.lowercase())
            if (profile != null) {
                selectActivity(profile)
            } else {
                // Create custom profile from movement plane
                val plane = MovementPlane.entries.find {
                    it.name.equals(exerciseType, ignoreCase = true)
                } ?: MovementPlane.SAGITTAL
                selectMovementPlane(plane)
                if (exerciseName != null) {
                    updateCustomActivityName(exerciseName)
                }
            }
        }
    }

    private fun initializeComponents() {
        viewModelScope.launch {
            poseDetector.initialize()
            voiceGuidanceEngine.initialize().collect { initialized ->
                if (!initialized) {
                    _uiState.update { it.copy(voiceGuidanceEnabled = false) }
                }
            }
        }
    }

    private fun observePoseUpdates() {
        poseDetector.latestPose
            .onEach { pose ->
                if (pose != null) {
                    processPose(pose)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeVoiceState() {
        voiceGuidanceEngine.isSpeaking
            .onEach { speaking ->
                _uiState.update { it.copy(isSpeaking = speaking) }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Process detected pose for setup analysis.
     */
    private fun processPose(pose: PoseResult) {
        val state = _uiState.value

        // Only analyze in verification steps
        if (state.currentStep !in listOf(
                SetupWizardStep.KEYPOINT_VERIFICATION,
                SetupWizardStep.ROM_VERIFICATION
            )
        ) {
            _uiState.update { it.copy(currentPose = pose) }
            return
        }

        // Analyze keypoints
        val analysisResult = keypointAnalyzer.analyze(
            pose = pose,
            activityProfile = state.selectedProfile,
            movementPlane = state.selectedMovementPlane
        )

        // Estimate distance
        val distanceResult = distanceEstimator.estimate(
            pose = pose,
            frameWidth = pose.imageWidth,
            frameHeight = pose.imageHeight,
            cameraFocalLengthPx = cameraManager.getFocalLengthPx(),
            enableDebug = false
        )

        _uiState.update {
            it.copy(
                currentPose = pose,
                analysisResult = analysisResult,
                distanceEstimate = distanceResult,
                setupScore = analysisResult.overallScore,
                isReadyToRecord = analysisResult.isReadyToRecord,
                currentIssues = analysisResult.issues,
                primaryIssue = analysisResult.primaryIssue
            )
        }

        // Voice guidance for issues
        if (state.voiceGuidanceEnabled && state.currentStep == SetupWizardStep.KEYPOINT_VERIFICATION) {
            voiceGuidanceEngine.processAnalysisResult(analysisResult)
        }

        // Check ROM pose if in that step
        if (state.currentStep == SetupWizardStep.ROM_VERIFICATION) {
            checkRomPose(pose, analysisResult)
        }
    }

    /**
     * Check if current pose satisfies ROM pose requirements.
     */
    private fun checkRomPose(pose: PoseResult, analysisResult: KeypointAnalyzer.AnalysisResult) {
        val state = _uiState.value
        val currentRomPose = state.romPoses.getOrNull(state.currentRomPoseIndex) ?: return

        // Check if required keypoints are visible with good confidence
        val requiredVisible = currentRomPose.requiredKeypoints.all { kpIndex ->
            val confidence = analysisResult.keypointConfidences[kpIndex] ?: 0f
            confidence >= 0.6f
        }

        if (requiredVisible) {
            val updatedResults = state.romPoseResults.toMutableMap()
            updatedResults[currentRomPose.name] = true

            _uiState.update {
                it.copy(romPoseResults = updatedResults)
            }
        }
    }

    // ========================================
    // ACTIVITY SELECTION
    // ========================================

    fun selectActivity(profile: ActivityProfile) {
        _uiState.update {
            it.copy(
                selectedActivityId = profile.activityId,
                selectedProfile = profile,
                selectedMovementPlane = profile.movementPlane,
                romPoses = profile.romVerificationPoses
            )
        }
    }

    fun selectMovementPlane(plane: MovementPlane) {
        _uiState.update {
            it.copy(
                selectedMovementPlane = plane,
                selectedProfile = null,
                selectedActivityId = null
            )
        }
    }

    fun updateCustomActivityName(name: String) {
        _uiState.update { it.copy(customActivityName = name) }
    }

    // ========================================
    // NAVIGATION
    // ========================================

    fun proceedToNextStep() {
        val state = _uiState.value

        when (state.currentStep) {
            SetupWizardStep.ACTIVITY_SELECTION -> {
                _uiState.update { it.copy(currentStep = SetupWizardStep.CAMERA_POSITIONING) }
                speakSetupInstructions()
            }

            SetupWizardStep.CAMERA_POSITIONING -> {
                _uiState.update { it.copy(currentStep = SetupWizardStep.KEYPOINT_VERIFICATION) }
            }

            SetupWizardStep.KEYPOINT_VERIFICATION -> {
                if (state.isReadyToRecord) {
                    if (state.romPoses.isNotEmpty()) {
                        _uiState.update {
                            it.copy(
                                currentStep = SetupWizardStep.ROM_VERIFICATION,
                                currentRomPoseIndex = 0
                            )
                        }
                        speakCurrentRomInstruction()
                    } else {
                        _uiState.update { it.copy(currentStep = SetupWizardStep.SETUP_COMPLETE) }
                        voiceGuidanceEngine.speakSetupComplete()
                    }
                }
            }

            SetupWizardStep.ROM_VERIFICATION -> {
                val nextIndex = state.currentRomPoseIndex + 1
                if (nextIndex < state.romPoses.size) {
                    _uiState.update { it.copy(currentRomPoseIndex = nextIndex) }
                    speakCurrentRomInstruction()
                } else {
                    _uiState.update { it.copy(currentStep = SetupWizardStep.SETUP_COMPLETE) }
                    voiceGuidanceEngine.speakSetupComplete()
                }
            }

            SetupWizardStep.SETUP_COMPLETE -> {
                saveSetupAndProceed()
            }
        }
    }

    fun goToPreviousStep() {
        val state = _uiState.value

        val previousStep = when (state.currentStep) {
            SetupWizardStep.ACTIVITY_SELECTION -> null
            SetupWizardStep.CAMERA_POSITIONING -> SetupWizardStep.ACTIVITY_SELECTION
            SetupWizardStep.KEYPOINT_VERIFICATION -> SetupWizardStep.CAMERA_POSITIONING
            SetupWizardStep.ROM_VERIFICATION -> {
                if (state.currentRomPoseIndex > 0) {
                    _uiState.update { it.copy(currentRomPoseIndex = state.currentRomPoseIndex - 1) }
                    speakCurrentRomInstruction()
                    return
                }
                SetupWizardStep.KEYPOINT_VERIFICATION
            }
            SetupWizardStep.SETUP_COMPLETE -> {
                if (state.romPoses.isNotEmpty()) SetupWizardStep.ROM_VERIFICATION
                else SetupWizardStep.KEYPOINT_VERIFICATION
            }
        }

        if (previousStep != null) {
            _uiState.update { it.copy(currentStep = previousStep) }
        } else {
            cancelSetup()
        }
    }

    fun skipRomVerification() {
        _uiState.update { it.copy(currentStep = SetupWizardStep.SETUP_COMPLETE) }
        voiceGuidanceEngine.speakSetupComplete()
    }

    fun cancelSetup() {
        viewModelScope.launch {
            _events.send(SetupWizardEvent.SetupCancelled)
        }
    }

    // ========================================
    // VOICE GUIDANCE
    // ========================================

    fun toggleVoiceGuidance() {
        val newEnabled = !_uiState.value.voiceGuidanceEnabled
        _uiState.update { it.copy(voiceGuidanceEnabled = newEnabled) }
        voiceGuidanceEngine.setEnabled(newEnabled)
    }

    private fun speakSetupInstructions() {
        val state = _uiState.value
        if (state.voiceGuidanceEnabled) {
            voiceGuidanceEngine.speakSetupInstructions(
                profile = state.selectedProfile,
                movementPlane = state.selectedMovementPlane
            )
        }
    }

    private fun speakCurrentRomInstruction() {
        val state = _uiState.value
        val currentRomPose = state.romPoses.getOrNull(state.currentRomPoseIndex) ?: return
        if (state.voiceGuidanceEnabled) {
            voiceGuidanceEngine.speakRomInstruction(currentRomPose.instruction)
        }
    }

    // ========================================
    // CAMERA CONTROLS
    // ========================================

    /**
     * Bind camera to lifecycle with pose detection analyzer.
     */
    fun bindCamera(
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        surfaceProvider: androidx.camera.core.Preview.SurfaceProvider
    ) {
        viewModelScope.launch {
            cameraManager.initialize()
            cameraManager.bindCamera(
                lifecycleOwner = lifecycleOwner,
                surfaceProvider = surfaceProvider,
                imageAnalyzer = poseDetector,
                useFrontCamera = _uiState.value.useFrontCamera
            )
        }
    }

    /**
     * Rebind camera when front/back camera is toggled.
     */
    fun rebindCamera(
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        surfaceProvider: androidx.camera.core.Preview.SurfaceProvider
    ) {
        cameraManager.bindCamera(
            lifecycleOwner = lifecycleOwner,
            surfaceProvider = surfaceProvider,
            imageAnalyzer = poseDetector,
            useFrontCamera = _uiState.value.useFrontCamera
        )
    }

    fun toggleCamera() {
        _uiState.update { it.copy(useFrontCamera = !it.useFrontCamera) }
    }

    fun togglePoseOverlay() {
        _uiState.update { it.copy(isPoseOverlayVisible = !it.isPoseOverlayVisible) }
    }

    // ========================================
    // SAVE AND PROCEED
    // ========================================

    private fun saveSetupAndProceed() {
        viewModelScope.launch {
            android.util.Log.d("SetupWizard", "saveSetupAndProceed started")
            _uiState.update { it.copy(isLoading = true) }

            try {
                val state = _uiState.value
                val configId = UUID.randomUUID().toString()
                android.util.Log.d("SetupWizard", "Building config with id: $configId")

                // Build configuration entity
                val config = buildConfigurationEntity(configId, state)
                android.util.Log.d("SetupWizard", "Config built, inserting to DB")

                // Save to database
                cameraSetupConfigDao.insert(config)
                android.util.Log.d("SetupWizard", "Config saved to DB")

                _uiState.update { it.copy(isLoading = false) }
                _events.send(SetupWizardEvent.SetupSaved(configId))
                android.util.Log.d("SetupWizard", "Sending NavigateToRecording event with sessionId: $sessionId")
                _events.send(SetupWizardEvent.NavigateToRecording(sessionId))
                android.util.Log.d("SetupWizard", "NavigateToRecording event sent")
            } catch (e: Exception) {
                android.util.Log.e("SetupWizard", "Error in saveSetupAndProceed: ${e.message}", e)
                _uiState.update { it.copy(error = e.message, isLoading = false) }
                _events.send(SetupWizardEvent.Error(e.message ?: "Failed to save setup"))
            }
        }
    }

    /**
     * Save setup and navigate directly to recording.
     * Called when auto-transition or voice command triggers recording start.
     */
    fun saveAndStartRecording() {
        android.util.Log.d("SetupWizard", "saveAndStartRecording called")
        // Navigate directly without waiting for save to complete
        viewModelScope.launch {
            // Fire navigation immediately
            _events.send(SetupWizardEvent.NavigateToRecording(sessionId))
            android.util.Log.d("SetupWizard", "NavigateToRecording sent immediately")
        }
        // Save in background (don't block navigation)
        saveSetupInBackground()
    }

    private fun saveSetupInBackground() {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val configId = UUID.randomUUID().toString()
                val config = buildConfigurationEntity(configId, state)
                cameraSetupConfigDao.insert(config)
                android.util.Log.d("SetupWizard", "Config saved in background")
            } catch (e: Exception) {
                android.util.Log.e("SetupWizard", "Background save failed: ${e.message}", e)
            }
        }
    }

    private fun buildConfigurationEntity(
        configId: String,
        state: SetupWizardUiState
    ): CameraSetupConfigEntity {
        val pose = state.currentPose
        val analysis = state.analysisResult
        val distance = state.distanceEstimate

        val profile = state.selectedProfile
        val plane = state.selectedMovementPlane

        val effectiveView = profile?.getEffectiveView() ?: plane.defaultView
        val distanceMin = profile?.optimalDistanceMinMeters ?: plane.defaultDistanceMin
        val distanceMax = profile?.optimalDistanceMaxMeters ?: plane.defaultDistanceMax
        val heightRatio = profile?.getEffectiveCameraHeight() ?: plane.defaultHeightRatio

        // Build reference pose
        val referencePose = pose?.let {
            ReferencePose(
                landmarks = it.landmarks.map { l ->
                    NormalizedLandmark(l.x, l.y, l.z, l.visibility, l.presence)
                },
                timestampMs = it.timestampMs,
                imageWidth = it.imageWidth,
                imageHeight = it.imageHeight
            )
        } ?: ReferencePose(emptyList(), 0, 0, 0)

        // Build setup instructions
        val instructions = buildSetupInstructions(
            profile = profile,
            plane = plane,
            distanceMin = distanceMin,
            distanceMax = distanceMax,
            heightRatio = heightRatio,
            view = effectiveView
        )

        // Build AR setup data
        val arData = buildARSetupData(
            distance = distance?.distanceMeters ?: ((distanceMin + distanceMax) / 2),
            heightRatio = distance?.cameraHeightRatio ?: heightRatio,
            view = effectiveView
        )

        val boundingBox = analysis?.subjectBoundingBox ?: BoundingBox(0.1f, 0.1f, 0.9f, 0.9f)

        return CameraSetupConfigEntity(
            id = configId,
            recordingSessionId = sessionId ?: UUID.randomUUID().toString(),
            activityId = state.selectedActivityId ?: "custom",
            activityName = profile?.displayName ?: state.customActivityName.ifEmpty { "Custom Exercise" },
            movementPlane = plane,
            estimatedDistanceMeters = distance?.distanceMeters ?: ((distanceMin + distanceMax) / 2),
            cameraHeightRatio = distance?.cameraHeightRatio ?: heightRatio,
            cameraView = analysis?.detectedCameraView ?: effectiveView,
            subjectCenterX = boundingBox.centerX,
            subjectCenterY = boundingBox.centerY,
            subjectBoundingBoxJson = gson.toJson(boundingBox),
            averageKeypointConfidence = pose?.overallConfidence ?: 0f,
            criticalKeypointConfidencesJson = gson.toJson(
                analysis?.keypointConfidences?.mapKeys { it.key.toString() } ?: emptyMap<String, Float>()
            ),
            setupScore = state.setupScore,
            referencePoseJson = gson.toJson(referencePose),
            setupInstructionsJson = gson.toJson(instructions),
            arSetupDataJson = gson.toJson(arData),
            setupThumbnailPath = null,
            capturedAt = System.currentTimeMillis(),
            deviceModel = Build.MODEL,
            cameraFocalLength = cameraManager.getFocalLengthPx(),
            syncStatus = SyncStatus.PENDING
        )
    }

    private fun buildSetupInstructions(
        profile: ActivityProfile?,
        plane: MovementPlane,
        distanceMin: Float,
        distanceMax: Float,
        heightRatio: Float,
        view: CameraView
    ): SetupInstructions {
        val distanceFeetMin = (distanceMin * 3.28084f).toInt()
        val distanceFeetMax = (distanceMax * 3.28084f).toInt()

        val heightText = when {
            heightRatio < 0.3f -> "near ground level (about 1 foot high)"
            heightRatio < 0.5f -> "at knee to waist height (about 2-3 feet high)"
            heightRatio < 0.7f -> "at waist to chest height (about 3-4 feet high)"
            else -> "at chest to head height (about 4-5 feet high)"
        }

        return SetupInstructions(
            summary = "${view.name.replace("_", " ")} setup, $distanceFeetMin-$distanceFeetMax feet away, $heightText",
            cameraPlacement = CameraPlacementInstructions(
                distanceText = "About $distanceFeetMin to $distanceFeetMax feet (${distanceMin.toInt()}-${distanceMax.toInt()} meters) from where you'll exercise",
                heightText = "Position camera $heightText",
                angleText = view.toInstruction(),
                stabilityText = "Place on a stable surface or use a tripod",
                environmentText = "Ensure good lighting with no strong backlight"
            ),
            subjectPositioning = SubjectPositioningInstructions(
                standingPositionText = "Stand in the center of your exercise area",
                facingDirectionText = view.toInstruction(),
                spaceRequirementText = "Ensure at least 6 feet of clear space around you",
                startingPoseText = "Begin in your starting position for ${profile?.displayName ?: "the exercise"}"
            ),
            verificationChecklist = listOf(
                VerificationItem(
                    checkText = "Full body visible",
                    voicePrompt = "Make sure your entire body from head to feet is visible"
                ),
                VerificationItem(
                    checkText = "Good lighting",
                    voicePrompt = "Ensure you are well lit without shadows"
                ),
                VerificationItem(
                    checkText = "Stable camera",
                    voicePrompt = "Place camera on a stable surface"
                )
            )
        )
    }

    private fun buildARSetupData(
        distance: Float,
        heightRatio: Float,
        view: CameraView
    ): ARSetupData {
        val heightMeters = heightRatio * 1.7f // Assume 1.7m tall person

        val orientation = when (view) {
            CameraView.SIDE_LEFT -> 90f
            CameraView.SIDE_RIGHT -> -90f
            CameraView.FRONT -> 0f
            CameraView.BACK -> 180f
            CameraView.DIAGONAL_45_LEFT -> 45f
            CameraView.DIAGONAL_45_RIGHT -> -45f
        }

        return ARSetupData(
            exerciseZone = ExerciseZone(
                centerOffsetFromCamera = Vector3(0f, 0f, distance),
                radius = 0.5f,
                orientation = orientation
            ),
            cameraPosition = CameraPositionGuide(
                distanceFromSubject = distance,
                heightFromGround = heightMeters,
                facingDirection = 0f
            ),
            floorMarkers = listOf(
                FloorMarker(
                    position = Vector3(0f, 0f, distance),
                    type = FloorMarkerType.CENTER,
                    label = "Stand here"
                ),
                FloorMarker(
                    position = Vector3(-0.5f, 0f, distance),
                    type = FloorMarkerType.BOUNDARY,
                    label = null
                ),
                FloorMarker(
                    position = Vector3(0.5f, 0f, distance),
                    type = FloorMarkerType.BOUNDARY,
                    label = null
                )
            ),
            heightMarkers = listOf(
                HeightMarker(
                    height = heightMeters,
                    label = "Camera height",
                    isForCamera = true
                )
            )
        )
    }

    // ========================================
    // CLEANUP
    // ========================================

    override fun onCleared() {
        super.onCleared()
        voiceGuidanceEngine.stop()
    }
}

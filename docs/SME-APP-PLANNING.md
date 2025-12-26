# MoveMentor SME Mobile App - Complete Planning Documentation

## Executive Summary

This document contains the complete Product Requirements Document (PRD), Technical Architecture, and Implementation Plan for the MoveMentor Subject Matter Expert (SME) Mobile Application.

**Scope**: Native Android app (Kotlin + Jetpack Compose) enabling SMEs to record, annotate, and create reference packages for biomechanical exercises with full offline capability.

**MVP Features**: Video recording + manual phase annotation + auto-detection + offline sync

**Authentication**: Shared system with existing mm-mobile app (JWT, SME roles)

---

# Part 1: Product Requirements Document (PRD)

## 1.1 Product Vision

The MoveMentor SME Mobile App empowers fitness professionals, physical therapists, and coaches to create high-quality biomechanical reference content on-the-go. SMEs can record exercise demonstrations, annotate movement phases, and generate reference packages from any location without requiring connectivity.

## 1.2 User Personas

### Primary: Physical Therapist (PT) Sarah
- Records rehabilitation exercise demonstrations between patient appointments
- Works in clinic with unreliable WiFi
- Needs precise phase timing for therapeutic exercises
- Quick 2-3 minute recording sessions

### Secondary: Strength Coach Marcus
- Creates sport-specific exercise references for athletes
- Records in gym with no WiFi
- Uses auto-detection to speed up workflow
- Creates high volume of content (10-20 exercises per session)

### Tertiary: Yoga Instructor Maya
- Captures slow, deliberate movements with multiple holds
- Emphasizes breath and transition phases
- Prefers manual phase annotation
- Less technical, needs simple interface

## 1.3 Core User Stories

### Authentication
- **US-001**: SME login with existing MoveMentor credentials
- **US-002**: Offline token management (30-day offline access)
- **US-003**: Device registration for offline access

### Video Recording
- **US-004**: Initiate recording session with exercise metadata
- **US-005**: Camera preview with real-time pose skeleton overlay
- **US-006**: Record exercise with 30 FPS pose extraction
- **US-007**: Real-time quality indicators during recording
- **US-008**: Complete recording with quality metrics

### Video Trimming
- **US-009**: Review recording with frame-by-frame control
- **US-010**: Set trim start/end boundaries
- **US-011**: Thumbnail strip navigation

### Phase Annotation
- **US-012**: Manual phase creation at playhead position
- **US-013**: Adjust phase boundaries by dragging
- **US-014**: Auto-detect phases (velocity-based)
- **US-015**: Add coaching cues (entry, active, exit, corrections)
- **US-016**: Delete/reorder phases

### Quality & Review
- **US-017**: View quality assessment metrics
- **US-018**: Preview with phase indicators

### Reference Package Generation
- **US-019**: Configure package metadata
- **US-020**: Trigger package generation (online or queued)
- **US-021**: View generation status

### Camera Setup Sync
- **US-029**: Camera setup configuration captured during recording (distance, height, angle)
- **US-030**: AR setup data captured (floor markers, height markers, exercise zone)
- **US-031**: Setup instructions generated for end users
- **US-032**: Camera setup included in package generation request

### Offline Operation
- **US-022**: Full offline recording and annotation
- **US-023**: Offline data storage with sync tracking
- **US-024**: Sync queue management

### Synchronization
- **US-025**: Automatic sync when online (WiFi)
- **US-026**: Session sync flow (create → frames → phases → generate)
- **US-027**: Conflict resolution
- **US-028**: Manual sync trigger

## 1.4 Feature Requirements

### Recording Module
| Requirement | Specification |
|-------------|---------------|
| Resolution | 1080p preferred, 720p minimum |
| Frame Rate | 30 FPS target |
| Pose Model | MediaPipe Pose (Full), 33 landmarks |
| Processing | On-device (GPU preferred, CPU fallback) |
| Max Duration | 60 seconds (MVP) |
| Min Duration | 2 seconds |

### Phase Annotation
| Field | Type | Constraints |
|-------|------|-------------|
| phaseName | String | Max 50 chars, required |
| startFrame/endFrame | Int | Valid range, required |
| complianceThreshold | Double | 0.6-1.0, default 0.7 |
| holdDurationMs | Int | 0-5000, default 0 |
| entryCue/exitCue | String | Max 200 chars |
| activeCues | List | Max 5 items |
| correctionCues | Map | Max 10 entries |

### Auto-Detection Algorithm
```
1. Calculate joint velocities from pose sequence
2. Compute aggregate velocity (weighted by primary joints)
3. Apply smoothing filter (moving average, window=5 frames)
4. Find local minima (velocity < threshold)
5. Merge phases shorter than 10 frames
6. Assign confidence based on velocity contrast
```

### Camera Setup Configuration
| Field | Type | Description |
|-------|------|-------------|
| optimalDistanceMeters | Float | Optimal distance from camera to subject |
| cameraHeightRatio | Float | 0.0 = floor, 1.0 = head height |
| cameraView | Enum | FRONT, SIDE_LEFT, SIDE_RIGHT, BACK, DIAGONAL_* |
| movementPlane | Enum | SAGITTAL, FRONTAL, TRANSVERSE, MULTI_PLANE |
| subjectPositioning | Object | Center X/Y, bounding box |
| arSetupData | Object | Exercise zone, floor markers, height markers |
| setupInstructions | Object | Text instructions for end users |
| referencePose | Object | Captured reference pose landmarks |
| setupScore | Float | Quality score (0-100) |

## 1.5 Screen Flow

```
Splash → Login → Home/Sessions
                    ↓
              New Recording
                    ↓
              Recording Screen (Camera + Pose)
                    ↓
              Recording Complete
                    ↓
              Trim Screen (Timeline + Markers)
                    ↓
              Phase Annotation (Timeline + Editor)
                    ↓
              Review & Generate
                    ↓
              Success → Sessions List
```

## 1.6 Success Metrics

| Metric | Target |
|--------|--------|
| Recording Success Rate | > 95% |
| Pose Quality Score | > 85% average |
| Session Completion Rate | > 80% |
| Sync Success Rate | > 99% |
| Time to First Recording | < 5 minutes |
| Offline Recovery | 100% |

## 1.7 Out of Scope (MVP)

- iOS version (Phase 2)
- Multi-angle recording
- Video editing (cropping, filters)
- Voice recording for cues
- Collaborative annotation
- Wearable integration

---

# Part 2: Technical Architecture

## 2.1 System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                        │
│  Jetpack Compose UI (Screens, Components, Theme, Navigation) │
├─────────────────────────────────────────────────────────────┤
│                      Domain Layer                            │
│  ViewModels (MVVM) + Use Cases + Domain Models               │
├─────────────────────────────────────────────────────────────┤
│                       Data Layer                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │
│  │ Local       │  │ Remote      │  │ ML Pipeline │          │
│  │ Room DB     │  │ Retrofit    │  │ MediaPipe   │          │
│  │ DataStore   │  │ OkHttp      │  │ PhaseDetect │          │
│  └─────────────┘  └─────────────┘  └─────────────┘          │
│  ┌─────────────┐  ┌─────────────┐                           │
│  │ Repository  │  │ Sync        │                           │
│  │ Pattern     │  │ WorkManager │                           │
│  └─────────────┘  └─────────────┘                           │
├─────────────────────────────────────────────────────────────┤
│              Dependency Injection (Hilt)                     │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Backend (mm-backend-2)                    │
│  /v1/expert-recordings/* + /v1/mobile/sync + /v1/auth/*     │
└─────────────────────────────────────────────────────────────┘
```

## 2.2 Package Structure

```
com.biomechanix.movementor.sme/
├── MoveMentorSmeApp.kt
├── MainActivity.kt
├── data/
│   ├── local/
│   │   ├── db/AppDatabase.kt
│   │   ├── dao/ (RecordingSessionDao, PoseFrameDao, PhaseAnnotationDao, SyncQueueDao)
│   │   ├── entity/ (RecordingSessionEntity, PoseFrameEntity, PhaseAnnotationEntity)
│   │   └── preferences/PreferencesManager.kt
│   ├── remote/
│   │   ├── api/ (AuthApi, RecordingApi, SyncApi)
│   │   ├── dto/
│   │   └── interceptor/ (AuthInterceptor, OrganizationInterceptor)
│   └── repository/ (RecordingRepository, AnnotationRepository, AuthRepository, SyncRepository)
├── domain/model/ (RecordingSession, PoseFrame, PhaseAnnotation, Landmark, SyncStatus)
├── di/ (AppModule, DatabaseModule, NetworkModule, RepositoryModule, MlModule)
├── ml/ (MediaPipePoseExtractor, PhaseDetector, PoseAnalysisUtils)
├── sync/ (SyncManager, SyncWorker, ConflictResolver, SyncQueueProcessor)
├── ui/
│   ├── components/ (VideoPlayer, PoseOverlay, PhaseTimeline, PhaseCard, SyncStatusIndicator)
│   ├── screens/
│   │   ├── auth/ (LoginScreen, LoginViewModel)
│   │   ├── recording/ (RecordingScreen, RecordingViewModel)
│   │   ├── playback/ (PlaybackScreen, PlaybackViewModel)
│   │   ├── annotation/ (AnnotationScreen, AnnotationViewModel)
│   │   ├── sessions/ (SessionListScreen, SessionDetailScreen)
│   │   └── settings/ (SettingsScreen)
│   └── theme/ (Theme, Color, Type, Shape, Spacing)
└── navigation/ (Screen, AppNavigation)
```

## 2.3 Room Database Entities

### RecordingSessionEntity
```kotlin
@Entity(tableName = "recording_sessions")
data class RecordingSessionEntity(
    @PrimaryKey val id: String,
    val organizationId: String,
    val expertId: String,
    val exerciseType: String,
    val exerciseName: String,
    val status: RecordingSessionStatus, // INITIATED, RECORDING, REVIEW, COMPLETED
    val frameCount: Int,
    val frameRate: Int,
    val durationSeconds: Double?,
    val videoFilePath: String?,
    val trimStartFrame: Int?,
    val trimEndFrame: Int?,
    val qualityScore: Double?,
    val syncStatus: SyncStatus, // PENDING, SYNCING, SYNCED, CONFLICT, ERROR
    val createdAt: Long,
    val updatedAt: Long
)
```

### PoseFrameEntity
```kotlin
@Entity(tableName = "pose_frames")
data class PoseFrameEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val frameIndex: Int,
    val timestampMs: Long,
    val landmarksJson: String, // 33 landmarks: [[x, y, z, confidence, visibility], ...]
    val overallConfidence: Float,
    val isValid: Boolean
)
```

### PhaseAnnotationEntity
```kotlin
@Entity(tableName = "phase_annotations")
data class PhaseAnnotationEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val phaseName: String,
    val phaseIndex: Int,
    val startFrame: Int,
    val endFrame: Int,
    val source: AnnotationSource, // MANUAL, AUTO_VELOCITY
    val confidence: Double?,
    val complianceThreshold: Double?,
    val entryCue: String?,
    val activeCuesJson: String?,
    val exitCue: String?,
    val correctionCuesJson: String?,
    val syncStatus: SyncStatus
)
```

## 2.4 API Integration

### Key Endpoints
| Feature | Endpoint | Method |
|---------|----------|--------|
| Create Session | `/v1/expert-recordings/sessions` | POST |
| Submit Frames | `/v1/expert-recordings/sessions/{id}/frames` | POST |
| Set Trim | `/v1/expert-recordings/sessions/{id}/trim` | PUT |
| Create Phases | `/v1/expert-recordings/sessions/{id}/phases` | POST |
| Auto-Detect | `/v1/expert-recordings/sessions/{id}/phases/auto` | POST |
| Update Phase | `/v1/expert-recordings/phases/{id}` | PUT |
| Generate Package | `/v1/expert-recordings/sessions/{id}/generate` | POST |
| Sync | `/v1/mobile/sync` | POST |

### Camera Setup Sync (Package Generation)
The `GeneratePackageRequest` includes an optional `cameraSetup` field:
```kotlin
data class GeneratePackageRequest(
    val name: String,
    val description: String? = null,
    val version: String = "1.0.0",
    // ... other fields
    val cameraSetup: CameraSetupDto? = null  // NEW: Camera setup from SME
)
```

The `CameraSetupDto` is converted from `CameraSetupConfigEntity` stored locally during recording setup and includes:
- Spatial parameters (distance, height, angle)
- AR setup data (floor markers, height markers, exercise zones)
- Setup instructions (text guidance for end users)
- Reference pose landmarks

### Interceptors
- **AuthInterceptor**: Adds Bearer token, handles 401 with token refresh
- **OrganizationInterceptor**: Adds X-Organization-ID header

## 2.5 Sync Architecture

### Sync Queue Flow
```
LOCAL_ONLY → PENDING_SYNC → SYNCING → SYNCED
                              ↓
                         SYNC_FAILED → (Retry or Manual)
                              ↓
                          CONFLICT → (Resolve)
```

### Conflict Resolution
- **Sessions**: Local video/frames authoritative, server metadata merged
- **Phases**: SME annotations always authoritative (USE_LOCAL)
- **Strategy**: Last-write-wins with manual override option

### WorkManager Scheduling
- **WiFi Connected**: Immediate sync attempt
- **Background (WiFi)**: Periodic sync every 15 minutes
- **Cellular (if enabled)**: Periodic sync every 30 minutes
- **Manual Trigger**: Immediate expedited work

## 2.6 State Management

### ViewModel Pattern
```kotlin
abstract class BaseViewModel<State, Event>(initialState: State) : ViewModel() {
    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()
}
```

### Key ViewModels
- `RecordingViewModel`: Camera, pose detection, frame collection
- `AnnotationViewModel`: Phase CRUD, auto-detection, timeline
- `SessionListViewModel`: Session list, sync status
- `AuthViewModel`: Login, logout, token management

---

# Part 3: Implementation Plan

## 3.1 Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI Framework | Jetpack Compose (Material 3) |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 |
| DI | Hilt |
| Database | Room |
| Preferences | DataStore |
| Camera | CameraX |
| ML | MediaPipe Tasks Vision |
| Network | Retrofit + OkHttp |
| Background | WorkManager |
| Video Playback | Media3 ExoPlayer |

## 3.2 Phase Breakdown

### Phase 1: Foundation (Week 1-2)
**Deliverables:**
- Project setup with Gradle/Hilt/Room
- Theme system (Material 3)
- Navigation framework
- Authentication screens
- Splash screen with auth state

**Key Files:**
- `MoveMentorSmeApp.kt`, `MainActivity.kt`
- `di/AppModule.kt`, `DatabaseModule.kt`, `NetworkModule.kt`
- `data/local/db/AppDatabase.kt`
- `ui/theme/*`, `navigation/*`
- `ui/screens/auth/LoginScreen.kt`

### Phase 2: Core Recording (Week 3-4)
**Deliverables:**
- CameraX video recording
- MediaPipe pose detection
- Real-time pose overlay
- Recording session management
- Permission handling

**Key Files:**
- `data/recording/VideoRecorder.kt`
- `ml/PoseDetector.kt`, `PoseOverlayView.kt`
- `ui/screens/recording/RecordingScreen.kt`
- `data/repository/RecordingRepository.kt`

### Phase 3: Video Review & Trimming (Week 5-6)
**Deliverables:**
- ExoPlayer video playback
- Timeline scrubber
- Trim functionality
- Quality assessment display
- Recording list screen

**Key Files:**
- `ui/components/VideoPlayer.kt`, `TimelineScrubber.kt`
- `ui/screens/review/RecordingReviewScreen.kt`
- `ui/screens/list/RecordingListScreen.kt`

### Phase 4: Phase Annotation (Week 7-8)
**Deliverables:**
- Phase timeline UI
- Phase creation/editing
- Coaching cue entry
- Phase validation
- Phase data persistence

**Key Files:**
- `ui/screens/annotation/PhaseAnnotationScreen.kt`
- `ui/components/PhaseTimeline.kt`, `PhaseEditor.kt`
- `data/local/dao/PhaseDao.kt`

### Phase 5: Auto-Detection (Week 9-10)
**Deliverables:**
- Velocity-based phase detection
- Confidence scoring
- Accept/Reject UI
- Detection settings

**Key Files:**
- `ml/PhaseDetector.kt`, `VelocityAnalyzer.kt`
- `ui/screens/detection/AutoDetectionScreen.kt`

### Phase 6: Sync Infrastructure (Week 11-12)
**Deliverables:**
- Sync queue management
- WorkManager integration
- Conflict resolution
- Video upload (chunked)
- Sync status indicators

**Key Files:**
- `sync/SyncManager.kt`, `SyncWorker.kt`
- `sync/VideoUploader.kt`, `ConflictResolver.kt`
- `data/remote/api/SyncApi.kt`

### Phase 7: Dashboard & Polish (Week 13-14)
**Deliverables:**
- Home dashboard
- Settings screen
- Error handling
- Performance optimization
- Final UI polish

**Key Files:**
- `ui/screens/home/HomeScreen.kt`
- `ui/screens/settings/SettingsScreen.kt`

## 3.3 Key Dependencies (libs.versions.toml)

```toml
[versions]
kotlin = "2.0.0"
composeBom = "2024.08.00"
hilt = "2.51.1"
room = "2.6.1"
camerax = "1.3.4"
mediapipe = "0.10.14"
retrofit = "2.9.0"
workManager = "2.9.0"

[libraries]
# Core
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }

# Camera & ML
camerax-core = { group = "androidx.camera", name = "camera-core", version.ref = "camerax" }
camerax-video = { group = "androidx.camera", name = "camera-video", version.ref = "camerax" }
mediapipe-tasks-vision = { group = "com.google.mediapipe", name = "tasks-vision", version.ref = "mediapipe" }

# Network & Sync
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
workmanager = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }
```

## 3.4 Testing Strategy

| Level | Scope | Tools |
|-------|-------|-------|
| Unit | ViewModels, Repositories, Use Cases | JUnit4, MockK, Turbine |
| Integration | Room DAOs, Sync flows | Room Testing, Hilt Testing |
| UI | Critical flows (auth, recording, annotation) | Compose UI Test, Espresso |

**Target Coverage**: 80% for business logic

## 3.5 Risk Mitigation

| Risk | Mitigation |
|------|------------|
| MediaPipe performance on low-end devices | Adaptive frame rate, "lite" mode option |
| Large video files | Storage monitoring, cleanup old recordings |
| Sync conflicts | Last-write-wins, conflict detection UI |
| Network timeout during upload | Chunked upload with resume |
| Memory pressure during recording | Limit pose history, compress aggressively |

## 3.6 Performance Budgets

| Metric | Target |
|--------|--------|
| App startup (cold) | < 2 seconds |
| Frame processing | < 50ms |
| Video frame drop | < 1% |
| Memory during recording | < 300MB |
| APK size | < 50MB |

---

# Part 4: Critical File References

## Backend (mm-backend-2)
- `D:\code\projects\mm-backend-2\docs\design\API-EXPERT-RECORDING-ENDPOINTS.md` - Complete API spec
- `D:\code\projects\mm-backend-2\docs\design\DESIGN-05-API-SPECIFICATION.md` - API reference
- `D:\code\projects\mm-backend-2\docs\design\DESIGN-06-MOBILE-PACKAGE.md` - Package format
- `D:\code\projects\mm-backend-2\src\main\java\com\movementor\controller\ExpertRecordingController.java`

## Frontend (mm-frontend)
- `D:\code\projects\mm-frontend\src\features\expert-recording\utils\phaseDetection.ts` - Phase detection algorithm to port

## Mobile Reference (mm-mobile)
- `D:\code\projects\mm-mobile\android\app\src\main\java\com\biomechanix\movementor\di\AppModule.kt` - Hilt pattern
- `D:\code\projects\mm-mobile\android\app\src\main\java\com\biomechanix\movementor\data\local\db\AppDatabase.kt` - Room pattern
- `D:\code\projects\mm-mobile\android\gradle\libs.versions.toml` - Dependency versions
- `D:\code\projects\mm-mobile\android\app\src\main\java\com\biomechanix\movementor\navigation\AppNavigation.kt` - Navigation

---

# Next Steps

Upon approval, implementation will begin with:
1. **Project initialization** - Create Android project structure
2. **Gradle setup** - Configure build.gradle.kts and libs.versions.toml
3. **Hilt DI setup** - Create core dependency injection modules
4. **Room database** - Define entities, DAOs, and database
5. **Theme system** - Port Material 3 theme from mm-mobile
6. **Navigation** - Set up Compose Navigation

The implementation will follow the 7-phase plan over approximately 14 weeks.

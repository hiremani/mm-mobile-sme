# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the Subject Matter Expert (SME) mobile application for Biomechanix Inc.'s MoveMentor platform. SMEs use this app to:
- Record videos of biomechanical movements, exercises, and poses
- Annotate videos with phases (manual or auto-generated)
- Edit phase annotations and add phase-wise instructions
- Work offline with automatic sync when online

## Planning Documentation

Complete PRD, architecture, and implementation plan: `docs/SME-APP-PLANNING.md`

## Related Repositories

- Backend: `D:\code\projects\mm-backend-2`
- Frontend (web): `D:\code\projects\mm-frontend`
- End-user mobile app: `D:\code\projects\mm-mobile`

Review these repositories to understand the platform architecture before implementation.

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **Min SDK**: 26 (Android 8.0), Target SDK: 34
- **DI**: Hilt
- **Database**: Room
- **Preferences**: DataStore
- **Camera**: CameraX
- **ML**: MediaPipe Tasks Vision
- **Network**: Retrofit + OkHttp
- **Background**: WorkManager
- **Video Playback**: Media3 ExoPlayer

## Project Structure

```
mm-mobile-sme/
├── android/          # Android app project
│   ├── app/          # Main Android application module
│   ├── gradle/       # Gradle wrapper and version catalog
│   └── ...           # Gradle build files
├── ios/              # iOS app (planned for future phases)
├── docs/             # Project documentation
├── CLAUDE.md         # This file
└── README.md         # Project README
```

## Build Commands

Run from the `android/` directory:

```bash
cd android
./gradlew assembleDebug           # Build debug APK
./gradlew assembleDevDebug        # Build dev flavor debug
./gradlew test                    # Run unit tests
./gradlew testDevDebugUnitTest    # Run dev debug unit tests
./gradlew connectedAndroidTest    # Run instrumented tests
./gradlew lint                    # Run lint checks
./gradlew clean                   # Clean build
```

## Architecture

Clean Architecture with MVVM pattern (under `android/app/src/main/java/`):
```
com.biomechanix.movementor.sme/
├── data/local/     # Room DB, DAOs, DataStore
├── data/remote/    # Retrofit APIs, DTOs
├── data/repository/# Repository implementations
├── domain/model/   # Domain entities
├── di/             # Hilt modules
├── ml/             # MediaPipe pose detection, phase detection
├── sync/           # WorkManager, sync queue
├── ui/components/  # Reusable Compose components
├── ui/screens/     # Feature screens + ViewModels
├── ui/theme/       # Material 3 theme
└── navigation/     # Compose Navigation
```

## Key Patterns

- **Offline-first**: All data saved to Room first, synced via WorkManager
- **Repository pattern**: Single source of truth coordinating local/remote
- **StateFlow + Channel**: ViewModel state and one-time events
- **Hilt DI**: Constructor injection throughout

## API Endpoints

Base: `/api/v1/expert-recordings/`
- `POST /sessions` - Create recording session
- `POST /sessions/{id}/frames` - Submit pose frames
- `PUT /sessions/{id}/trim` - Set trim boundaries
- `POST /sessions/{id}/phases` - Create phase annotations
- `POST /sessions/{id}/phases/auto` - Auto-detect phases
- `POST /sessions/{id}/generate` - Generate reference package
- `POST /mobile/sync` - Offline sync

## Implementation Progress

### Phase 1: Foundation (Complete)
- Gradle project structure with Kotlin DSL
- libs.versions.toml with all dependencies
- Hilt DI modules (App, Database, Network, ML, Repository)
- Room entities: RecordingSession, PoseFrame, PhaseAnnotation, SyncQueue
- DAOs with Flow queries and sync tracking
- API interfaces: AuthApi, RecordingApi, SyncApi
- DTOs for all API operations
- Auth and Organization interceptors
- Material 3 theme system (light/dark)
- Compose Navigation with all routes

### Phase 2: Core Recording (Complete)
- CameraManager: CameraX video recording with preview
- PoseDetector: MediaPipe wrapper for real-time pose estimation
- PoseOverlay: Skeleton rendering with body part colors
- Permission utilities for camera/audio
- RecordingRepository: Session and frame management
- RecordingViewModel: Camera/pose state management
- RecordingScreen: Camera preview with pose overlay
- NewRecordingScreen: Exercise type/name selection

### Remaining Phases
- Phase 3: Video Review & Trimming
- Phase 4: Phase Annotation
- Phase 5: Auto-Detection
- Phase 6: Sync Infrastructure
- Phase 7: Dashboard & Polish

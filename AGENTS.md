# Home Relay Agent Guide

Read [README.md](README.md), [docs/architecture.md](docs/architecture.md), and
[docs/testing.md](docs/testing.md) before modifying this repository. The
historical Version 1 design and plan are in `docs/superpowers/`.

## Project rules

- Package and namespace: `app.maw629.homerelay`.
- App and Android Share-sheet label: `Home Relay`.
- Support Android API 26 and later; compile and target SDK are 37.
- Keep the app native Kotlin with Jetpack Compose, Room, DataStore, SAF,
  WorkManager, and NotificationCompat. Do not add a dependency-injection
  framework unless a new approved design requires one.
- Do not add Google Drive API, OAuth, Google Cloud, Firebase, a server,
  analytics SDK, broad storage permission, or `MANAGE_EXTERNAL_STORAGE`.
- Treat all shared content as untrusted. Accept only `content://` file URIs;
  never accept text shares or `file://` URIs.
- Provider-write success is not cloud-sync or Windows-sync confirmation.

## Data and lifecycle invariants

- Stage every accepted shared URI in `noBackupFilesDir/pending` before the
  share receiver finishes.
- Room is the durable upload queue. Preserve its state transition guards and
  update `app/schemas/.../HomeRelayDatabase/` for database schema changes.
- DataStore stores the selected document-tree URI. Keep only one persisted SAF
  destination grant: release invalid candidate grants and replaced grants in
  the ordering described in `docs/architecture.md`.
- WorkManager uses unique work name `upload:<itemId>`, connected-network
  constraints, and exponential backoff. Do not create a second Room database,
  destination store, or app container inside a worker.
- Preserve restart/reboot recovery for queued and interrupted uploads.
- Transfers at least 10 MiB use foreground data-sync execution and progress.
- Share receiver UI must respect safe drawing insets. Android 15+ enforces
  edge-to-edge for this app's target SDK.

## Change workflow

1. Inspect affected code and relevant architecture/test documentation first.
2. For any behavior fix or feature, write a focused failing test before
   production code. Run the test red, implement the minimum change, then run
   it green.
3. Keep changes scoped. Do not refactor unrelated files while fixing a defect.
4. Add or update local unit tests for deterministic logic and instrumentation
   tests for Android/UI/provider interactions.
5. Run the relevant WSL checks and Windows device checks in
   `docs/testing.md`. Do not claim a device behavior is verified without a
   connected-device or emulator result.
6. Update `docs/architecture.md` if a component boundary, state transition,
   security invariant, or release behavior changes. Update `README.md` if a
   setup, command, or user-visible capability changes.

## Environment split

- WSL: `./gradlew testDebugUnitTest lintDebug assembleDebug`.
- Windows with connected phone/emulator:
  `.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest lintDebug`.
- Use Android Studio's bundled JBR/JDK. Prefer JDK 21 for Gradle; JDK 25 emits
  Kotlin target fallback warnings.
- Use Gradle wrapper scripts only. Do not install or invoke system Gradle.

## Secret and artifact hygiene

Never commit or expose:

- `keystore.properties`, `*.jks`, `*.keystore`, passwords, or key aliases with
  their passwords.
- `local.properties`, local SDK paths, APK/AAB outputs, `build/`, or `.gradle/`.
- `.superpowers/` agent scratch files, reports, or ledgers.

Keep signing keys outside the repository and back them up securely.

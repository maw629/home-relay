# Home Relay

Home Relay is a sideloaded Android app that receives files from Android's Share
sheet and writes them to one user-selected document-tree folder. Google Drive
is supported through Android's Storage Access Framework (SAF); the app does not
use OAuth, the Google Drive API, a cloud backend, Firebase, or broad storage
permissions.

Start with [docs/architecture.md](docs/architecture.md) for the maintained
technical contract. Contributors and coding agents must read
[AGENTS.md](AGENTS.md) before changing the project.

## User flow

1. Select a dedicated destination such as `Google Drive > Home Relay Inbox`.
2. Share one or more files to `Home Relay`.
3. Home Relay privately stages each accepted `content://` URI in no-backup
   storage, then durably queues it for provider writing or records a durable
   attention outcome.
4. A transparent receiver shows a centered status overlay with the aggregate
   share result, then returns to the source app after its configured display
   duration.
5. Queued files are written to the selected document provider. Google Drive and
   Drive for desktop synchronize separately and eventually.

Completed means the selected Android document provider accepted the write. The
share-status overlay and a completed provider write do not independently prove
that Drive cloud replication or Windows sync has finished.

## Requirements

- Android Studio with its bundled JBR/JDK and Android SDK API 37 installed.
- Android SDK Build-Tools and Platform-Tools (`adb`).
- WSL 2 with a Linux Android SDK for local Gradle builds, if using the hybrid
  workflow below.
- A physical phone or emulator for instrumentation tests.
- Google Drive and Zalo installed on the physical phone for full UAT.
- Google Drive for desktop on Windows to validate eventual Windows sync.

The app package is `app.maw629.homerelay`, `minSdk` is 26, and `compileSdk` and
`targetSdk` are 37.

## Development workflow

Keep the repository in the WSL filesystem when possible. Use WSL for unit
tests, lint, and APK builds. Use Windows Android Studio and Windows `adb` for
connected-device tests, installation, and manual UAT.

### WSL checks

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The share-status overlay remains visible for 2,000 ms after its terminal
result by default. To build a debug APK with a three-second display duration:

```bash
SHARE_STATUS_DISPLAY_MILLIS=3000 ./gradlew assembleDebug
```

If `SHARE_STATUS_DISPLAY_MILLIS` is unset, invalid, negative, or overflows a
`Long`, Home Relay uses the 2,000 ms default.

### Windows checks

Connect a phone or start an emulator, then run from PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest lintDebug
```

See [docs/testing.md](docs/testing.md) for focused test commands, diagnostics,
and acceptance-test recording.

### Debug installation

```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

The phone can be disconnected after installation. Reconnect it only for `adb`,
instrumentation tests, or diagnostics.

## Manual acceptance checklist

- [ ] Select `Google Drive > Home Relay Inbox`; force-stop Home Relay, reopen it, and confirm the destination remains selected.
- [ ] Reboot the phone and confirm the destination remains selected.
- [ ] Share a PDF, image, DOCX, ZIP, and multiple files from Zalo to Home Relay.
- [ ] Share a real `content://` file and confirm it is privately staged before the receiver finishes, then is queued or shown as needing attention.
- [ ] Recreate the receiver while it is preparing and confirm it observes the existing intake without creating a second row; recreate it after a terminal result and confirm the original display deadline remains in effect.
- [ ] While a share is preparing, use Back and predictive Back and confirm neither abandons intake; after a terminal result, confirm Back returns to the source app.
- [ ] Share a Zalo file and confirm only the centered overlay is visible over Zalo, including transparent system bars, then that it automatically returns to Zalo after two seconds.
- [ ] Repeat the Zalo overlay and return checks with gesture and three-button navigation on API 26-34 and Android 15+.
- [ ] Interrupt staging, reopen Home Relay, and confirm Recent Uploads shows the interrupted share with no Retry action and instructs the sender to share it again.
- [ ] Share a file in airplane mode, reconnect, and observe automatic retry.
- [ ] Share two same-named files and confirm unique Drive names.
- [ ] Share a file at least 10 MiB and observe a foreground progress notification.
- [ ] Remove the Drive account or delete the selected folder; confirm an item reaches `NEEDS_ATTENTION` with folder reselection.
- [ ] Deny notifications and confirm Recent Uploads still exposes queued and failed items.
- [ ] Confirm a completed item appears in Drive web and in Windows File Explorer via Drive for desktop.

Observed debug-build UAT evidence as of 2026-08-31:

- Destination persistence after force-stop/relaunch and reboot passed.
- Zalo single-file sharing passed for PDF, image, DOCX, and ZIP.
- Files by Google multiple-file sharing passed; Zalo did not offer a
  multiple-file share flow.
- Offline retry, duplicate-name handling, denied notifications, and Drive web
  plus Drive for desktop synchronization passed.
- A foreground progress notification was observed, but provider writes can
  finish too quickly to make its progress consistently visible. Google Drive's
  later cloud-sync progress is outside Home Relay's control.
- Destination-loss recovery and signed-release acceptance remain to be recorded.

## Release signing

Create a keystore outside this repository with Android Studio's **Generate
Signed Bundle / APK** flow. Do not commit the keystore or any passwords.
Create an ignored `keystore.properties` at the repository root:

```properties
storeFile=C:/secure/path/home-relay-release.jks
storePassword=your-keystore-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

The release signing configuration is enabled only when all four properties are
present. Build and inspect the signed release with:

```powershell
.\gradlew.bat clean assembleRelease signingReport
```

Install the resulting APK with:

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
```

If the installed debug app uses a different certificate, Android requires its
uninstallation before installing the release build. This removes local queue
data and the selected destination; select the destination again afterward.

An Android App Bundle (`.aab`) is for Play distribution and is not directly
installable. Use `app-release.apk` for sideloading.

# Home Relay Android App Design

> Historical Version 1 design record. For the maintained implementation
> contract, read [`docs/architecture.md`](../../architecture.md).

## Purpose

Build a private Android app named `Home Relay` that appears as `Home Relay` in Android's Share sheet. When a file is shared from Zalo, the app queues it and writes it to one Google Drive folder selected during one-time setup. Google Drive for desktop then makes the folder available on the owner's Windows laptop.

The first release is for one Android user and one Google Drive account. It is distributed as a locally sideloaded APK.

## Scope

Included:

- Receiving single and multiple file shares from Android apps, including Zalo.
- Selecting a destination Google Drive folder once and changing it in Settings.
- Persistently queuing files, retrying temporary failures, and notifying the user of outcomes.
- Viewing and retrying recent uploads.
- Writing to a Google Drive folder selected through Android's system folder picker.

Excluded:

- Chat, iPhone support, multiple users, Drive file browsing, and in-app printing.
- A custom server, Firebase, Google Cloud project, Google Drive API, and OAuth integration.
- Guaranteed confirmation that the Google Drive Android app has completed cloud replication after accepting a write.

## User Flow

1. On first launch, the user selects `Choose Drive folder`.
2. Android opens its system folder picker. The user selects a destination such as `Google Drive > Home Relay Inbox`.
3. The app retains Android's read/write permission for that selected folder tree.
4. In Zalo, the user selects a file, chooses Share, and selects `Home Relay`.
5. The app immediately copies the received file into private staging storage and adds an upload record to its queue.
6. A notification reports that the file is queued, uploading, completed, or needs attention.
7. A background worker writes the staged file into the selected Drive folder when a network is available.
8. Google Drive for desktop synchronizes the folder to the Windows laptop, where the user can print or otherwise use the file.

## Architecture

The app is a native Kotlin Android application with a small Compose interface. It has no network service of its own. Google Drive's installed Android document provider performs the final cloud synchronization.

### Folder Access

The app uses Android's Storage Access Framework rather than the Google Drive API:

- Launch `ACTION_OPEN_DOCUMENT_TREE` during setup.
- Request read, write, and persistable URI grants.
- Persist the returned grant with `takePersistableUriPermission`.
- Store the opaque tree URI in DataStore for display and future access.
- Never assume a Drive provider package or authority. The system picker determines the provider.
- Validate that the selected directory supports document creation with a zero-byte probe document, then delete the probe before treating setup as complete.

This gives the app access only to the selected folder and its descendants. It does not require a Google account password, OAuth refresh token, Drive API key, storage runtime permission, or broad Drive access.

The folder grant can become invalid when the user uninstalls or clears the app, removes the Drive account, loses edit access, or deletes the selected folder. The app must guide the user to select the folder again in these cases.

### Share Receiver

`ShareReceiverActivity` is exported only for Android's standard sharing actions:

- `ACTION_SEND` for one file.
- `ACTION_SEND_MULTIPLE` for multiple files.
- MIME type `*/*`.

The receiver reads `EXTRA_STREAM` and `ClipData` content URIs. It accepts files only, not shared plain text. Sender-provided URI permission is temporary, so the receiver copies each file to private, no-backup staging storage before finishing its activity.

The share receiver presents a minimal confirmation state: `Queued N file(s) for Home Relay`. It never asks for a destination folder during normal sharing.

### Persistent Upload Queue

Room stores one record per staged file:

- Stable item ID.
- Original display name and MIME type.
- Unique output name.
- Staged file path and byte size.
- Created time, retry count, and most recent error code.
- State: `QUEUED`, `UPLOADING`, `COMPLETED`, `NEEDS_ATTENTION`, or `CANCELLED`.

The output name combines a timestamp, a short random suffix, and a sanitized original filename. This avoids provider-specific duplicate-name behavior and preserves the original name for the user.

DataStore holds only small settings, including the selected tree URI and notification preferences. File content is never placed in DataStore or the database.

### Transfer Worker

A unique WorkManager `CoroutineWorker` processes each queue item:

1. Verify that the persisted destination grant is usable.
2. Wait for connected network access.
3. Create a document in the selected tree with the recorded output name and MIME type.
4. Stream the staged file through `ContentResolver` to the destination document.
5. Mark the record completed and delete its staged copy only after the document provider accepts the stream close.

Workers retry transient connectivity and provider I/O failures with exponential backoff. Transfers of 10 MiB or more run in foreground mode with a progress notification. It must tolerate work re-execution after process interruption.

Writing through the document provider does not provide a provider-independent confirmation that the file has finished synchronizing to Google's cloud. The completed state means `saved to the selected Drive folder provider`; Windows availability remains eventual.

## User Interface

### Setup And Settings

The setup screen contains:

- Destination folder status.
- `Choose Drive folder` and `Change folder` actions.
- A short explanation that the selected folder must remain writable in the installed Google Drive app.
- Notification permission status for Android 13 and later.

The settings screen exposes the same destination controls and a link to Recent Uploads. No Drive browser is embedded in the app.

### Recent Uploads

Recent Uploads shows the most recent queue items, filename, size, state, time, and error message where applicable. `NEEDS_ATTENTION` items expose `Retry` and, when folder access is invalid, `Choose folder again`. Queued items can be cancelled, which deletes their staged file.

## Failure Handling

| Condition | App behavior |
| --- | --- |
| No network or transient provider failure | Keep staged file and retry automatically with exponential backoff. |
| Share URI cannot be read | Show an immediate error; do not create a queue item. |
| Insufficient private staging storage | Show an immediate error; do not create a queue item. |
| Destination permission lost, folder deleted, or Drive account signed out | Mark item `NEEDS_ATTENTION`; notify the user to choose the Drive folder again. |
| Quota, policy, or permanent provider error | Mark item `NEEDS_ATTENTION`; do not retry automatically. |
| Write outcome unknown after failure | Retain the original queue record for user review. A retry uses a new unique output name to avoid accidental overwrite. |
| App restart or device reboot | Resume queued work from Room and private staging storage. |

## Security And Privacy

- The app has access only to the folder chosen through the system picker, not the user's full Drive.
- A dedicated `Home Relay Inbox` folder should be selected rather than a Drive root or general personal folder.
- Shared files are untrusted input. The app sanitizes output names, treats MIME types as advisory, and never executes or previews received content.
- Staged content remains in private no-backup storage and is removed after successful provider acceptance or explicit cancellation.
- The app requests no broad storage permission, account credential, OAuth token, contact, location, or analytics permission.
- Android 13 and later require the notification permission for normal notification display. The core queue must continue working when notifications are denied.

## Development Stack

### Required Software

- Windows laptop with current stable Android Studio.
- Android Studio's bundled JBR/JDK and its Gradle wrapper; no system Gradle installation.
- Android SDK Platform, Build Tools, Android Emulator, and Platform Tools (`adb`) installed through SDK Manager.
- Git for source control.
- Google Drive for desktop on Windows.
- A physical Android device with Zalo and Google Drive installed.

### Application Dependencies

- Kotlin.
- Jetpack Compose and Material 3.
- AndroidX Activity Result APIs for the folder picker.
- AndroidX DataStore for settings.
- Room for the upload queue.
- WorkManager with `CoroutineWorker` for persistent work.
- NotificationCompat for status and progress notifications.
- JUnit, kotlinx-coroutines-test, AndroidX Test, Compose UI testing, and WorkManager testing.

Target the latest stable Android SDK and begin with `minSdk 26` (Android 8). This supports the required document-tree and background-work APIs while avoiding legacy Android behavior.

## Testing Strategy

### Automated Tests

- Local unit tests: filename generation, upload-state transitions, error classification, retry policy, and cancellation cleanup.
- Worker tests: queued work, connected-network constraints, retry result, permanent failure result, and progress updates against a fake document destination.
- Compose instrumentation tests: first-launch setup states, queued/completed/failed list rendering, retry actions, folder-reselection prompt, and notification-permission states.

Do not attempt to make Google Drive folder selection a hermetic automated test. It is an account-backed external document provider and system UI integration.

### Manual Integration Tests

Use a dedicated `Home-Relay-SAF-Test` folder in a non-production Drive account during development. Test on both a Google Play emulator and the physical Android phone.

- Select a Drive folder; kill and restart the app; reboot the phone; confirm the folder grant remains usable.
- Share a PDF, image, Office file, ZIP file, and multiple files from Zalo.
- Share while offline, reconnect, and confirm automatic retry.
- Test duplicate filenames and a large file.
- Revoke or remove Drive access, delete the selected folder, and confirm actionable recovery.
- Deny notification permission and confirm that uploads remain visible in Recent Uploads.
- Confirm each completed file appears in Drive web and in Windows File Explorer through Drive for desktop.
- Test the signed release APK, not only the debug build.

## Distribution

Version 1 is distributed as a signed release APK and installed on the owner's phone with `adb install` or Android's package installer. The signing key is backed up securely and never committed to source control.

No Google Play publication is required. If public or multi-user distribution becomes a goal, reassess account separation, support, privacy policy, release signing, and the Drive provider dependency.

## Acceptance Criteria

1. The user selects a Google Drive folder once during setup and can change it only from Settings.
2. `Home Relay` appears in the Android Share sheet for files shared by Zalo.
3. Sharing a file never shows a folder chooser and creates a persistent queue item before the sender URI expires.
4. A temporary offline condition causes automatic retry after connectivity returns.
5. A permanent destination problem exposes an actionable recovery state without discarding the staged file.
6. A successful provider write creates a uniquely named file in the selected Drive folder.
7. Drive for desktop makes completed files available to the Windows laptop according to its configured synchronization policy.

# Home Relay Testing

Read [docs/architecture.md](architecture.md) before testing behavior that spans
shares, private staging, Room, WorkManager, and SAF providers.

## Test layers

| Layer | Runs where | Proves |
| --- | --- | --- |
| Local unit tests | WSL or Windows | Deterministic parsing, naming, repository, state, and ViewModel behavior. |
| Instrumentation tests | Windows with phone/emulator | Android UI, Room, WorkManager, provider fakes, and activity lifecycle behavior. |
| Manual provider UAT | Physical phone and optionally emulator | Real Google Drive document provider, real Zalo share behavior, permissions, reboot, and desktop synchronization. |

## Commands

### WSL

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew assembleRelease
```

### Windows with a device or emulator

```powershell
adb devices
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest lintDebug
```

`connectedDebugAndroidTest` requires `adb devices` to show a device with state
`device`.

Run one instrumentation class with PowerShell-safe Gradle runner arguments:

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=app.maw629.homerelay.share.ShareReceiverActivityTest'
```

Do not use Gradle `--tests` with `connectedDebugAndroidTest`; that option does
not filter Android instrumentation tests.

## Required change coverage

- Add a local unit test for deterministic logic and error mapping.
- Add instrumentation coverage for Android activity, Compose, Room, provider,
  permission, or WorkManager behavior that cannot be trusted on the JVM.
- Cover a real `content://` share that creates its durable staging row and
  private staged file before the receiver finishes.
- Cover receiver recreation while preparation is active without duplicate
  staging, recreation after a terminal result without resetting the terminal
  deadline, and process-restored intake without reopening a stale URI.
- Cover Back and predictive Back prevention while preparation is active,
  automatic finish at the terminal deadline, and dismissal with Back after a
  real terminal status.
- Cover the transparent centered receiver card, transparent system bars, and
  source-app visibility outside the overlay.
- Cover interrupted-recovery UI in Recent Uploads: `SHARE_INTERRUPTED` tells
  the sender to share again and has no Retry action.
- Verify that a bug-regression test fails before the production fix and passes
  after it.
- Run focused tests first, then the full suite before merging/releasing.
- Run a real-device test for exported share entry points and any SAF behavior.

## Manual acceptance record

For every release candidate, record the following with date, device model,
Android API level, app commit/version, Drive provider/account type, file type
and size, result, and any evidence path:

| Case | Expected result |
| --- | --- |
| Folder persistence | Selected tree remains after force-stop, relaunch, and reboot. |
| File intake | PDF, image, DOCX, ZIP, and multiple-file share privately stage before the receiver finishes, then become queued or a durable attention record without a folder picker. |
| Receiver lifecycle | Recreation during preparation observes the existing operation without a duplicate row; recreation after terminal status retains its original deadline. |
| Receiver Back handling | Back and predictive Back cannot dismiss the receiver while preparation is active; after a terminal result, Back can return to the source app. |
| Zalo share overlay | A Zalo share shows only Home Relay's centered transparent status card over Zalo, with the source app visible outside the card and through transparent system bars, then returns to Zalo after two seconds. |
| Zalo navigation/API coverage | Repeat the Zalo overlay and return case with gesture and three-button navigation on API 26-34 and Android 15+. |
| Interrupted staging | After staging interruption and app restart, Recent Uploads shows a nonretryable `SHARE_INTERRUPTED` record that tells the sender to share again. |
| Offline behavior | Queued item retries after connectivity returns. |
| Duplicate names | Same original filename produces distinct destination names. |
| Large file | At least 10 MiB produces foreground provider-write progress when observable. |
| Lost destination | Folder deletion/account removal reaches `NEEDS_ATTENTION` with reselection action. |
| Notification denial | Recent Uploads and recovery actions work when notifications are denied. |
| Replication | Completed provider write appears in Drive web and eventually Windows Drive for desktop. |
| Signed release | Release APK installs and repeats the release-critical cases. |

Zalo may not expose multiple-file sharing on a given version. In that case,
record it as unavailable and validate `ACTION_SEND_MULTIPLE` with Files by
Google or another file manager.

For Zalo overlay cases, record the navigation mode as well as date, device
model, Android API level, app commit/version, source file type and size, result,
and evidence path. Do not claim device or manual behavior is verified unless
the device command and the corresponding case were run.

The Home Relay foreground progress notification measures the copy to the
document provider. Drive's subsequent cloud-sync progress is outside the app's
control and may make provider-write progress difficult to observe.

## Diagnostics

Keep the phone connected while reproducing an issue. Clear logs first:

```powershell
adb logcat -c
```

Capture relevant logs as UTF-8, avoiding PowerShell's default UTF-16
`Tee-Object` output:

```powershell
adb logcat -v time | Select-String -Pattern 'AndroidRuntime|HomeRelay|WorkManager|ActivityTaskManager|DocumentsContract|Permission' |
  Set-Content -Encoding utf8 .\home-relay-debug.log
```

For a visible UI problem, capture the screen while the failing activity is
still foreground:

```powershell
adb exec-out uiautomator dump /dev/tty > .\home-relay-window.xml
adb exec-out screencap -p > .\home-relay-window.png
```

Confirm the hierarchy is for Home Relay before drawing conclusions:

```powershell
Select-String -Path .\home-relay-window.xml -Pattern 'app.maw629.homerelay'
```

If a log was captured with `Tee-Object`, convert a copy in WSL before reading:

```bash
iconv -f UTF-16LE -t UTF-8 home-relay-debug.log > home-relay-debug.utf8.log
```

Never commit captured user files, Drive contents, logs containing sensitive
material, release keystores, or `keystore.properties`.

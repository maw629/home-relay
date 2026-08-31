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
| File intake | PDF, image, DOCX, ZIP, and multiple-file share queue without a folder picker. |
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

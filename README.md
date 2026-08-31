# Home Relay

Home Relay receives files from Android's share sheet, stages them privately, and
writes them to a folder selected through the Storage Access Framework, including
Google Drive folders exposed by the Drive app. It does not use OAuth, the Google
Drive API, a cloud backend, or broad storage permissions.

## Local setup

1. Open this directory in Android Studio and install the required Android SDK
   platform and build tools when prompted.
2. Connect an Android device or start an emulator, then build and install the
   debug app:

   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. In Home Relay, select the target folder with the system folder picker. For
   Google Drive, install Drive, sign in to a dedicated non-production account,
   and select the intended Drive folder.

## Automated verification

Run the complete automated suite before device testing:

```bash
./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug
```

`connectedDebugAndroidTest` requires a connected Android device or emulator.

## Manual acceptance checklist

- [ ] Select `Google Drive > Home Relay Inbox`; force-stop Home Relay, reopen it, and confirm the destination remains selected.
- [ ] Reboot the phone and confirm the destination remains selected.
- [ ] Share a PDF, image, DOCX, ZIP, and multiple files from Zalo to Home Relay.
- [ ] Share a file in airplane mode, reconnect, and observe automatic retry.
- [ ] Share two same-named files and confirm unique Drive names.
- [ ] Share a file at least 10 MiB and observe a foreground progress notification.
- [ ] Remove the Drive account or delete the selected folder; confirm an item reaches `NEEDS_ATTENTION` with folder reselection.
- [ ] Deny notifications and confirm Recent Uploads still exposes queued and failed items.
- [ ] Confirm a completed item appears in Drive web and in Windows File Explorer via Drive for desktop.

### Emulator validation

Create a current stable Google Play emulator in Android Studio. Sign in to a
dedicated non-production Google account, install Google Drive, select a
dedicated `Home-Relay-SAF-Test` folder, and run the checklist. Zalo-specific
sharing can be skipped if Zalo is unavailable in the emulator.

### Physical-device validation

Install the debug APK with the command in Local setup. Use Zalo's actual
`Share file to Other app` flow for every applicable checklist item. Capture
`adb logcat` for any document-provider, WorkManager, or notification failure.

## Release signing

Create a new keystore outside this repository with Android Studio's **Generate
Signed Bundle / APK** flow. Do not commit the keystore or its passwords. Create
`keystore.properties` at the repository root with paths and credentials for
your local keystore:

```properties
storeFile=/absolute/path/outside/the/repository/home-relay-release.jks
storePassword=your-keystore-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

`keystore.properties`, `*.jks`, and `*.keystore` are ignored by Git. The
release signing configuration is enabled only when all four properties are
present. Keep this file local and never add passwords to source files.

Build the signed release APK after configuring the local properties file:

```bash
./gradlew assembleRelease
```

Install it using Android's package installer or:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Verify that Home Relay appears in Zalo's Android Share sheet, retains its
selected destination after relaunch, and passes the full checklist on the
physical phone.

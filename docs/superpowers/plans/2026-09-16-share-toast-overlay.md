# Share Toast Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the full-screen share confirmation with a transient system Toast followed by `finish()`, so sharing returns to the sending app.

**Architecture:** Keep intent parsing, staging, and queueing byte-for-byte identical; only the tail of `ShareReceiverActivity` changes from Compose `setContent` to `statusMessage()` + `Toast.LENGTH_SHORT` + `finish()` in `finally`, with a translucent `noHistory` manifest entry.

**Tech Stack:** Native Kotlin, Android SDK (minSdk 26, compile/target 37), Jetpack lifecycle-runtime-ktx, JUnit4 + Robolectric (local), Espresso/ActivityScenario (instrumentation), Gradle wrapper only.

**Spec:** `docs/superpowers/specs/2026-09-16-share-toast-design.md`

## Global Constraints

- Package and namespace: `app.maw629.homerelay`.
- App and Android Share-sheet label: `Home Relay`.
- Support Android API 26 and later; compile SDK 37 and target SDK 37.
- Keep native Kotlin with Jetpack Compose, Room, DataStore, SAF, WorkManager, NotificationCompat. Do not add a dependency-injection framework.
- Do not add Google Drive API, OAuth, Google Cloud, Firebase, a server, analytics SDK, broad storage permission, or `MANAGE_EXTERNAL_STORAGE`.
- Accept only `content://` file URIs; never accept text shares or `file://` URIs.
- Stage every accepted shared URI in `noBackupFilesDir/pending` before the share receiver finishes; `finish()` happens only after `queueShares()` returns.
- Room is the durable upload queue; preserve state transition guards.
- WorkManager unique work name `upload:<itemId>`, connected-network constraint, exponential backoff.
- Share receiver never launches a folder picker or notification-permission request.
- Use Gradle wrapper scripts only (`./gradlew` on WSL, `.\gradlew.bat` on Windows). Kotlin toolchain Temurin 21, do not change JDK.
- Never commit `keystore.properties`, `*.jks`, `local.properties`, APK/AAB outputs, `build/`, `.gradle/`, logs, or user files.

---

## File Structure

- Modify: `app/src/main/java/app/maw629/homerelay/share/ShareReceiverActivity.kt` — remove Compose UI, keep `ShareQueueStatus` + `queueShares()`, add `statusMessage()`, Toast + `finish()`. Single responsibility: transient share intake entry point.
- Create: `app/src/test/java/app/maw629/homerelay/share/ShareStatusMessageTest.kt` — pure JVM unit test for the message mapper. Single responsibility: lock exact user-visible strings.
- Modify: `app/src/main/AndroidManifest.xml` — share activity only: translucent theme + `noHistory` + `excludeFromRecents`. Single responsibility: window/back-stack behavior.
- Modify: `app/src/androidTest/java/app/maw629/homerelay/share/ShareReceiverActivityTest.kt` — assert auto-finish instead of Compose nodes. Single responsibility: receiver lifecycle proof on device.
- Delete: `app/src/androidTest/java/app/maw629/homerelay/share/ShareQueueScreenVisibilityTest.kt` — no Compose surface remains to screenshot.
- Modify: `docs/architecture.md` — "Android UI constraints" section: full-window surface becomes headless Toast receiver.

---

### Task 1: Message mapper with failing-then-passing unit test

**Files:**
- Modify: `app/src/main/java/app/maw629/homerelay/share/ShareReceiverActivity.kt`
- Test: `app/src/test/java/app/maw629/homerelay/share/ShareStatusMessageTest.kt`

**Interfaces:**
- Consumes: existing `ShareQueueStatus` sealed interface (`Preparing`, `Queued(count)`, `DestinationMissing`, `SourceUnreadable`, `StorageFull`).
- Produces: `internal fun statusMessage(status: ShareQueueStatus): String` used by Task 2's Toast call.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/app/maw629/homerelay/share/ShareStatusMessageTest.kt` with exactly:

```kotlin
package app.maw629.homerelay.share

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareStatusMessageTest {
    @Test
    fun preparingMessage() {
        assertEquals("Preparing files for Home Relay", statusMessage(ShareQueueStatus.Preparing))
    }

    @Test
    fun singleFileQueuedMessage() {
        assertEquals("Queued 1 file for Home Relay", statusMessage(ShareQueueStatus.Queued(1)))
    }

    @Test
    fun multipleFilesQueuedMessage() {
        assertEquals("Queued 3 files for Home Relay", statusMessage(ShareQueueStatus.Queued(3)))
    }

    @Test
    fun destinationMissingMessage() {
        assertEquals(
            "Choose a destination in Home Relay before sharing files",
            statusMessage(ShareQueueStatus.DestinationMissing)
        )
    }

    @Test
    fun sourceUnreadableMessage() {
        assertEquals("A shared file could not be read", statusMessage(ShareQueueStatus.SourceUnreadable))
    }

    @Test
    fun storageFullMessage() {
        assertEquals("Not enough storage to queue shared files", statusMessage(ShareQueueStatus.StorageFull))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "app.maw629.homerelay.share.ShareStatusMessageTest"`
Expected: FAIL with `unresolved reference: statusMessage` (compilation failure proves red).

- [ ] **Step 3: Write minimal implementation**

Append to the bottom of `app/src/main/java/app/maw629/homerelay/share/ShareReceiverActivity.kt` (after the `ShareQueueStatus` sealed interface, before the `ShareQueueScreen` composable which Task 2 removes):

```kotlin
internal fun statusMessage(status: ShareQueueStatus): String = when (status) {
    ShareQueueStatus.Preparing -> "Preparing files for Home Relay"
    is ShareQueueStatus.Queued -> "Queued ${status.count} ${if (status.count == 1) "file" else "files"} for Home Relay"
    ShareQueueStatus.DestinationMissing -> "Choose a destination in Home Relay before sharing files"
    ShareQueueStatus.SourceUnreadable -> "A shared file could not be read"
    ShareQueueStatus.StorageFull -> "Not enough storage to queue shared files"
}
```

Do not change any other line in this task.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "app.maw629.homerelay.share.ShareStatusMessageTest"`
Expected: PASS, 6 tests, `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/app/maw629/homerelay/share/ShareStatusMessageTest.kt app/src/main/java/app/maw629/homerelay/share/ShareReceiverActivity.kt
git commit -m "test: add statusMessage mapper for share toast"
```

---

### Task 2: Headless receiver (Toast + finish, no Compose)

**Files:**
- Modify: `app/src/main/java/app/maw629/homerelay/share/ShareReceiverActivity.kt`

**Interfaces:**
- Consumes: `statusMessage()` from Task 1, existing `ShareIntentParser.parse()`, `container.destinationStore`, `container.shareStager`, `container.uploadRepository`.
- Produces: headless `ShareReceiverActivity` that always finishes; no `ShareQueueScreen` remains for Task 4 to reference.

- [ ] **Step 1: Rewrite ShareReceiverActivity.kt to the headless version**

Replace the entire file content with exactly:

```kotlin
package app.maw629.homerelay.share

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.maw629.homerelay.HomeRelayApplication
import java.util.UUID
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shares = ShareIntentParser.parse(intent)
        lifecycleScope.launch {
            try {
                val status = queueShares(shares)
                Toast.makeText(this@ShareReceiverActivity, statusMessage(status), Toast.LENGTH_SHORT).show()
            } finally {
                finish()
            }
        }
    }

    private suspend fun queueShares(shares: List<IncomingShare>): ShareQueueStatus {
        val container = (application as HomeRelayApplication).container
        if (container.destinationStore.destinationTreeUri.firstOrNull() == null) {
            return ShareQueueStatus.DestinationMissing
        }

        var queuedCount = 0
        for (share in shares) {
            when (val result = container.shareStager.stage(UUID.randomUUID().toString(), share)) {
                is StageResult.Staged -> {
                    container.uploadRepository.enqueue(result, share)
                    queuedCount++
                }
                StageResult.SourceUnreadable -> return ShareQueueStatus.SourceUnreadable
                StageResult.StorageFull -> return ShareQueueStatus.StorageFull
            }
        }
        return ShareQueueStatus.Queued(queuedCount)
    }
}

sealed interface ShareQueueStatus {
    data object Preparing : ShareQueueStatus
    data class Queued(val count: Int) : ShareQueueStatus
    data object DestinationMissing : ShareQueueStatus
    data object SourceUnreadable : ShareQueueStatus
    data object StorageFull : ShareQueueStatus
}

internal fun statusMessage(status: ShareQueueStatus): String = when (status) {
    ShareQueueStatus.Preparing -> "Preparing files for Home Relay"
    is ShareQueueStatus.Queued -> "Queued ${status.count} ${if (status.count == 1) "file" else "files"} for Home Relay"
    ShareQueueStatus.DestinationMissing -> "Choose a destination in Home Relay before sharing files"
    ShareQueueStatus.SourceUnreadable -> "A shared file could not be read"
    ShareQueueStatus.StorageFull -> "Not enough storage to queue shared files"
}
```

Verify no remaining references to `setContent`, `ShareQueueScreen`, `HomeRelayTheme`, `MaterialTheme`, `Surface`, `Text`, `Box`, `WindowInsets`, `mutableStateOf` in this file.

- [ ] **Step 2: Run local tests and lint**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`
Expected: `BUILD SUCCESSFUL`. Unused Compose imports must not remain (lint would flag `UnusedImports` if the old imports survive).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/maw629/homerelay/share/ShareReceiverActivity.kt
git commit -m "feat: share receiver shows toast and finishes"
```

---

### Task 3: Manifest window behavior for the share activity

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: headless activity from Task 2 (no content view to display).
- Produces: translucent, no-history share entry point verified by Task 6 device UAT.

- [ ] **Step 1: Edit the share activity declaration**

Change this exact block:

```xml
        <activity
            android:name=".share.ShareReceiverActivity"
            android:exported="true"
            android:label="Home Relay">
```

To this exact block:

```xml
        <activity
            android:name=".share.ShareReceiverActivity"
            android:exported="true"
            android:label="Home Relay"
            android:theme="@android:style/Theme.Translucent.NoTitleBar"
            android:noHistory="true"
            android:excludeFromRecents="true">
```

Do not touch `MainActivity`, permissions, `provider`, or intent filters.

- [ ] **Step 2: Verify merge and lint**

Run: `./gradlew lintDebug assembleDebug`
Expected: `BUILD SUCCESSFUL`, no manifest merger errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: make share receiver translucent with no history"
```

---

### Task 4: Instrumentation tests for auto-finish, remove surface test

**Files:**
- Modify: `app/src/androidTest/java/app/maw629/homerelay/share/ShareReceiverActivityTest.kt`
- Delete: `app/src/androidTest/java/app/maw629/homerelay/share/ShareQueueScreenVisibilityTest.kt`

**Interfaces:**
- Consumes: headless activity from Task 2, `SampleContentProvider` authority `app.maw629.homerelay.share-test` from `app/src/androidTest/AndroidManifest.xml`.
- Produces: device proof that sharing finishes back to the sender; no test references `ShareQueueScreen`.

- [ ] **Step 1: Rewrite ShareReceiverActivityTest.kt**

Replace the entire file content with exactly:

```kotlin
package app.maw629.homerelay.share

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.maw629.homerelay.HomeRelayApplication
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class ShareReceiverActivityTest {
    @Test
    fun singleFileShareFinishesAfterQueueing() {
        ActivityScenario.launch<ShareReceiverActivity>(sampleSendIntent()).use { scenario ->
            val deadline = System.currentTimeMillis() + 5_000
            while (scenario.state != Lifecycle.State.DESTROYED && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
            }
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    private fun sampleSendIntent() = Intent(Intent.ACTION_SEND)
        .setClass(
            ApplicationProvider.getApplicationContext(),
            ShareReceiverActivity::class.java
        )
        .setType("application/pdf")
        .putExtra(
            Intent.EXTRA_STREAM,
            Uri.parse("content://app.maw629.homerelay.share-test/report.pdf")
        )
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private companion object {
        @JvmStatic
        @BeforeClass
        fun configureDestinationBeforeActivityLaunch() = runBlocking {
            ApplicationProvider.getApplicationContext<HomeRelayApplication>()
                .container
                .destinationStore
                .setDestination("content://app.maw629.homerelay.share-test/tree/destination")
        }
    }
}
```

- [ ] **Step 2: Delete the obsolete surface test**

Run: `git rm app/src/androidTest/java/app/maw629/homerelay/share/ShareQueueScreenVisibilityTest.kt`
Expected: file removed, staged for commit.

- [ ] **Step 3: Verify no stale references remain**

Search for `ShareQueueScreen` under `app/src`. Expected: zero matches. If any match remains, stop and fix before committing.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/app/maw629/homerelay/share/ShareReceiverActivityTest.kt
git commit -m "test: share receiver finishes instead of showing surface"
```

Note: `connectedDebugAndroidTest` for this file runs in Task 6 on Windows with a device; do not claim device verification here.

---

### Task 5: Update architecture doc for headless receiver

**Files:**
- Modify: `docs/architecture.md`

**Interfaces:**
- Consumes: design decision from spec section "UI constraints update".
- Produces: maintained contract matching the shipped behavior.

- [ ] **Step 1: Replace the share-receiver UI bullets**

Change this exact block:

```markdown
- The share confirmation is a full-window Compose surface with safe-drawing
  insets. This keeps queue status below system bars on Android 15+ edge-to-edge
  enforcement.
- The visible share status is one of preparing, queued count, missing
  destination, unreadable source, or local storage full.
```

To this exact block:

```markdown
- The share receiver is headless. It shows a transient system Toast for the
  terminal queue status and finishes immediately, returning to the sending app.
  There is no receiver window content, so safe-inset and surface-contrast rules
  do not apply to it.
- The toasted share status is one of queued count, missing destination,
  unreadable source, or local storage full. Preparing is internal only and is
  never toasted.
```

Do not change any other section.

- [ ] **Step 2: Commit**

```bash
git add docs/architecture.md
git commit -m "docs: headless share receiver with toast"
```

---

### Task 6: Full verification and device UAT

**Files:** none (verification only).

**Interfaces:**
- Consumes: all tasks above.
- Produces: release-ready evidence; no code changes.

- [ ] **Step 1: Run WSL checks**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`
Expected: `BUILD SUCCESSFUL` with zero test failures and zero lint errors.

- [ ] **Step 2: Run Windows device checks (Windows with phone/emulator)**

Run in PowerShell:

```powershell
adb devices
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest lintDebug
```

Expected: `adb devices` shows one device with state `device`; all tests pass including `app.maw629.homerelay.share.ShareReceiverActivityTest.singleFileShareFinishesAfterQueueing`. Do not claim device behavior verified without this output.

- [ ] **Step 3: Manual share UAT on the phone**

With the debug APK installed (`adb install -r app\build\outputs\apk\debug\app-debug.apk`):
1. Share a PDF from Files by Google to Home Relay: confirm a Toast with `Queued 1 file for Home Relay` floats over the sending app and the foreground app after the share is the sender, not Home Relay.
2. Share an image from Zalo to Home Relay: same Toast and back-stack expectation.
3. If Zalo offers no multi-file flow, share multiple files from Files by Google and confirm `Queued N files for Home Relay`.

Record date, device model, API level, commit, file types, and results. If any step shows Home Relay staying foreground or no Toast, stop and file a bug against Task 2/3 instead of working around it here.

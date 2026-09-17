# Conversation Uploads Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Revamp the Uploads tab into a single-channel, own-messages-only conversation with delivery states.

**Architecture:** UI-layer revamp only. `UploadRow` stays the model; a new pure `MessageStatus` mapper plus time formatter feed a rewritten `UploadsScreen` (right-aligned bubbles, `reverseLayout` newest-at-bottom); the ViewModel filters `CANCELLED` from the `uploads` flow. No Room, WorkManager, SAF, notification, or queue-logic changes.

**Tech Stack:** Native Kotlin, Jetpack Compose Material3, JUnit4 (JVM) + Compose UI test (instrumentation), Gradle wrapper only.

**Spec:** `docs/superpowers/specs/2026-09-17-conversation-uploads-design.md`

## Global Constraints

- Package and namespace: `app.maw629.homerelay`.
- Support Android API 26 and later; compile SDK 37 and target SDK 37.
- Keep native Kotlin with Jetpack Compose; do not add a dependency-injection framework or any new dependency.
- No Room schema change; preserve DAO state transition guards.
- Do not change intent handling, staging, WorkManager scheduling, SAF destination handling, notifications, or Settings.
- Exact user-visible copy: `Sending…`, `Uploading…`, `Sent ✓`, `Retry`, `Cancel`, `Choose folder again`, `N bytes` (e.g. `42 bytes`), header texts `Home Relay` and `Recent uploads`, short time format `HH:mm`.
- TDD for every behavior change: red run first, then minimal implementation, then green run.
- Use Gradle wrapper scripts only (`./gradlew` on WSL, `.\gradlew.bat` on Windows). Kotlin toolchain Temurin 21.
- Never commit secrets, keystores, APK outputs, `build/`, `.gradle/`, logs, or user files.
- Do not claim a device behavior is verified without a connected-device or emulator result.

---

## File Structure

- Create: `app/src/main/java/app/maw629/homerelay/ui/ChatMessage.kt` — `MessageStatus` enum, `messageStatus()`, `formatMessageTime()`. Single responsibility: pure conversation-presentation helpers with zero Android-framework calls (except `ZoneId`, which is plain `java.time`).
- Create: `app/src/test/java/app/maw629/homerelay/ui/ChatMessageTest.kt` — JVM unit tests for the helpers. Single responsibility: lock mapper and formatter behavior.
- Modify: `app/src/main/java/app/maw629/homerelay/ui/HomeRelayViewModel.kt` — append `.filter { it.state != UploadState.CANCELLED }` in the `uploads` mapping. Single responsibility change: conversation excludes cancelled sends.
- Modify: `app/src/main/java/app/maw629/homerelay/ui/UploadsScreen.kt` — rewrite list body to own-message bubbles with `reverseLayout`; composable signature unchanged. Single responsibility: conversation rendering.
- Modify: `app/src/test/java/app/maw629/homerelay/ui/HomeRelayViewModelTest.kt` — extend the `EmptyUploadDao` fake with preset items and add the cancelled-exclusion test.
- Modify: `app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt` — update the ISO-timestamp assertion to the `HH:mm` format; add bubble-content and visual-ordering tests.

---

### Task 1: MessageStatus mapper and time formatter with unit tests

**Files:**
- Create: `app/src/main/java/app/maw629/homerelay/ui/ChatMessage.kt`
- Test: `app/src/test/java/app/maw629/homerelay/ui/ChatMessageTest.kt`

**Interfaces:**
- Consumes: `app.maw629.homerelay.data.UploadState` (existing enum: `QUEUED`, `UPLOADING`, `COMPLETED`, `NEEDS_ATTENTION`, `CANCELLED`).
- Produces: `enum class MessageStatus { SENDING, SENT, FAILED }`, `fun messageStatus(state: UploadState): MessageStatus`, `fun formatMessageTime(createdAtMillis: Long, nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String` — used by Task 3's bubble.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/app/maw629/homerelay/ui/ChatMessageTest.kt` with exactly:

```kotlin
package app.maw629.homerelay.ui

import app.maw629.homerelay.data.UploadState
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageTest {
    @Test
    fun queuedMapsToSending() {
        assertEquals(MessageStatus.SENDING, messageStatus(UploadState.QUEUED))
    }

    @Test
    fun uploadingMapsToSending() {
        assertEquals(MessageStatus.SENDING, messageStatus(UploadState.UPLOADING))
    }

    @Test
    fun completedMapsToSent() {
        assertEquals(MessageStatus.SENT, messageStatus(UploadState.COMPLETED))
    }

    @Test
    fun needsAttentionMapsToFailed() {
        assertEquals(MessageStatus.FAILED, messageStatus(UploadState.NEEDS_ATTENTION))
    }

    @Test
    fun cancelledFallsBackToFailed() {
        assertEquals(MessageStatus.FAILED, messageStatus(UploadState.CANCELLED))
    }

    @Test
    fun epochZeroFormatsToMidnightUtc() {
        assertEquals("00:00", formatMessageTime(0L, 1_000_000L, ZoneId.of("UTC")))
    }

    @Test
    fun knownInstantFormatsToUtcWallTime() {
        assertEquals("01:46", formatMessageTime(1_000_000_000_000L, 1_000_003_600_000L, ZoneId.of("UTC")))
    }

    @Test
    fun todayShowsTimeOnly() {
        assertEquals("01:47", formatMessageTime(1_000_000_060_000L, 1_000_003_600_000L, ZoneId.of("UTC")))
    }

    @Test
    fun yesterdayShowsYesterdayAndTime() {
        assertEquals(
            "Yesterday 01:46",
            formatMessageTime(1_000_000_000_000L, 1_000_086_400_000L, ZoneId.of("UTC"))
        )
    }

    @Test
    fun sixDaysAgoShowsWeekdayAndTime() {
        assertEquals(
            "Saturday 01:46",
            formatMessageTime(999_913_600_000L, 1_000_432_000_000L, ZoneId.of("UTC"))
        )
    }

    @Test
    fun threeDaysAgoShowsWeekdayAndTime() {
        assertEquals(
            "Sunday 01:46",
            formatMessageTime(1_000_000_000_000L, 1_000_259_200_000L, ZoneId.of("UTC"))
        )
    }

    @Test
    fun sevenDaysAgoShowsDateAndTime() {
        assertEquals(
            "2001/09/09 01:46",
            formatMessageTime(1_000_000_000_000L, 1_000_604_800_000L, ZoneId.of("UTC"))
        )
    }

    @Test
    fun tenDaysAgoShowsDateAndTime() {
        assertEquals(
            "2001/09/09 01:46",
            formatMessageTime(1_000_000_000_000L, 1_000_864_000_000L, ZoneId.of("UTC"))
        )
    }

    @Test
    fun futureTimestampShowsTimeOnly() {
        assertEquals("02:46", formatMessageTime(1_000_003_600_000L, 1_000_000_000_000L, ZoneId.of("UTC")))
    }
}
```

(`1_000_000_000_000L` ms is `2001-09-09T01:46:40Z`, so `HH:mm` in UTC is `01:46`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "app.maw629.homerelay.ui.ChatMessageTest"`
Expected: FAIL with compilation errors `Unresolved reference: MessageStatus`, `Unresolved reference: messageStatus`, `Unresolved reference: formatMessageTime` (proves red — nothing exists yet).

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/app/maw629/homerelay/ui/ChatMessage.kt` with exactly:

```kotlin
package app.maw629.homerelay.ui

import app.maw629.homerelay.data.UploadState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class MessageStatus {
    SENDING,
    SENT,
    FAILED
}

fun messageStatus(state: UploadState): MessageStatus = when (state) {
    UploadState.QUEUED, UploadState.UPLOADING -> MessageStatus.SENDING
    UploadState.COMPLETED -> MessageStatus.SENT
    UploadState.NEEDS_ATTENTION -> MessageStatus.FAILED
    // Unreachable: CANCELLED rows are filtered out of the conversation
    // upstream in HomeRelayViewModel.uploads; FAILED is the safe fallback.
    UploadState.CANCELLED -> MessageStatus.FAILED
}

private val messageTimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val messageDateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd")

fun formatMessageTime(
    createdAtMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val created = Instant.ofEpochMilli(createdAtMillis).atZone(zone)
    val daysAgo = ChronoUnit.DAYS.between(created.toLocalDate(), Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()).toInt()
    val time = created.format(messageTimeFormat)
    return when {
        daysAgo <= 0 -> time
        daysAgo == 1 -> "Yesterday $time"
        daysAgo < 7 -> "${created.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} $time"
        else -> "${created.format(messageDateFormat)} $time"
    }
}
```

Do not add anything else to this file.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "app.maw629.homerelay.ui.ChatMessageTest"`
Expected: PASS, 7 tests, `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/app/maw629/homerelay/ui/ChatMessageTest.kt app/src/main/java/app/maw629/homerelay/ui/ChatMessage.kt
git commit -m "feat: message status mapper and time formatter"
```

---

### Task 2: Filter cancelled uploads from the conversation flow

**Files:**
- Modify: `app/src/main/java/app/maw629/homerelay/ui/HomeRelayViewModel.kt`
- Test: `app/src/test/java/app/maw629/homerelay/ui/HomeRelayViewModelTest.kt`

**Interfaces:**
- Consumes: `UploadRepository.observeUploads()` returning `Flow<List<UploadItem>>`; `UploadItem` constructor `(id: String, originalName: String, mimeType: String, outputName: String, stagedPath: String, byteSize: Long, createdAtMillis: Long, retryCount: Int, state: UploadState, errorCode: UploadErrorCode)`; existing `viewModel(store, gateway)` test helper.
- Produces: `uploads: StateFlow<List<UploadRow>>` excluding `CANCELLED` — consumed by Task 3's screen with no further change.

- [ ] **Step 1: Write the failing test**

In `app/src/test/java/app/maw629/homerelay/ui/HomeRelayViewModelTest.kt`, make these exact edits:

(a) Extend the `viewModel` helper with a DAO parameter (appended after the existing `appVersion` parameter so the positional `FakeAppVersionProvider("1.1", 2L)` call in `settingsStateExposesAppVersionFromProvider` keeps compiling):

```kotlin
    private fun viewModel(
        store: DestinationRepository,
        gateway: DestinationGateway,
        appVersion: AppVersionProvider = FakeAppVersionProvider("", 0L),
        dao: UploadDao = EmptyUploadDao()
    ): HomeRelayViewModel =
        HomeRelayViewModel(
            store,
            gateway,
            UploadRepository(dao, NoOpUploadScheduler(), { "id" }, { 0L }, { "suffix" }),
            appVersion
        )
```

(b) Give `EmptyUploadDao` preset items (default keeps existing usages empty):

```kotlin
    private class EmptyUploadDao(
        private val items: Flow<List<UploadItem>> = MutableStateFlow(emptyList())
    ) : UploadDao {
        override suspend fun insert(item: UploadItem) = Unit
        override fun observeAll(): Flow<List<UploadItem>> = items
        override suspend fun get(id: String): UploadItem? = null
        override suspend fun update(item: UploadItem) = Unit
        override suspend fun beginUpload(id: String): Int = 0
        override suspend fun requeueInterruptedUploads(): Int = 0
        override suspend fun requeueInterruptedUpload(id: String): Int = 0
        override suspend fun queuedIds(): List<String> = emptyList()
        override suspend fun finishUpload(id: String, state: UploadState, errorCode: UploadErrorCode, retryCount: Int): Int = 0
        override suspend fun retry(id: String, outputName: String, retryCount: Int): Int = 0
        override suspend fun cancel(id: String): Int = 0
        override suspend fun delete(id: String) = Unit
    }
```

(c) Add the test (place after `successfulValidationReplacesDestinationAndClearsExistingError`):

```kotlin
    @Test
    fun uploadsExcludeCancelledItems() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val dao = EmptyUploadDao(
            MutableStateFlow(
                listOf(
                    UploadItem(
                        id = "q1",
                        originalName = "queued.pdf",
                        mimeType = "application/pdf",
                        outputName = "queued.pdf",
                        stagedPath = "/pending/queued.pdf",
                        byteSize = 10L,
                        createdAtMillis = 1L,
                        retryCount = 0,
                        state = UploadState.QUEUED,
                        errorCode = UploadErrorCode.NONE
                    ),
                    UploadItem(
                        id = "c1",
                        originalName = "cancelled.pdf",
                        mimeType = "application/pdf",
                        outputName = "cancelled.pdf",
                        stagedPath = "/pending/cancelled.pdf",
                        byteSize = 10L,
                        createdAtMillis = 2L,
                        retryCount = 0,
                        state = UploadState.CANCELLED,
                        errorCode = UploadErrorCode.NONE
                    )
                )
            )
        )
        val viewModel = viewModel(
            FakeDestinationRepository("content://old"),
            FakeDestinationGateway(DestinationResult.Success),
            dao = dao
        )
        advanceUntilIdle()

        assertEquals(listOf("q1"), viewModel.uploads.first().map(UploadRow::id))
    }
```

This test needs no new imports: `Flow`, `MutableStateFlow`, `first`, `UploadItem`, `UploadState`, `UploadErrorCode` are all already imported in the file.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "app.maw629.homerelay.ui.HomeRelayViewModelTest"`
Expected: FAIL with `AssertionError: expected:<[q1]> but was:<[q1, c1]>` on `uploadsExcludeCancelledItems` (all other tests still pass — proves the filter is the only missing piece).

- [ ] **Step 3: Write minimal implementation**

In `app/src/main/java/app/maw629/homerelay/ui/HomeRelayViewModel.kt`, change the `uploads` mapping from:

```kotlin
    val uploads: StateFlow<List<UploadRow>> = uploadRepository.observeUploads()
        .map { uploads ->
            uploads.map { upload ->
```

to:

```kotlin
    val uploads: StateFlow<List<UploadRow>> = uploadRepository.observeUploads()
        .map { uploads ->
            uploads.filter { it.state != UploadState.CANCELLED }.map { upload ->
```

`UploadState` is already imported in the file. Change nothing else in this task.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "app.maw629.homerelay.ui.HomeRelayViewModelTest"`
Expected: PASS, all tests in the class (existing 5 destination tests + version test + new filter test), `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/app/maw629/homerelay/ui/HomeRelayViewModelTest.kt app/src/main/java/app/maw629/homerelay/ui/HomeRelayViewModel.kt
git commit -m "feat: exclude cancelled uploads from conversation"
```

---

### Task 3: Rewrite UploadsScreen as own-message bubbles

**Files:**
- Modify: `app/src/main/java/app/maw629/homerelay/ui/UploadsScreen.kt`
- Test (update only): `app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt`

**Interfaces:**
- Consumes: `UploadRow` (unchanged shape), `messageStatus()` and `formatMessageTime()` from Task 1, `uploads` flow from Task 2 (already CANCELLED-free), existing callbacks `onRetry: (String) -> Unit`, `onCancel: (String) -> Unit`, `onChooseFolder: () -> Unit` with identical signatures.
- Produces: rewritten `UploadsScreen` + `OwnMessageBubble` composables with unchanged public signatures — consumed by Task 4's new tests and by `HomeRelayApp` with no wiring change.

- [ ] **Step 1: Rewrite UploadsScreen.kt**

Replace the entire file content with exactly:

```kotlin
package app.maw629.homerelay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.maw629.homerelay.data.UploadErrorCode
import app.maw629.homerelay.data.UploadState

@Composable
fun OwnMessageBubble(
    row: UploadRow,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onChooseFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = messageStatus(row.state)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            modifier = Modifier.testTag("messageBubble"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = row.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${row.sizeBytes} bytes",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = formatMessageTime(row.createdAtMillis),
                    style = MaterialTheme.typography.bodySmall
                )
                when (status) {
                    MessageStatus.SENDING -> {
                        Text(
                            text = if (row.state == UploadState.QUEUED) "Sending…" else "Uploading…",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (row.state == UploadState.QUEUED) {
                            TextButton(onClick = { onCancel(row.id) }) {
                                Text("Cancel")
                            }
                        }
                    }
                    MessageStatus.SENT -> {
                        Text(
                            text = "Sent ✓",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    MessageStatus.FAILED -> {
                        row.errorMessage?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = { onRetry(row.id) }) {
                            Text("Retry")
                        }
                        if (row.errorCode == UploadErrorCode.DESTINATION_ACCESS_LOST) {
                            TextButton(onClick = onChooseFolder) {
                                Text("Choose folder again")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UploadsScreen(
    modifier: Modifier = Modifier,
    uploads: List<UploadRow>,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onChooseFolder: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        reverseLayout = true
    ) {
        items(uploads, key = { it.id }) { upload ->
            OwnMessageBubble(
                row = upload,
                onRetry = onRetry,
                onCancel = onCancel,
                onChooseFolder = onChooseFolder
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Home Relay")
                Text("Recent uploads")
            }
        }
    }
}
```

Notes the implementer must respect: with `reverseLayout = true` the first composition item renders at the bottom, so the unchanged newest-first `uploads` order puts the newest message at the bottom and the header `item` declared last stays visually on top. Public signatures of both composables are unchanged, so `HomeRelayApp` needs no edit.

- [ ] **Step 2: Update the broken timestamp assertion**

In `app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt`, in `attentionUploadShowsCreationTimeAndActionableError`, replace:

```kotlin
        composeRule.onNodeWithText(Instant.ofEpochMilli(1_788_013_501_000).toString()).assertExists()
```

with:

```kotlin
        composeRule.onNodeWithText(formatMessageTime(1_788_013_501_000)).assertExists()
```

(`formatMessageTime` is in the same package `app.maw629.homerelay.ui`, so no import is needed. Remove the now-unused `import java.time.Instant` line from the test file.) All other existing tests in the file (`needsAttentionWithLostDestinationInvokesRetryAndFolderSelection`, `queuedUploadInvokesCancelWithItsId`, `completedUploadHasNoActions`) must keep passing unchanged — `Retry`, `Cancel`, and `Choose folder again` texts and callbacks are preserved by the rewrite.

- [ ] **Step 3: Verify compile plus JVM suite**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`
Expected: `BUILD SUCCESSFUL`. This proves the JVM suite is green and the rewritten screen plus updated instrumentation test compile. The instrumentation tests themselves cannot run on WSL — they execute in Task 5 on Windows with a device.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/maw629/homerelay/ui/UploadsScreen.kt app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt
git commit -m "feat: conversation bubbles for uploads"
```

---

### Task 4: Conversation Compose coverage (content, ordering, callbacks)

**Files:**
- Test only: `app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt`

**Interfaces:**
- Consumes: rewritten `UploadsScreen` from Task 3 (same signature), `formatMessageTime()` from Task 1.
- Produces: device-executed proof of bubble content, newest-at-bottom ordering, and end-aligned bubbles. No production change.

- [ ] **Step 1: Add the bubble-content test**

Append exactly to `app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt`:

```kotlin
    @Test
    fun completedBubbleShowsNameSizeTimeAndSent() {
        composeRule.setContent {
            UploadsScreen(
                uploads = listOf(
                    UploadRow(
                        id = "upload-1",
                        name = "report.pdf",
                        sizeBytes = 42,
                        createdAtMillis = 1_000_000_000_000L,
                        state = UploadState.COMPLETED,
                        errorCode = UploadErrorCode.NONE,
                        errorMessage = null
                    )
                ),
                onRetry = {},
                onCancel = {},
                onChooseFolder = {}
            )
        }

        composeRule.onNodeWithText("report.pdf").assertExists()
        composeRule.onNodeWithText("42 bytes").assertExists()
        composeRule.onNodeWithText(formatMessageTime(1_000_000_000_000L)).assertExists()
        composeRule.onNodeWithText("Sent ✓").assertExists()
    }
```

(`formatMessageTime` needs no import — same package. The exact `HH:mm` value is pinned by Task 1's JVM test; here it only proves wiring.)

- [ ] **Step 2: Add the newest-at-bottom ordering test**

Append exactly (new imports needed: `androidx.compose.ui.test.fetchSemanticsNode`, `org.junit.Assert.assertTrue`; `onNodeWithText` is already imported):

```kotlin
    @Test
    fun newestMessageRendersBelowOlderMessage() {
        composeRule.setContent {
            UploadsScreen(
                uploads = listOf(
                    UploadRow(
                        id = "new",
                        name = "new.pdf",
                        sizeBytes = 1,
                        createdAtMillis = 2L,
                        state = UploadState.COMPLETED,
                        errorCode = UploadErrorCode.NONE,
                        errorMessage = null
                    ),
                    UploadRow(
                        id = "old",
                        name = "old.pdf",
                        sizeBytes = 1,
                        createdAtMillis = 1L,
                        state = UploadState.COMPLETED,
                        errorCode = UploadErrorCode.NONE,
                        errorMessage = null
                    )
                ),
                onRetry = {},
                onCancel = {},
                onChooseFolder = {}
            )
        }

        val newTop = composeRule.onNodeWithText("new.pdf")
            .fetchSemanticsNode().boundsInRoot.top
        val oldTop = composeRule.onNodeWithText("old.pdf")
            .fetchSemanticsNode().boundsInRoot.top

        assertTrue(
            "Newest message must render below the older message",
            newTop > oldTop
        )
    }
```

(Two separate lookups are used instead of one `onAllNodes` query so no matcher combinator import is needed.)

- [ ] **Step 3: Add the end-alignment test**

Append exactly:

```kotlin
    @Test
    fun bubbleDoesNotSpanFullWidth() {
        composeRule.setContent {
            UploadsScreen(
                uploads = listOf(
                    UploadRow(
                        id = "upload-1",
                        name = "a.pdf",
                        sizeBytes = 1,
                        createdAtMillis = 1L,
                        state = UploadState.COMPLETED,
                        errorCode = UploadErrorCode.NONE,
                        errorMessage = null
                    )
                ),
                onRetry = {},
                onCancel = {},
                onChooseFolder = {}
            )
        }

        val bubble = composeRule.onNodeWithTag("messageBubble")
            .fetchSemanticsNode()
        assertTrue(
            "Own bubble must leave a left gutter for future incoming messages",
            bubble.boundsInRoot.left > 0
        )
    }
```

(This needs one more import: `androidx.compose.ui.test.onNodeWithTag`. The `messageBubble` tag is set in Task 3's `OwnMessageBubble`; if the tag is missing the test fails looking it up, which is the correct signal.)

- [ ] **Step 4: Verify compile on WSL (device run happens in Task 5)**

Run: `./gradlew assembleDebugAndroidTest`
Expected: `BUILD SUCCESSFUL` (proves the new tests compile; they execute on Windows with a device in Task 5).

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt
git commit -m "test: conversation bubble coverage"
```

---

### Task 5: Full verification and visual UAT

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

Expected: `adb devices` shows one device with state `device`; all tests pass, including the new `UploadsScreenTest.completedBubbleShowsNameSizeTimeAndSent`, `newestMessageRendersBelowOlderMessage`, `bubbleDoesNotSpanFullWidth`, and the updated `attentionUploadShowsCreationTimeAndActionableError`. Do not claim device behavior verified without this output. If `reverseLayout` bottom-stick misbehaves on the real device (spec Risk 1), stop and report back instead of working around it here — the fallback (ascending order plus explicit `scrollToItem`) is a spec-level decision.

- [ ] **Step 3: Visual UAT on the phone**

Install the debug APK (`adb install -r app\build\outputs\apk\debug\app-debug.apk`), queue files in each state (queued, uploading, completed, needs-attention, cancelled), and open the Uploads tab. Confirm: right-aligned bubbles with the file name, size, short time, and correct status row; newest at the bottom with bottom-stick on new arrivals; cancelled items absent; header still on top; no overlapping with system bars or the bottom navigation. Record date, device model, API level, commit, and results.

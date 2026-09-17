# Chat Chrome Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Uploads read as chat through UI-only scaffolding in 4 independently testable PRs (bubble readability, day dividers, header plus empty state, sender identity plus incoming scaffold).

**Architecture:** No model, queue, or transport change. `UploadRow` stays the message; pure helpers (`fileMonogram`, `dayKey`, `dayHeaderText`) feed region-owned edits to `UploadsScreen.kt` plus three small new composables. Each task owns a separate code region so later PRs never restructure earlier ones.

**Tech Stack:** Native Kotlin, Jetpack Compose Material3, JUnit4 (JVM) plus Compose UI test (instrumentation), Gradle wrapper only.

**Spec:** `docs/superpowers/specs/2026-09-17-chat-chrome-design.md`

## Global Constraints

- Package and namespace: `app.maw629.homerelay`.
- Support Android API 26 and later; compile SDK 37 and target SDK 37.
- Keep native Kotlin with Jetpack Compose; do not add a dependency-injection framework or any new dependency.
- No Room schema change; preserve DAO state transition guards; no new Room entities.
- Do not change intent handling, staging, WorkManager scheduling, SAF destination handling, notifications, Settings, or navigation.
- Exact user-visible copy: `Sending…`, `Uploading…`, `Sent ✓`, `Retry`, `Cancel`, `Choose folder again`, `Home Relay`, `Recent uploads`, proposed empty text `No uploads yet — shared files will appear here like messages`, sender label `Me`, meta separator `•`.
- `UploadsScreen` keeps `reverseLayout = true`, newest visually lowest, `testTag("messageBubble")`, and the left gutter for future incoming messages.
- TDD for every behavior change: red run first, then minimal implementation, then green run.
- Use Gradle wrapper scripts only (`./gradlew` on WSL, `.\gradlew.bat` on Windows). Kotlin toolchain Temurin 21.
- Never commit secrets, keystores, APK outputs, `build/`, `.gradle/`, logs, or user files.
- Do not claim a device behavior is verified without a connected-device or emulator result.

---

## File Structure

- Modify: `app/src/main/java/app/maw629/homerelay/ui/ChatMessage.kt` — add pure `fileMonogram()`, `dayKey()`, `dayHeaderText()` plus the `java.time.LocalDate` import. Single responsibility: conversation-presentation helpers with zero Android-framework calls.
- Create: `app/src/main/java/app/maw629/homerelay/ui/FileIcon.kt` — `FileTypeBadge` composable rendering the monogram in a Material3 `Surface`. Single responsibility: file-type mark without new dependencies or icon assets.
- Create: `app/src/main/java/app/maw629/homerelay/ui/DayHeader.kt` — centered date-divider pill with `testTag("dayHeader")`. Single responsibility: day separator rendering.
- Create: `app/src/main/java/app/maw629/homerelay/ui/IncomingBubble.kt` — left-aligned scaffold bubble, never called from `UploadsScreen` in this plan. Single responsibility: visual contract for the future inbox feature.
- Modify: `app/src/main/java/app/maw629/homerelay/ui/UploadsScreen.kt` — region-owned edits only: Task 1 bubble interior, Task 2 list grouping, Task 3 header/empty branch, Task 4 bubble header line. Public composable signatures unchanged.
- Create: `app/src/test/java/app/maw629/homerelay/ui/FileIconTest.kt` — JVM unit tests for `fileMonogram()`.
- Modify: `app/src/test/java/app/maw629/homerelay/ui/ChatMessageTest.kt` — append JVM unit tests for `dayKey()`/`dayHeaderText()`.
- Modify: `app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt` — append Compose tests per task; add the `onAllNodesWithTag` import in Task 2.
- Create: `app/src/androidTest/java/app/maw629/homerelay/ui/IncomingBubbleTest.kt` — isolated scaffold tests.

Plan note vs spec: the spec says `FileIcon.kt` maps extension/MIME to a Material icon. This plan implements it as a text monogram badge (`PDF`, `JPG`, `···`) because the dependency catalog (`gradle/libs.versions.toml`) has no material-icons library and the global constraint forbids new dependencies. If an icon font is wanted later, swap only the badge interior; the `testTag("fileBadge")` contract stays.

Task order is the merge order (1 → 2 → 3 → 4). Each task is one PR: full WSL gate green plus its listed tests before merge; Windows `connectedDebugAndroidTest` for the touched Compose suite before merge per `AGENTS.md`.

---

### Task 1: Bubble readability — file badge plus size-time meta row (PR1)

**Files:**
- Create: `app/src/main/java/app/maw629/homerelay/ui/FileIcon.kt`
- Modify: `app/src/main/java/app/maw629/homerelay/ui/ChatMessage.kt` (append `fileMonogram()`)
- Modify: `app/src/main/java/app/maw629/homerelay/ui/UploadsScreen.kt` (`OwnMessageBubble` interior only; add the `Alignment` import)
- Create: `app/src/test/java/app/maw629/homerelay/ui/FileIconTest.kt`
- Test: `app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt` (append badge/meta test)

**Interfaces:**
- Consumes: `UploadRow` (`name`, `sizeBytes`, `createdAtMillis`, `state`), `formatFileSize()`, `formatMessageTime()`.
- Produces: `fun fileMonogram(fileName: String): String`, `@Composable fun FileTypeBadge(monogram: String, modifier: Modifier = Modifier)` with `testTag("fileBadge")` — reused by Task 4's incoming scaffold.

- [ ] **Step 1: Write the failing JVM test**

Create `app/src/test/java/app/maw629/homerelay/ui/FileIconTest.kt` with exactly:

```kotlin
package app.maw629.homerelay.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FileIconTest {
    @Test
    fun pdfExtensionMapsToPdfMonogram() {
        assertEquals("PDF", fileMonogram("report.pdf"))
    }

    @Test
    fun extensionIsUppercased() {
        assertEquals("JPG", fileMonogram("photo.jpg"))
    }

    @Test
    fun missingExtensionMapsToEllipsis() {
        assertEquals("···", fileMonogram("README"))
    }

    @Test
    fun lastExtensionWins() {
        assertEquals("GZ", fileMonogram("archive.tar.gz"))
    }

    @Test
    fun longExtensionTruncatesToFourChars() {
        assertEquals("JPEG", fileMonogram("photo.jpeg2000"))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "app.maw629.homerelay.ui.FileIconTest"`
Expected: FAIL with "unresolved reference: fileMonogram"

- [ ] **Step 3: Write the minimal pure helper**

Append to `app/src/main/java/app/maw629/homerelay/ui/ChatMessage.kt`:

```kotlin
fun fileMonogram(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").trim().uppercase()
    if (ext.isEmpty()) return "···"
    return ext.take(4)
}
```

- [ ] **Step 4: Re-run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "app.maw629.homerelay.ui.FileIconTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Write the failing Compose test**

Append to `app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt`:

```kotlin
    @Test
    fun bubbleShowsFileBadgeAndMetaSeparator() {
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

        composeRule.onNodeWithTag("fileBadge").assertExists()
        composeRule.onNodeWithText("PDF").assertExists()
        composeRule.onNodeWithText("•").assertExists()
        composeRule.onNodeWithText("42 bytes").assertExists()
        composeRule.onNodeWithText("Sent ✓").assertExists()
    }
```

- [ ] **Step 6: Create the badge composable and rewire the bubble interior**

Create `app/src/main/java/app/maw629/homerelay/ui/FileIcon.kt` with exactly:

```kotlin
package app.maw629.homerelay.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun FileTypeBadge(monogram: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.testTag("fileBadge"),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = monogram,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}
```

In `app/src/main/java/app/maw629/homerelay/ui/UploadsScreen.kt`, add the import `androidx.compose.ui.Alignment`, then replace the name `Text` block:

```kotlin
                Text(
                    text = row.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )
```

with:

```kotlin
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FileTypeBadge(monogram = fileMonogram(row.name))
                    Text(
                        text = row.name,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
```

and replace the size/time `Text` pair:

```kotlin
                Text(
                    text = formatFileSize(row.sizeBytes),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = formatMessageTime(row.createdAtMillis),
                    style = MaterialTheme.typography.bodySmall
                )
```

with the meta row of separate nodes (keeps existing text assertions passing):

```kotlin
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = formatFileSize(row.sizeBytes),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = formatMessageTime(row.createdAtMillis),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
```

Status row (`Sending…`/`Uploading…`/`Sent ✓`/Retry/Cancel/`Choose folder again`) is untouched.

- [ ] **Step 7: Run the PR1 gate**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`
Expected: PASS with no new warnings. Then on Windows with a device/emulator:

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=app.maw629.homerelay.ui.UploadsScreenTest'
```

Expected: PASS, including the 7 pre-existing `UploadsScreenTest` tests unmodified.

- [ ] **Step 8: Commit (PR1)**

```bash
git add app/src/main/java/app/maw629/homerelay/ui/FileIcon.kt app/src/main/java/app/maw629/homerelay/ui/ChatMessage.kt app/src/main/java/app/maw629/homerelay/ui/UploadsScreen.kt app/src/test/java/app/maw629/homerelay/ui/FileIconTest.kt app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt
git commit -m "feat: file badge and meta row in upload bubbles"
```

### Task 2: Day grouping plus dividers (PR2)

**Files:**
- Modify: `app/src/main/java/app/maw629/homerelay/ui/ChatMessage.kt` (append `dayKey()`, `dayHeaderText()`; add `java.time.LocalDate` import)
- Create: `app/src/main/java/app/maw629/homerelay/ui/DayHeader.kt`
- Modify: `app/src/main/java/app/maw629/homerelay/ui/UploadsScreen.kt` (`UploadsScreen` list body only; bubbles from Task 1 reused untouched)
- Modify: `app/src/test/java/app/maw629/homerelay/ui/ChatMessageTest.kt` (append day tests)
- Test: `app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt` (append divider test; add `onAllNodesWithTag` import)

**Interfaces:**
- Consumes: `fileMonogram`/`FileTypeBadge` from Task 1 (untouched), `formatMessageTime` day rules, `messageDateFormat` (same-file private, reused for the 7+ day case).
- Produces: `fun dayKey(createdAtMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate`, `fun dayHeaderText(dayStartMillis: Long, nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String`, `@Composable fun DayHeader(text: String, modifier: Modifier = Modifier)` with `testTag("dayHeader")`.

- [ ] **Step 1: Write the failing JVM tests**

Append to `app/src/test/java/app/maw629/homerelay/ui/ChatMessageTest.kt`, inside the class:

```kotlin
    @Test
    fun dayKeyResolvesUtcCalendarDay() {
        assertEquals(
            java.time.LocalDate.of(2001, 9, 9),
            dayKey(1_000_000_000_000L, ZoneId.of("UTC"))
        )
    }

    @Test
    fun sameDayHeaderShowsToday() {
        assertEquals(
            "Today",
            dayHeaderText(1_000_000_060_000L, 1_000_003_600_000L, ZoneId.of("UTC"))
        )
    }

    @Test
    fun previousDayHeaderShowsYesterday() {
        assertEquals(
            "Yesterday",
            dayHeaderText(1_000_000_000_000L, 1_000_086_400_000L, ZoneId.of("UTC"))
        )
    }

    @Test
    fun threeDaysAgoHeaderShowsWeekday() {
        assertEquals(
            "Sunday",
            dayHeaderText(1_000_000_000_000L, 1_000_259_200_000L, ZoneId.of("UTC"))
        )
    }

    @Test
    fun sevenDaysAgoHeaderShowsDate() {
        assertEquals(
            "2001/09/09",
            dayHeaderText(1_000_000_000_000L, 1_000_604_800_000L, ZoneId.of("UTC"))
        )
    }

    @Test
    fun futureHeaderShowsToday() {
        assertEquals(
            "Today",
            dayHeaderText(1_000_003_600_000L, 1_000_000_000_000L, ZoneId.of("UTC"))
        )
    }
```

(Epoch values mirror the existing `formatMessageTime` tests: `1_000_000_000_000L` is Sunday 2001-09-09 01:46 UTC.)

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "app.maw629.homerelay.ui.ChatMessageTest"`
Expected: FAIL with "unresolved reference: dayKey"

- [ ] **Step 3: Write the minimal helpers**

In `app/src/main/java/app/maw629/homerelay/ui/ChatMessage.kt`, add the import `java.time.LocalDate` and append:

```kotlin
fun dayKey(
    createdAtMillis: Long,
    zone: ZoneId = ZoneId.systemDefault()
): LocalDate = Instant.ofEpochMilli(createdAtMillis).atZone(zone).toLocalDate()

fun dayHeaderText(
    dayStartMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val day = Instant.ofEpochMilli(dayStartMillis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val daysAgo = ChronoUnit.DAYS.between(day, today).toInt()
    return when {
        daysAgo <= 0 -> "Today"
        daysAgo == 1 -> "Yesterday"
        daysAgo < 7 -> day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        else -> day.format(messageDateFormat)
    }
}
```

- [ ] **Step 4: Re-run to verify green**

Run: `./gradlew :app:testDebugUnitTest --tests "app.maw629.homerelay.ui.ChatMessageTest"`
Expected: PASS (all pre-existing plus 6 new tests)

- [ ] **Step 5: Write the failing divider Compose test**

In `app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt`, add the import `androidx.compose.ui.test.onAllNodesWithTag` and append:

```kotlin
    @Test
    fun dividersGroupUploadsByDay() {
        val now = System.currentTimeMillis()
        fun row(id: String, createdAt: Long) = UploadRow(
            id = id,
            name = "$id.pdf",
            sizeBytes = 1,
            createdAtMillis = createdAt,
            state = UploadState.COMPLETED,
            errorCode = UploadErrorCode.NONE,
            errorMessage = null
        )
        composeRule.setContent {
            UploadsScreen(
                uploads = listOf(
                    row("new-a", now),
                    row("new-b", now - 60_000L),
                    row("old", now - 10L * 86_400_000L)
                ),
                onRetry = {},
                onCancel = {},
                onChooseFolder = {}
            )
        }

        composeRule.onAllNodesWithTag("dayHeader").assertCountEquals(2)
        composeRule.onNodeWithText("new-a.pdf").assertExists()
        composeRule.onNodeWithText("old.pdf").assertExists()
    }
```

- [ ] **Step 6: Create `DayHeader` and group the list without re-sorting**

Create `app/src/main/java/app/maw629/homerelay/ui/DayHeader.kt` with exactly:

```kotlin
package app.maw629.homerelay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun DayHeader(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.testTag("dayHeader"),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
```

In `app/src/main/java/app/maw629/homerelay/ui/UploadsScreen.kt`, replace the list body:

```kotlin
    ) {
        items(uploads, key = { it.id }) { upload ->
            OwnMessageBubble(
                row = upload,
                onRetry = onRetry,
                onCancel = onCancel,
                onChooseFolder = onChooseFolder
            )
        }
```

with grouped rendering that preserves the existing newest-first order (`groupBy` returns a `LinkedHashMap` in first-seen order; no re-sorting, so `reverseLayout` bottom-stick behavior is unchanged). Messages are emitted before their header within each group because `reverseLayout` lays the first-composed item at the bottom — header-after lands the divider visually above its day's messages (Messenger/Zalo order):

```kotlin
    ) {
        val grouped = uploads.groupBy { dayKey(it.createdAtMillis) }
        grouped.forEach { (day, dayUploads) ->
            items(dayUploads, key = { it.id }) { upload ->
                OwnMessageBubble(
                    row = upload,
                    onRetry = onRetry,
                    onCancel = onCancel,
                    onChooseFolder = onChooseFolder
                )
            }
            item(key = "day-$day") {
                DayHeader(text = dayHeaderText(dayUploads.first().createdAtMillis))
            }
        }
```

The tail header item (`Home Relay` / `Recent uploads`) stays last in composition order so it remains visually on top.

- [ ] **Step 7: Run the PR2 gate**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`
Expected: PASS. Then on Windows:

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=app.maw629.homerelay.ui.UploadsScreenTest'
```

Expected: PASS, including `newestMessageRendersBelowOlderMessage` and `bubbleDoesNotSpanFullWidth` unmodified.

- [ ] **Step 8: Commit (PR2)**

```bash
git add app/src/main/java/app/maw629/homerelay/ui/ChatMessage.kt app/src/main/java/app/maw629/homerelay/ui/DayHeader.kt app/src/main/java/app/maw629/homerelay/ui/UploadsScreen.kt app/src/test/java/app/maw629/homerelay/ui/ChatMessageTest.kt app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt
git commit -m "feat: day dividers grouping uploads by calendar day"
```

### Task 3: Conversation header plus empty state (PR3)

**Files:**
- Modify: `app/src/main/java/app/maw629/homerelay/ui/UploadsScreen.kt` (header/empty branch only)
- Test: `app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt` (append empty-state tests)

**Interfaces:**
- Consumes: grouped list and header item from Task 2 (untouched).
- Produces: `testTag("emptyState")` node shown only when `uploads.isEmpty()`; header texts `Home Relay` / `Recent uploads` retained and restyled.

- [ ] **Step 1: Write the failing Compose tests**

Append to `app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt`:

```kotlin
    @Test
    fun emptyQueueShowsHeaderAndEmptyState() {
        composeRule.setContent {
            UploadsScreen(
                uploads = emptyList(),
                onRetry = {},
                onCancel = {},
                onChooseFolder = {}
            )
        }

        composeRule.onNodeWithTag("emptyState").assertExists()
        composeRule.onNodeWithText("No uploads yet — shared files will appear here like messages").assertExists()
        composeRule.onNodeWithText("Home Relay").assertExists()
        composeRule.onNodeWithText("Recent uploads").assertExists()
    }

    @Test
    fun nonEmptyQueueHidesEmptyState() {
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

        composeRule.onNodeWithTag("emptyState").assertDoesNotExist()
    }
```

- [ ] **Step 2: Replace the tail header item with header plus empty branch**

In `app/src/main/java/app/maw629/homerelay/ui/UploadsScreen.kt`, replace:

```kotlin
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Home Relay")
                Text("Recent uploads")
            }
        }
```

with:

```kotlin
        if (uploads.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "No uploads yet — shared files will appear here like messages",
                        modifier = Modifier.testTag("emptyState")
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Home Relay",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Recent uploads",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
```

Bubbles and dividers are untouched.

- [ ] **Step 3: Run the PR3 gate**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`
Expected: PASS. Then on Windows:

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=app.maw629.homerelay.ui.UploadsScreenTest'
```

Expected: PASS.

- [ ] **Step 4: Commit (PR3)**

```bash
git add app/src/main/java/app/maw629/homerelay/ui/UploadsScreen.kt app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt
git commit -m "feat: conversation header and empty state for uploads"
```

### Task 4: Sender identity plus incoming-bubble scaffold (PR4)

**Files:**
- Modify: `app/src/main/java/app/maw629/homerelay/ui/UploadsScreen.kt` (`OwnMessageBubble` header lines only; add `CircleShape` import; `Alignment` import already added in Task 1)
- Create: `app/src/main/java/app/maw629/homerelay/ui/IncomingBubble.kt`
- Test: `app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt` (append identity test)
- Create: `app/src/androidTest/java/app/maw629/homerelay/ui/IncomingBubbleTest.kt`

**Interfaces:**
- Consumes: `FileTypeBadge`/`fileMonogram` from Task 1, meta-row structure from Task 1, list structure from Task 2. No DataStore change: sender is hardcoded `Me` with initial `M`; editable profile belongs to the follow-up spec, not this plan.
- Produces: `testTag("senderAvatar")` on own bubbles; `@Composable fun IncomingBubble(name: String, sizeBytes: Long, createdAtMillis: Long, senderName: String, modifier: Modifier = Modifier)` with `testTag("incomingBubble")`, never called from `UploadsScreen` in this plan (referenced by its test, so no unused-code lint).

- [ ] **Step 1: Write the failing identity test**

Append to `app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt`:

```kotlin
    @Test
    fun ownBubbleShowsSenderIdentity() {
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

        composeRule.onNodeWithText("Me").assertExists()
        composeRule.onNodeWithTag("senderAvatar").assertExists()
    }
```

- [ ] **Step 2: Write the failing scaffold test**

Create `app/src/androidTest/java/app/maw629/homerelay/ui/IncomingBubbleTest.kt` with exactly:

```kotlin
package app.maw629.homerelay.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class IncomingBubbleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun scaffoldRendersSenderNameAndFile() {
        composeRule.setContent {
            IncomingBubble(
                name = "dinner.jpg",
                sizeBytes = 2048,
                createdAtMillis = 1_000_000_000_000L,
                senderName = "Mom"
            )
        }

        composeRule.onNodeWithTag("incomingBubble").assertExists()
        composeRule.onNodeWithText("Mom").assertExists()
        composeRule.onNodeWithText("dinner.jpg").assertExists()
    }

    @Test
    fun scaffoldIsLeftAligned() {
        composeRule.setContent {
            IncomingBubble(
                name = "a.pdf",
                sizeBytes = 1,
                createdAtMillis = 1L,
                senderName = "Mom"
            )
        }

        val left = composeRule.onNodeWithTag("incomingBubble")
            .fetchSemanticsNode().boundsInRoot.left
        assertTrue("Incoming bubble must dock to the start edge, got $left", left <= 1f)
    }
}
```

- [ ] **Step 3: Add the sender header to `OwnMessageBubble` (additive, above the file row)**

In `app/src/main/java/app/maw629/homerelay/ui/UploadsScreen.kt`, add the import `androidx.compose.foundation.shape.CircleShape`, then inside `OwnMessageBubble`'s inner `Column`, insert as the first child (before the file `Row` from Task 1):

```kotlin
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.testTag("senderAvatar"),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondary
                    ) {
                        Text(
                            text = "M",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Text(
                        text = "Me",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
```

Meta row, badge row, and status row from Task 1 are untouched.

- [ ] **Step 4: Create the incoming scaffold (never wired to the list)**

Create `app/src/main/java/app/maw629/homerelay/ui/IncomingBubble.kt` with exactly:

```kotlin
package app.maw629.homerelay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun IncomingBubble(
    name: String,
    sizeBytes: Long,
    createdAtMillis: Long,
    senderName: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.testTag("incomingBubble"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FileTypeBadge(monogram = fileMonogram(name))
                    Text(
                        text = name,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = formatFileSize(sizeBytes),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = formatMessageTime(createdAtMillis),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
```

Do not reference `IncomingBubble` from `UploadsScreen`; it is referenced by `IncomingBubbleTest`, so no unused-code lint is expected and no `@Suppress` is added.

- [ ] **Step 5: Run the PR4 gate**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`
Expected: PASS with no new warnings. Then on Windows:

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=app.maw629.homerelay.ui.UploadsScreenTest'
.\gradlew.bat connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=app.maw629.homerelay.ui.IncomingBubbleTest'
```

Expected: PASS, including `bubbleDoesNotSpanFullWidth` still green (own bubbles keep their left gutter).

- [ ] **Step 6: Commit (PR4)**

```bash
git add app/src/main/java/app/maw629/homerelay/ui/UploadsScreen.kt app/src/main/java/app/maw629/homerelay/ui/IncomingBubble.kt app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt app/src/androidTest/java/app/maw629/homerelay/ui/IncomingBubbleTest.kt
git commit -m "feat: sender identity and incoming bubble scaffold"
```

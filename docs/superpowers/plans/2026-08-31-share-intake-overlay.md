# Durable Share Intake And Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Android file sharing durably stage each accepted URI before the receiver can close, show a transparent centered status overlay, and return to the source app after a build-configured delay.

**Architecture:** Extend the existing Room-backed `upload_items` queue with a `STAGING` state, then introduce an application-owned `ShareIntakeCoordinator` that owns staging jobs and their observable operation status. The receiver observes one coordinator operation through a saved-state ViewModel, preserving a single active intake across recreation while app startup converts incomplete staging rows into visible, non-retryable recovery records.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, AndroidX Activity and Lifecycle SavedState, Room migrations, DataStore, WorkManager, Android instrumentation, Robolectric, Gradle Kotlin DSL.

**Spec:** `docs/superpowers/specs/2026-08-31-share-intake-overlay-design.md`

## Global Constraints

- Keep package and namespace `app.maw629.homerelay`; retain the user-visible label `Home Relay`.
- Support Android API 26 and later, with compile and target SDK 37.
- Use only native Kotlin, Compose, Room, DataStore, SAF, WorkManager, and NotificationCompat; do not add a dependency-injection framework.
- Accept only `ACTION_SEND` and `ACTION_SEND_MULTIPLE` file `content://` URIs; never persist external temporary URI grants, accept text shares, or accept `file://` URIs.
- Create a durable `STAGING` Room row before opening an accepted source URI. If that initial row cannot be stored, do not open the URI and report queue storage unavailable.
- Preserve one Room database, one selected SAF destination, unique work name `upload:<itemId>`, connected-network constraints, exponential backoff, and the existing upload state guards.
- Provider-write completion is not cloud or Windows synchronization completion.
- Use `SHARE_STATUS_DISPLAY_MILLIS` only as a build-time environment variable. Its unset, invalid, negative, or overflowing value compiles as the 2,000 ms default.
- Maintain safe drawing behavior for Android 15+ edge-to-edge and transparent system bars for the receiver on API 26+.
- All code changes follow red-green TDD. Run WSL validation with `./gradlew testDebugUnitTest lintDebug assembleDebug`; do not claim connected-device validation without a device or emulator.

---

### Task 1: Start From The Approved Design Baseline

**Files:**
- Preserve: `docs/superpowers/specs/2026-08-31-share-intake-overlay-design.md`
- Revert to committed baseline before implementation: `README.md`
- Revert to committed baseline before implementation: `app/build.gradle.kts`
- Revert to committed baseline before implementation: `app/src/androidTest/java/app/maw629/homerelay/share/ShareReceiverActivityTest.kt`
- Revert to committed baseline before implementation: `app/src/main/AndroidManifest.xml`
- Revert to committed baseline before implementation: `app/src/main/java/app/maw629/homerelay/share/ShareReceiverActivity.kt`
- Revert to committed baseline before implementation: `app/src/main/res/values/themes.xml`
- Revert to committed baseline before implementation: `docs/architecture.md`
- Delete provisional test: `app/src/test/java/app/maw629/homerelay/share/ShareReceiverActivityRobolectricTest.kt`

**Interfaces:**
- Consumes: design commit `e4b3157` and the approved specification.
- Produces: a clean implementation baseline without the earlier activity-bound overlay prototype, so every production behavior introduced by this plan has a preceding failing test.

- [ ] **Step 1: Confirm the worktree contains only the known provisional prototype and the approved design commit**

Run:

```bash
git status --short
git log --oneline -5
git diff --check
```

Expected: `e4b3157 docs: specify durable share intake` is in history. The listed modified source files are the earlier, uncommitted transparent-overlay prototype; no unrelated user changes are present.

- [ ] **Step 2: Remove only the provisional implementation with an explicit patch**

Use `apply_patch` to revert the named prototype files to their `HEAD` contents and delete the untracked local test. Do not revert or delete the committed spec, the current plan, or unrelated worktree files. Before applying the patch, use `git diff -- <each named file>` to confirm it still contains only the known prototype change.

Expected: only the new plan file is untracked. The production receiver again has no auto-finish or transparent receiver theme. This reset is required by TDD because the prototype was written before the approved durable-intake design and must not become an implementation reference.

- [ ] **Step 3: Establish the baseline test result**

Run:

```bash
./gradlew testDebugUnitTest
```

Expected: PASS. Record environment warnings separately from failures; the current JDK 25 Kotlin fallback warning is pre-existing and not a test failure.

### Task 2: Add Durable Staging State And Room Migration

**Files:**
- Modify: `app/src/main/java/app/maw629/homerelay/data/UploadItem.kt`
- Modify: `app/src/main/java/app/maw629/homerelay/data/UploadDao.kt`
- Modify: `app/src/main/java/app/maw629/homerelay/data/HomeRelayDatabase.kt`
- Modify: `app/src/main/java/app/maw629/homerelay/HomeRelayApplication.kt`
- Create: `app/schemas/app.maw629.homerelay.data.HomeRelayDatabase/2.json`
- Modify: `app/src/androidTest/java/app/maw629/homerelay/data/UploadDaoTest.kt`
- Modify: `app/src/test/java/app/maw629/homerelay/domain/UploadRepositoryTest.kt`
- Modify: `app/src/test/java/app/maw629/homerelay/ui/HomeRelayViewModelTest.kt`
- Modify: `app/src/androidTest/java/app/maw629/homerelay/work/UploadWorkerTest.kt`

**Interfaces:**
- Consumes: existing version-1 `upload_items` schema and current worker contract that claims only `QUEUED` rows.
- Produces: `UploadState.STAGING`, `UploadErrorCode.SHARE_INTERRUPTED`, `UploadDao.completeStaging(...)`, `UploadDao.failStaging(...)`, and `UploadDao.stagingItems()`.
- Produces: `HomeRelayDatabase.MIGRATION_1_2`, registered by the one production `Room.databaseBuilder` call.

- [ ] **Step 1: Write the failing DAO and migration instrumentation tests**

Add a test that creates a `STAGING` item, calls `completeStaging`, and asserts all terminal persistence properties by literal value:

```kotlin
@Test
fun completeStagingQueuesOnlyTheMatchingStagingRow() = runTest {
    dao.insert(sampleUpload(state = UploadState.STAGING))

    assertEquals(
        1,
        dao.completeStaging(
            id = "upload-1",
            originalName = "provider-report.pdf",
            outputName = "20260831-120000-a1b2c3-provider-report.pdf",
            byteSize = 42L
        )
    )

    val item = dao.get("upload-1")!!
    assertEquals(UploadState.QUEUED, item.state)
    assertEquals(UploadErrorCode.NONE, item.errorCode)
    assertEquals("provider-report.pdf", item.originalName)
    assertEquals(42L, item.byteSize)
}
```

Add a separate guarded-transition test where the row is already `QUEUED` and `completeStaging(...)` returns `0`. Add a recovery test that reads `dao.stagingItems()`, calls `failStaging(id, SHARE_INTERRUPTED)`, and asserts the row becomes `NEEDS_ATTENTION`.

Add a `MigrationTestHelper` test that creates a schema-version-1 database containing one queued row, migrates it with `HomeRelayDatabase.MIGRATION_1_2`, and asserts the old row retains its ID, path, state, and error code. Use the supplied version-1 schema JSON rather than recreating version-1 SQL in the test.

- [ ] **Step 2: Run the instrumentation test compilation to verify the APIs do not exist yet**

Run:

```bash
./gradlew assembleDebugAndroidTest
```

Expected: FAIL during `compileDebugAndroidTestKotlin` because `STAGING`, `SHARE_INTERRUPTED`, DAO staging methods, and `MIGRATION_1_2` do not exist.

- [ ] **Step 3: Add the minimum persisted model and guarded DAO transitions**

Update the enums and DAO with these exact persistence semantics:

```kotlin
enum class UploadState {
    STAGING,
    QUEUED,
    UPLOADING,
    COMPLETED,
    NEEDS_ATTENTION,
    CANCELLED
}

enum class UploadErrorCode {
    NONE,
    SOURCE_UNREADABLE,
    STAGING_STORAGE_FULL,
    SHARE_INTERRUPTED,
    DESTINATION_ACCESS_LOST,
    DESTINATION_QUOTA,
    DESTINATION_POLICY,
    WRITE_OUTCOME_UNKNOWN
}
```

Add DAO methods equivalent to:

```kotlin
@Query("""
    UPDATE upload_items
    SET originalName = :originalName, outputName = :outputName, byteSize = :byteSize,
        state = 'QUEUED', errorCode = 'NONE'
    WHERE id = :id AND state = 'STAGING'
""")
suspend fun completeStaging(
    id: String,
    originalName: String,
    outputName: String,
    byteSize: Long
): Int

@Query("""
    UPDATE upload_items SET state = 'NEEDS_ATTENTION', errorCode = :errorCode
    WHERE id = :id AND state = 'STAGING'
""")
suspend fun failStaging(id: String, errorCode: UploadErrorCode): Int

@Query("SELECT * FROM upload_items WHERE state = 'STAGING' ORDER BY createdAtMillis ASC")
suspend fun stagingItems(): List<UploadItem>
```

Keep `beginUpload` restricted to `QUEUED`; do not permit a worker to claim `STAGING`. Increase `HomeRelayDatabase` to version 2. Define an explicit no-op `Migration(1, 2)` because enum values are stored as text and no table column changes, then register it in `AppContainer`'s `Room.databaseBuilder` call. Let KSP generate the version-2 schema; do not hand-edit JSON.

Update every in-repository fake `UploadDao` to implement the new interface. Its `completeStaging` and `failStaging` implementations must enforce the same `STAGING` guard as production rather than returning unconditional success.

- [ ] **Step 4: Run the focused instrumentation tests to verify they pass**

Run on a connected emulator/device:

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=app.maw629.homerelay.data.UploadDaoTest'
```

Expected: PASS, including guarded transitions and version-1-to-version-2 migration. If no device is attached, run `./gradlew assembleDebugAndroidTest` and record that the instrumentation behavior remains pending device execution.

- [ ] **Step 5: Commit the durable database state**

```bash
git add app/src/main/java/app/maw629/homerelay/data/UploadItem.kt app/src/main/java/app/maw629/homerelay/data/UploadDao.kt app/src/main/java/app/maw629/homerelay/data/HomeRelayDatabase.kt app/src/main/java/app/maw629/homerelay/HomeRelayApplication.kt app/schemas/app.maw629.homerelay.data.HomeRelayDatabase/2.json app/src/androidTest/java/app/maw629/homerelay/data/UploadDaoTest.kt app/src/test/java/app/maw629/homerelay/domain/UploadRepositoryTest.kt app/src/test/java/app/maw629/homerelay/ui/HomeRelayViewModelTest.kt app/src/androidTest/java/app/maw629/homerelay/work/UploadWorkerTest.kt
git commit -m "feat: persist share staging state"
```

### Task 3: Make Private Staging Target A Pre-Recorded Queue Path

**Files:**
- Modify: `app/src/main/java/app/maw629/homerelay/share/ShareStager.kt`
- Modify: `app/src/androidTest/java/app/maw629/homerelay/share/AndroidShareStagerTest.kt`
- Create: `app/src/androidTest/java/app/maw629/homerelay/share/SampleContentProvider.kt`
- Modify: `app/src/test/java/app/maw629/homerelay/domain/UploadRepositoryTest.kt`

**Interfaces:**
- Consumes: a staging row ID generated before source access.
- Produces: `ShareStager.pendingFile(id: String): File` and `ShareStager.stage(target: File, share: IncomingShare): StageResult`.
- Produces: `StageResult.Staged.file` equal to the supplied target, allowing a preexisting Room row to own the final private path.

- [ ] **Step 1: Write failing stager tests for caller-owned targets**

Replace the implicit ID-generated staging call in `AndroidShareStagerTest` with a caller-created target. Assert the source bytes are copied into exactly that target and that a failed stream leaves neither a partial file nor a final target:

```kotlin
val target = stager.pendingFile("item-1")
val result = stager.stage(
    target,
    IncomingShare(Uri.parse("content://app.maw629.homerelay.share-test/report.pdf"), "report.pdf", "application/pdf")
)

assertTrue(result is StageResult.Staged)
assertEquals(target.canonicalFile, (result as StageResult.Staged).file.canonicalFile)
assertEquals("hello", target.readText())
```

Name the break the test catches: generating a second, unstored filename after the Room staging row was created would make the queue point to a file that does not exist.

- [ ] **Step 2: Run the focused test to verify it fails**

Run on a device/emulator:

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=app.maw629.homerelay.share.AndroidShareStagerTest'
```

Expected: FAIL to compile because `pendingFile` and target-based `stage` are absent.

- [ ] **Step 3: Implement deterministic path creation and target-based staging**

Change the interface and Android implementation to this shape:

```kotlin
interface ShareStager {
    fun pendingFile(id: String): File
    suspend fun stage(target: File, share: IncomingShare): StageResult
}
```

`pendingFile` returns a safe filename under `context.noBackupFilesDir/pending` without creating or opening it. Use a fixed name derived only from the supplied row ID, such as `<safe-id>.staged`; do not include the provider display name because it is resolved only while staging.

`stage` creates the parent directory, writes to a temporary `.partial` file in that same directory, and renames only after the copy succeeds. Retain the existing `content://` rejection and `IOException`/`SecurityException` mapping. On every non-success path, delete its temporary file and delete the supplied final target if it was created. Do not delete a successful target; the queue row now owns it.

Create the missing `SampleContentProvider` declared in `app/src/androidTest/AndroidManifest.xml` at authority `app.maw629.homerelay.share-test`. It must serve the literal `hello` bytes from `report.pdf` and return `report.pdf` through `OpenableColumns.DISPLAY_NAME`; use this real stream in the test from Step 1. Add a test-only blocking endpoint whose input stream signals a test-owned latch before waiting for a test release latch. Keep the latch/reset helpers in the androidTest provider, never in production source. Task 6 uses this endpoint to prove Back and recreation behavior without asserting mock calls.

- [ ] **Step 4: Run the focused stager tests to verify they pass**

Run the command from Step 2. Expected: PASS on device/emulator. Without a device, `./gradlew assembleDebugAndroidTest` must pass and the device test remains explicitly pending.

- [ ] **Step 5: Commit deterministic private staging**

```bash
git add app/src/main/java/app/maw629/homerelay/share/ShareStager.kt app/src/androidTest/java/app/maw629/homerelay/share/AndroidShareStagerTest.kt app/src/androidTest/java/app/maw629/homerelay/share/SampleContentProvider.kt app/src/test/java/app/maw629/homerelay/domain/UploadRepositoryTest.kt
git commit -m "feat: stage shares at durable queue paths"
```

### Task 4: Extend The Queue Repository For Staging And Retry Eligibility

**Files:**
- Modify: `app/src/main/java/app/maw629/homerelay/domain/UploadRepository.kt`
- Modify: `app/src/test/java/app/maw629/homerelay/domain/UploadRepositoryTest.kt`
- Modify: `app/src/test/java/app/maw629/homerelay/ui/HomeRelayViewModelTest.kt`
- Modify: `app/src/androidTest/java/app/maw629/homerelay/work/UploadWorkerTest.kt`

**Interfaces:**
- Consumes: `UploadDao` staging transitions from Task 2 and `StageResult.Staged` from Task 3.
- Produces: `suspend fun createStaging(share: IncomingShare, stagedPathForId: (String) -> String): UploadItem`.
- Produces: `suspend fun completeStaging(item: UploadItem, staged: StageResult.Staged): Boolean`, `suspend fun failStaging(id: String, error: UploadErrorCode): Boolean`, and `suspend fun recoverInterruptedStaging(): Int`.
- Produces: `UploadErrorCode.isRetryable()` with `false` for `NONE`, `SOURCE_UNREADABLE`, `STAGING_STORAGE_FULL`, and `SHARE_INTERRUPTED`.

- [ ] **Step 1: Write failing repository unit tests**

Add one test for each externally observable queue rule:

```kotlin
@Test
fun createStagingPersistsBeforeReturningItsTargetPath() = runTest {
    val item = repository.createStaging(
        IncomingShare(Uri.parse("content://sender/report"), "report.pdf", "application/pdf")
    ) { id -> "/pending/$id.staged" }

    assertEquals(UploadState.STAGING, dao.get(item.id)!!.state)
    assertEquals("/pending/new-item.staged", dao.get(item.id)!!.stagedPath)
}

@Test
fun completeStagingSchedulesOnlyAfterTheQueuedTransition() = runTest {
    val item = insertStagingItem()

    assertTrue(repository.completeStaging(item, StageResult.Staged(File(item.stagedPath), 42, "report.pdf")))

    assertEquals(UploadState.QUEUED, dao.get(item.id)!!.state)
    assertEquals(listOf(item.id), scheduler.scheduledIds)
}

@Test
fun interruptedStagingBecomesNonRetryableAttentionAndDeletesPrivateFile() = runTest {
    val staged = File.createTempFile("staging", ".file")
    dao.insert(item("item-1", UploadState.STAGING, stagedPath = staged.absolutePath))

    repository.recoverInterruptedStaging()

    assertEquals(UploadErrorCode.SHARE_INTERRUPTED, dao.get("item-1")!!.errorCode)
    assertFalse(staged.exists())
}
```

Add a test that `retry` rejects `SHARE_INTERRUPTED` even if a private file is present, and a test that it rejects every non-retryable staging/source error. Retain coverage that a retryable destination/write error requeues a real private file and schedules its work.

- [ ] **Step 2: Run the focused unit tests to verify they fail**

Run:

```bash
./gradlew testDebugUnitTest --tests app.maw629.homerelay.domain.UploadRepositoryTest
```

Expected: FAIL because the staging APIs and retry eligibility rules are absent.

- [ ] **Step 3: Implement repository-owned staging persistence**

`createStaging` must call the injected `newId`, derive the stored path by invoking `stagedPathForId(id)`, then insert a row with:

```kotlin
UploadItem(
    id = id,
    originalName = share.displayName,
    mimeType = share.mimeType,
    outputName = OutputNameFactory.create(share.displayName, nowMillis(), randomSuffix()),
    stagedPath = stagedPath,
    byteSize = 0L,
    createdAtMillis = nowMillis(),
    retryCount = 0,
    state = UploadState.STAGING,
    errorCode = UploadErrorCode.NONE
)
```

Do not catch a failed initial insert in this method; the coordinator must see that failure and must not open the source URI.

`completeStaging` calls guarded `dao.completeStaging` using the provider-resolved name and a fresh output name. If it returns `0`, return `false` and never schedule work. If it returns `1`, schedule the unique work and notify best-effort with `runCatching`; a post-transition scheduler or notification failure must not turn a durable queued row into an intake failure because `resumePending()` will schedule it at startup.

`failStaging` delegates to its guarded DAO operation. `recoverInterruptedStaging` reads all `STAGING` rows, transitions each with `SHARE_INTERRUPTED`, and deletes that row's stored private path only when the guarded transition succeeds. It returns the count transitioned.

Keep cancellation and worker behavior unchanged except that retry must first require `errorCode.isRetryable()` and `File(item.stagedPath).isFile`. Keep the existing operation mutex around retry/cancel scheduling races.

- [ ] **Step 4: Run focused unit tests to verify they pass**

Run:

```bash
./gradlew testDebugUnitTest --tests app.maw629.homerelay.domain.UploadRepositoryTest
```

Expected: PASS. The fake DAO tests must prove the same guards as SQL, not merely accept every transition.

- [ ] **Step 5: Commit queue staging operations**

```bash
git add app/src/main/java/app/maw629/homerelay/domain/UploadRepository.kt app/src/test/java/app/maw629/homerelay/domain/UploadRepositoryTest.kt app/src/test/java/app/maw629/homerelay/ui/HomeRelayViewModelTest.kt app/src/androidTest/java/app/maw629/homerelay/work/UploadWorkerTest.kt
git commit -m "feat: coordinate durable share staging"
```

### Task 5: Add Application-Owned Share Intake Coordination

**Files:**
- Create: `app/src/main/java/app/maw629/homerelay/share/ShareIntakeCoordinator.kt`
- Modify: `app/src/main/java/app/maw629/homerelay/HomeRelayApplication.kt`
- Create: `app/src/test/java/app/maw629/homerelay/share/ShareIntakeCoordinatorTest.kt`

**Interfaces:**
- Consumes: `ShareStager`, `DestinationRepository`, and the staging APIs from Task 4.
- Produces: `ShareIntakeOperations`, implemented by `ShareIntakeCoordinator`, exposing `suspend fun recoverInterruptedStaging()`, `suspend fun awaitRecovery(): Result<Unit>`, `fun start(intakeId: String, shares: List<IncomingShare>): StateFlow<ShareIntakeStatus>`, `fun observe(intakeId: String): StateFlow<ShareIntakeStatus>?`, and `fun release(intakeId: String)`.
- Produces: `ShareIntakeStatus.Preparing` and `ShareIntakeStatus.Terminal(queuedCount: Int, attentionCount: Int, queueUnavailableCount: Int, attentionErrors: Set<UploadErrorCode>, terminalAtMillis: Long)`.

- [ ] **Step 1: Write failing coordinator tests**

Use real `UploadRepository` with guarded fake DAO/scheduler, a fake `DestinationRepository`, a fake `ShareStager`, and a `StandardTestDispatcher`. Do not mock the coordinator itself. Cover these independent cases:

```kotlin
@Test
fun coordinatorCreatesStagingRowBeforeOpeningSource() = runTest {
    val stageStarted = CompletableDeferred<Unit>()
    stager.onStage = { target, _ ->
        assertEquals(UploadState.STAGING, dao.items.values.single().state)
        assertEquals(target.absolutePath, dao.items.values.single().stagedPath)
        stageStarted.complete(Unit)
        StageResult.Staged(target.apply { writeText("bytes") }, 5, "report.pdf")
    }

    coordinator.recoverInterruptedStaging()
    coordinator.start("intake-1", listOf(reportShare))
    advanceUntilIdle()

    assertTrue(stageStarted.isCompleted)
    assertEquals(UploadState.QUEUED, dao.items.values.single().state)
}
```

Add tests that prove:

- calling `start("intake-1", shares)` twice invokes the fake stager once per share and returns the same operation flow;
- source unreadable and local-storage-full outcomes become durable attention rows with no private file;
- a missing destination is checked only after successful private staging, preserves that complete file, and produces `DESTINATION_ACCESS_LOST`;
- failure to insert a staging row invokes no source staging and increments terminal `queueUnavailableCount`;
- failure of the final guarded queue transition deletes its completed private file, leaves the row in `STAGING`, and reports a queue-unavailable terminal outcome;
- recovery transitions preexisting staging rows to `SHARE_INTERRUPTED`, deletes their paths, and runs before the fake stager receives a new share;
- a scheduler exception after `QUEUED` does not change the terminal result from queued.

- [ ] **Step 2: Run the coordinator unit test to verify it fails**

Run:

```bash
./gradlew testDebugUnitTest --tests app.maw629.homerelay.share.ShareIntakeCoordinatorTest
```

Expected: FAIL because `ShareIntakeCoordinator` does not exist.

- [ ] **Step 3: Implement one coordinator with a startup recovery gate**

Define the status types in `ShareIntakeCoordinator.kt`. Pass the application scope, stager, destination repository, upload repository, UUID supplier, and clock into the coordinator constructor. Do not add a DI framework.

The coordinator owns a map from intake ID to a `MutableStateFlow<ShareIntakeStatus>`. `start` must return the existing flow for a matching active intake ID rather than launch a second job. It starts its work in the application scope, not the receiver lifecycle scope. `release(intakeId)` removes only a terminal operation. `ShareIntakeViewModel.onCleared()` calls it after its activity has truly finished; ViewModels survive configuration changes, so recreation never calls release.

Maintain a `CompletableDeferred<Result<Unit>>` recovery gate. `recoverInterruptedStaging()` completes the gate after `UploadRepository.recoverInterruptedStaging()` runs. Each new intake awaits the gate before creating any row or opening any source. If recovery failed, it emits a terminal queue-unavailable result without opening a source.

For each share, sequentially:

1. Call `uploadRepository.createStaging(share) { id -> stager.pendingFile(id).absolutePath }`.
2. Only after that insert succeeds, call `stager.stage(File(item.stagedPath), share)`.
3. Map source/storage outcomes using guarded `failStaging`, deleting a supplied final file for failures.
4. After successful staging, read `destinationTreeUri.firstOrNull()`. If missing or the read throws, mark the row `DESTINATION_ACCESS_LOST` and retain the complete private file.
5. If a destination exists, call `completeStaging`. If its guarded transition fails or throws, delete the completed private file, leave the durable row in `STAGING` for next-start `SHARE_INTERRUPTED` recovery, and increment queue-unavailable count.

Catch all coordinator-level failures and convert them to a terminal aggregate status. Never leave an operation permanently in `Preparing`. Set `terminalAtMillis` exactly once from the injected clock after every accepted URI has either queued, reached durable attention, or received the explicit queue-unavailable outcome.

Change `HomeRelayApplication` so `AppContainer` receives its existing application scope and creates the one coordinator. In the application's startup coroutine, call coordinator recovery before `uploadRepository.resumePending()`. Because `resumePending()` only schedules `QUEUED`, recovered `STAGING` rows can never be claimed by a worker.

- [ ] **Step 4: Run coordinator tests to verify they pass**

Run:

```bash
./gradlew testDebugUnitTest --tests app.maw629.homerelay.share.ShareIntakeCoordinatorTest
```

Expected: PASS, including no-duplicate, source-not-opened-before-insert, recovery-gate, and private-file-cleanup assertions.

- [ ] **Step 5: Commit application-owned intake coordination**

```bash
git add app/src/main/java/app/maw629/homerelay/share/ShareIntakeCoordinator.kt app/src/main/java/app/maw629/homerelay/HomeRelayApplication.kt app/src/test/java/app/maw629/homerelay/share/ShareIntakeCoordinatorTest.kt
git commit -m "feat: recover durable share intake"
```

### Task 6: Bind Receiver Recreation And Back Handling To One Intake

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/app/maw629/homerelay/share/ShareIntakeViewModel.kt`
- Modify: `app/src/main/java/app/maw629/homerelay/share/ShareReceiverActivity.kt`
- Create: `app/src/test/java/app/maw629/homerelay/share/ShareIntakeViewModelTest.kt`
- Modify: `app/src/androidTest/java/app/maw629/homerelay/share/ShareReceiverActivityTest.kt`

**Interfaces:**
- Consumes: coordinator operation state from Task 5 and `ShareIntentParser.parse(intent)`.
- Produces: `ShareIntakeViewModel.beginOrAttach(shares: List<IncomingShare>)`, `StateFlow<ShareIntakeStatus>`, and saved-state key `share_intake_id`.
- Produces: a receiver that blocks `OnBackPressedDispatcher` while state is `Preparing`, but permits normal Back after terminal state.

- [ ] **Step 1: Write the failing ViewModel tests**

Construct the ViewModel with a real `SavedStateHandle` and a fake coordinator. Test that it saves a UUID intake ID before it calls `coordinator.start`, then test that a second ViewModel created with the same saved handle calls `observe(existingId)` and never calls `start` again.

Add a process-restoration test: when the handle contains an intake ID but `observe` returns null, `beginOrAttach` must not reparse or restart the old intent. After the fake recovery gate completes, it emits a terminal interrupted-share status with the message path `Share the file again.`

- [ ] **Step 2: Run ViewModel tests to verify they fail**

Run:

```bash
./gradlew testDebugUnitTest --tests app.maw629.homerelay.share.ShareIntakeViewModelTest
```

Expected: FAIL because the ViewModel and its saved-state APIs do not exist.

- [ ] **Step 3: Add the saved-state ViewModel and activity wiring**

Add the catalog/dependency entry for `androidx.lifecycle:lifecycle-viewmodel-savedstate` using the repository's existing Lifecycle version. Implement `ShareIntakeViewModel` with `SavedStateHandle` and coordinator constructor dependencies.

Use `share_intake_id` as the only saved key. On a first launch, generate the intake ID, store it in the handle, then call `coordinator.start`. On a recreated activity, observe the live operation. On a process-restored ID with no live operation, wait for `coordinator.awaitRecovery()` and expose `ShareIntakeStatus.Terminal(queuedCount = 0, attentionCount = 1, queueUnavailableCount = 0, attentionErrors = setOf(UploadErrorCode.SHARE_INTERRUPTED), terminalAtMillis = clock())`; do not stage the restored intent again, because the prior temporary share grant cannot be trusted.

Create the ViewModel with `AbstractSavedStateViewModelFactory` in `ShareReceiverActivity`, passing the app container's `ShareIntakeOperations`. Do not construct an app container in the activity or ViewModel. Its `onCleared()` calls `operations.release(intakeId)` only after terminal state; a live operation is never released or cancelled.

Register an `OnBackPressedCallback` with the activity's dispatcher. Enable it while the ViewModel exposes `Preparing`; its callback consumes Back without finishing. Disable it at terminal state so normal Back can dismiss the already-complete overlay. This dispatcher callback also integrates with predictive Back on Android versions supported by the AndroidX Activity dependency.

Keep the actual staging job in the coordinator even when the activity is destroyed. Do not cancel it in `onDestroy` or ViewModel `onCleared`.

- [ ] **Step 4: Add failing receiver recreation and Back instrumentation tests**

Create a controllable test `content://` source that blocks its read until the test releases it. Launch the receiver with that source, wait for the provider to report that staging started, invoke `onBackPressedDispatcher.onBackPressed()`, and assert `ActivityScenario.state` is not `DESTROYED`. Release the source and assert the operation reaches its terminal status.

Launch a second controlled share, wait until its provider read has started, call `scenario.recreate()`, release the read, and query the real app database. Assert exactly one `upload_items` row exists for the shared URI and its final state is `QUEUED` or its expected durable attention state; do not assert an implementation call count.

- [ ] **Step 5: Run receiver tests to verify the new lifecycle cases fail**

Run on device/emulator:

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=app.maw629.homerelay.share.ShareReceiverActivityTest'
```

Expected: FAIL before activity wiring because Back closes the old receiver during preparation and recreation starts a duplicate intake.

- [ ] **Step 6: Run focused unit and device tests to verify they pass**

Run:

```bash
./gradlew testDebugUnitTest --tests app.maw629.homerelay.share.ShareIntakeViewModelTest
```

Then run the PowerShell device command from Step 5. Expected: both PASS. Without a device, run `./gradlew assembleDebugAndroidTest` and record the unexecuted lifecycle assertions.

- [ ] **Step 7: Commit lifecycle-safe receiver ownership**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/app/maw629/homerelay/share/ShareIntakeViewModel.kt app/src/main/java/app/maw629/homerelay/share/ShareReceiverActivity.kt app/src/test/java/app/maw629/homerelay/share/ShareIntakeViewModelTest.kt app/src/androidTest/java/app/maw629/homerelay/share/ShareReceiverActivityTest.kt
git commit -m "feat: retain share intake across receiver lifecycle"
```

### Task 7: Render The Transparent Auto-Hiding Share Overlay

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/values-v29/themes.xml`
- Modify: `app/src/main/java/app/maw629/homerelay/share/ShareReceiverActivity.kt`
- Modify: `app/src/androidTest/java/app/maw629/homerelay/share/ShareReceiverActivityTest.kt`
- Modify: `app/src/androidTest/java/app/maw629/homerelay/share/ShareQueueScreenVisibilityTest.kt`
- Create: `app/src/androidTest/java/app/maw629/homerelay/share/ShareOverlayHostActivity.kt`
- Modify: `app/src/androidTest/AndroidManifest.xml`

**Interfaces:**
- Consumes: terminal `ShareIntakeStatus` with an absolute `terminalAtMillis` from Task 5.
- Produces: `BuildConfig.SHARE_STATUS_DISPLAY_MILLIS: Long`, default `2_000L`.
- Produces: transparent receiver-only theme `Theme.HomeRelay.ShareReceiver` and centered `ShareQueueOverlay(status)` composable.

- [ ] **Step 1: Write failing overlay and duration instrumentation tests**

Extend the real-content receiver test to wait for a final status then assert `ActivityScenario.state == DESTROYED`. With the default build, assert the receiver remains active one second after the observed terminal status and is destroyed by 2.1 seconds after it. Use the terminal timestamp or condition observation to derive the remaining delay; do not assume activity creation time equals terminal time.

Add a separate safe-area test that obtains Compose layout bounds and asserts the card's center equals the center of the same safe-drawing rectangle used by production. Do not compare Compose root coordinates to a different decor-window coordinate system.

Replace the existing full-surface contrast assertion with a card contrast assertion. Name the regression it catches: an opaque or low-contrast card makes the confirmation unreadable over the source app.

Add an instrumentation host activity with a solid, distinctive background and launch the receiver above it. Use `UiAutomation.takeScreenshot()` or UiAutomator bounds to assert a pixel outside the status card remains the host background. Also assert receiver window status/navigation bar colors are transparent. This test catches a full opaque window or system-bar scrim, not merely a transparent Compose root.

- [ ] **Step 2: Run the focused test to verify it fails**

Run on a connected emulator/device:

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=app.maw629.homerelay.share.ShareReceiverActivityTest'
```

Expected: FAIL because the receiver has no terminal status timer, transparent theme, or generated duration constant. If no device is attached, first run `./gradlew assembleDebugAndroidTest`, record that only compilation was verified, and do not claim the behavioral failure ran.

- [ ] **Step 3: Implement build-duration configuration and transparent theme**

In `app/build.gradle.kts`, read exactly one environment variable:

```kotlin
val shareStatusDisplayMillis = providers.environmentVariable("SHARE_STATUS_DISPLAY_MILLIS")
    .orNull
    ?.toLongOrNull()
    ?.takeIf { it >= 0L }
    ?: 2_000L
```

Enable BuildConfig generation and place this field inside `defaultConfig`:

```kotlin
buildConfigField("long", "SHARE_STATUS_DISPLAY_MILLIS", "${shareStatusDisplayMillis}L")
```

Set `android:theme="@style/Theme.HomeRelay.ShareReceiver"` only on `ShareReceiverActivity`. The theme is translucent, has transparent window/status/navigation backgrounds, and disables dimming. In `values-v29/themes.xml`, override `android:enforceStatusBarContrast` and `android:enforceNavigationBarContrast` to `false` for that receiver theme so Android does not add an opaque contrast scrim. Set an explicit light/dark system-bar icon policy appropriate for the chosen transparent overlay; test the policy on device in both navigation modes.

`ShareReceiverActivity` must set Compose content only for a `Box` that fills the screen, applies `WindowInsets.safeDrawing`, and has `contentAlignment = Alignment.Center`. Place a padded Material 3 `Card` with status text inside it. Do not create a background `Surface`, start `MainActivity`, request notifications, or launch a folder picker.

For terminal status, use a Compose `LaunchedEffect` keyed to `terminalAtMillis`. Compute `remainingMillis = maxOf(0L, terminalAtMillis + BuildConfig.SHARE_STATUS_DISPLAY_MILLIS - System.currentTimeMillis())`, delay that amount, then call `finish()`. This keeps the original deadline through configuration recreation. `Preparing` has no finish timer.

- [ ] **Step 4: Run focused tests to verify they pass**

Run:

```bash
./gradlew assembleDebugAndroidTest
```

Then run on device/emulator:

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=app.maw629.homerelay.share.ShareReceiverActivityTest'
```

Expected: default-delay lifecycle test, card contrast, real content staging, safe-area center, transparency, auto-finish, recreation, and Back cases pass.

- [ ] **Step 5: Verify environment configuration through generated build behavior**

Run these independent fresh Gradle builds; configuration cache must invalidate because the provider API declares the environment input:

```bash
env -u SHARE_STATUS_DISPLAY_MILLIS ./gradlew generateDebugBuildConfig
SHARE_STATUS_DISPLAY_MILLIS=0 ./gradlew generateDebugBuildConfig
SHARE_STATUS_DISPLAY_MILLIS=3000 ./gradlew generateDebugBuildConfig
SHARE_STATUS_DISPLAY_MILLIS=-1 ./gradlew generateDebugBuildConfig
SHARE_STATUS_DISPLAY_MILLIS=not-a-number ./gradlew generateDebugBuildConfig
SHARE_STATUS_DISPLAY_MILLIS=999999999999999999999999999999 ./gradlew generateDebugBuildConfig
```

For each build, inspect the generated compiled `BuildConfig` value using the generated source or `javap`; assert literal values of `2000L`, `0L`, `3000L`, `2000L`, `2000L`, and `2000L`, respectively. This validates build output rather than an assertion about Gradle script text.

- [ ] **Step 6: Commit the receiver overlay**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/res/values/themes.xml app/src/main/res/values-v29/themes.xml app/src/main/java/app/maw629/homerelay/share/ShareReceiverActivity.kt app/src/androidTest/java/app/maw629/homerelay/share/ShareReceiverActivityTest.kt app/src/androidTest/java/app/maw629/homerelay/share/ShareQueueScreenVisibilityTest.kt app/src/androidTest/java/app/maw629/homerelay/share/ShareOverlayHostActivity.kt app/src/androidTest/AndroidManifest.xml
git commit -m "feat: show transient share status overlay"
```

### Task 8: Render Interrupted Shares In Recent Uploads Without Retry

**Files:**
- Modify: `app/src/main/java/app/maw629/homerelay/ui/HomeRelayViewModel.kt`
- Modify: `app/src/main/java/app/maw629/homerelay/ui/UploadsScreen.kt`
- Modify: `app/src/test/java/app/maw629/homerelay/ui/HomeRelayViewModelTest.kt`
- Modify: `app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt`

**Interfaces:**
- Consumes: `UploadErrorCode.SHARE_INTERRUPTED` and `UploadErrorCode.isRetryable()` from Task 4.
- Produces: `UploadRow.canRetry: Boolean` and message `Share the file again.` for interrupted shares.
- Produces: Recent Uploads rows that render Retry only when `canRetry` is true.

- [ ] **Step 1: Write failing presentation tests**

Add a ViewModel mapping test with a literal interrupted item and assert:

```kotlin
assertEquals("Share the file again.", row.errorMessage)
assertFalse(row.canRetry)
```

Add a Compose test that renders a `NEEDS_ATTENTION` interrupted row, asserts the share-again message exists, and asserts `Retry` does not exist. Keep the existing destination-access-lost test and assert it still has Retry plus `Choose folder again`.

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./gradlew testDebugUnitTest --tests app.maw629.homerelay.ui.HomeRelayViewModelTest
./gradlew assembleDebugAndroidTest
```

Expected: tests fail because the message mapping and `canRetry` field do not exist.

- [ ] **Step 3: Implement retry-aware presentation**

Add `canRetry` to `UploadRow`. Populate it in `HomeRelayViewModel` from the same centralized retry-eligibility rule used by `UploadRepository`; do not duplicate a divergent error-code list in the screen. Map `SHARE_INTERRUPTED` to exactly `Share the file again.`

Change `UploadsScreen` to render its Retry button only when `upload.state == UploadState.NEEDS_ATTENTION && upload.canRetry`. Retain the destination reselection button only for `DESTINATION_ACCESS_LOST`.

- [ ] **Step 4: Run tests to verify they pass**

Run the commands from Step 2. Expected: unit tests pass and instrumentation tests compile. Run the focused `UploadsScreenTest` on device/emulator when available.

- [ ] **Step 5: Commit the recovery presentation**

```bash
git add app/src/main/java/app/maw629/homerelay/ui/HomeRelayViewModel.kt app/src/main/java/app/maw629/homerelay/ui/UploadsScreen.kt app/src/test/java/app/maw629/homerelay/ui/HomeRelayViewModelTest.kt app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt
git commit -m "feat: show interrupted share recovery"
```

### Task 9: Update Maintained Documentation And Execute Full Validation

**Files:**
- Modify: `README.md`
- Modify: `docs/architecture.md`
- Modify: `docs/testing.md`

**Interfaces:**
- Consumes: the completed durable intake, recovery, overlay, and UI behavior.
- Produces: documentation matching the operational architecture and build/device validation requirements.

- [ ] **Step 1: Update user-facing README behavior and configuration**

Replace the full-screen share-status description with the actual sequence: each share is privately staged and durably queued or recorded for attention, a centered overlay reports the aggregate result, and the receiver returns to the source app after the configured duration. Document this exact build example:

```bash
SHARE_STATUS_DISPLAY_MILLIS=3000 ./gradlew assembleDebug
```

State that unset, invalid, negative, and overflowing values use 2,000 ms. Do not imply this overlay confirms Drive cloud or Windows sync.

- [ ] **Step 2: Update the maintained architecture contract**

Add `STAGING` and `SHARE_INTERRUPTED` to the persistent state and transition rules. State that the application-owned coordinator creates a staging row before source open; startup recovery converts incomplete rows before resuming queued workers; active recreation observes an existing operation; process-restored intents do not restage temporary URIs; and interrupted recovery records have no retry action.

Replace the prior full-window confirmation requirement with the transparent centered card, transparent system-bar, safe-drawing, terminal-deadline, and source-app-return contract.

- [ ] **Step 3: Update testing and manual acceptance guidance**

Add required instrumentation coverage for real `content://` staging-before-finish, receiver recreation, Back/predictive-Back prevention during preparation, auto-finish, transparent source-app visibility, and interrupted-recovery UI. Add manual UAT rows requiring a Zalo share to show only the centered overlay and return to Zalo after two seconds, tested with gesture and three-button navigation on API 26-34 and Android 15+.

- [ ] **Step 4: Run complete WSL validation**

Run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Expected: PASS. Review lint HTML/SARIF only if lint fails; do not suppress unrelated existing warnings merely to alter output.

- [ ] **Step 5: Run connected and manual validation when a device is available**

Run from Windows PowerShell:

```powershell
adb devices
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest lintDebug
```

Expected: the device is listed with state `device`, then all commands pass. Perform and record the added Zalo UAT cases with date, device model, Android API, navigation mode, app commit, source file type/size, and evidence path. If no device is available, report the exact unexecuted command and do not claim those behaviors are verified.

- [ ] **Step 6: Inspect the final changes and commit**

Run:

```bash
git status --short
```

Confirm generated schema version 2 is tracked and no APK, build directory, `.gradle`, SDK path, log, or secret is staged. Then commit only documentation files:

```bash
git add README.md docs/architecture.md docs/testing.md
git commit -m "docs: describe durable share overlay flow"
```

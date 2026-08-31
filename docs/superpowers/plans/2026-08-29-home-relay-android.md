# Home Relay Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a sideloaded Android app named Home Relay that receives files from the Android Share sheet and writes them to a user-selected Google Drive folder without asking for that folder again.

**Architecture:** Home Relay uses Android's Storage Access Framework to retain write access to one user-selected document tree. A share receiver stages incoming `content://` URIs immediately, persists metadata in Room, and schedules WorkManager workers to write staged files through the selected document provider. Compose provides the small setup, settings, and upload-history interface; no backend, Google Drive API, or OAuth exists in version 1.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, AndroidX Activity Result APIs, Storage Access Framework, DataStore, Room, WorkManager, NotificationCompat, JUnit, kotlinx-coroutines-test, AndroidX Test, Compose UI testing, WorkManager testing.

**Spec:** `docs/superpowers/specs/2026-08-29-home-relay-android-design.md`

## Global Constraints

- Package name is `app.maw629.homerelay`; the application and Share-sheet label are `Home Relay`.
- Support Android 8.0 and newer with `minSdk 26`; use the latest stable Android SDK installed in Android Studio for both `compileSdk` and `targetSdk`.
- Version 1 supports one local Android user, one selected destination folder, and file shares only; text shares, chat, iPhone support, Drive browsing, and printing are excluded.
- Use Android's Storage Access Framework `ACTION_OPEN_DOCUMENT_TREE`; do not add a Google Cloud project, Google Drive API dependency, OAuth flow, Firebase, server, analytics SDK, broad storage permission, or `MANAGE_EXTERNAL_STORAGE`.
- Copy every inbound shared URI into app-private no-backup storage before the share activity finishes.
- Treat success as a completed write to the selected Android document provider, not confirmation that Google Drive has replicated to the cloud or Windows.
- Use a dedicated destination folder such as `Home Relay Inbox`, not a Drive root or general personal folder.
- The workspace is a Git repository. Create commits only when the user explicitly requests them.

---

## Planned File Structure

```text
settings.gradle.kts                                  Gradle project settings
build.gradle.kts                                     Root build configuration
gradle/libs.versions.toml                            Central Android and library versions
app/build.gradle.kts                                 App SDK, Compose, Room, WorkManager, test dependencies
app/src/main/AndroidManifest.xml                     App, share receiver, notification, and foreground-service declarations
app/src/main/java/app/maw629/homerelay/HomeRelayApplication.kt
                                                     Application-owned dependency container and WorkerFactory
app/src/main/java/app/maw629/homerelay/data/UploadItem.kt   Room entity, state, and error code
app/src/main/java/app/maw629/homerelay/data/UploadDao.kt    Queue persistence queries
app/src/main/java/app/maw629/homerelay/data/HomeRelayDatabase.kt
                                                     Room database
app/src/main/java/app/maw629/homerelay/data/DestinationStore.kt
                                                     DataStore selected-tree setting
app/src/main/java/app/maw629/homerelay/domain/OutputNameFactory.kt
                                                     Collision-safe output naming
app/src/main/java/app/maw629/homerelay/domain/UploadRepository.kt
                                                     Queue creation, retry, cancellation, observation
app/src/main/java/app/maw629/homerelay/share/IncomingShare.kt
                                                     Parsed shared-file model
app/src/main/java/app/maw629/homerelay/share/ShareIntentParser.kt
                                                     ACTION_SEND and ACTION_SEND_MULTIPLE parsing
app/src/main/java/app/maw629/homerelay/share/ShareStager.kt
                                                     Temporary URI to private-file staging
app/src/main/java/app/maw629/homerelay/share/ShareReceiverActivity.kt
                                                     Exported Share-sheet entry activity
app/src/main/java/app/maw629/homerelay/destination/DestinationGateway.kt
                                                     Destination validation and file-write interface
app/src/main/java/app/maw629/homerelay/destination/AndroidDocumentTreeGateway.kt
                                                     Storage Access Framework implementation
app/src/main/java/app/maw629/homerelay/work/UploadWorker.kt Background provider write and retry mapping
app/src/main/java/app/maw629/homerelay/work/UploadScheduler.kt
                                                     WorkManager request construction and unique work names
app/src/main/java/app/maw629/homerelay/notifications/UploadNotifier.kt
                                                     Channels, status notifications, foreground progress
app/src/main/res/drawable/ic_stat_home_relay.xml      Monochrome notification icon
app/src/main/java/app/maw629/homerelay/ui/HomeRelayApp.kt   Compose navigation and shared theme
app/src/main/java/app/maw629/homerelay/ui/SettingsScreen.kt Folder selection and notification settings
app/src/main/java/app/maw629/homerelay/ui/UploadsScreen.kt  Recent uploads, retry, cancellation
app/src/main/java/app/maw629/homerelay/ui/HomeRelayViewModel.kt
                                                     Screen state and UI actions
app/src/test/java/app/maw629/homerelay/                     Local test files listed in each task
app/src/androidTest/java/app/maw629/homerelay/              Android test files listed in each task
README.md                                            Local setup, sideloading, manual test checklist
```

### Task 1: Bootstrap The Android Project

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/app/maw629/homerelay/HomeRelayApplication.kt`
- Create: `app/src/main/java/app/maw629/homerelay/ui/HomeRelayApp.kt`
- Create: `app/src/main/java/app/maw629/homerelay/MainActivity.kt`
- Create: `app/src/androidTest/java/app/maw629/homerelay/HomeRelayLaunchTest.kt`

**Interfaces:**
- Produces: Application ID `app.maw629.homerelay`, `HomeRelayApplication`, and a Compose app root that later tasks extend.

- [ ] **Step 1: Create an Empty Activity project in Android Studio**

Create a Kotlin/Compose project named `Home Relay` with package `app.maw629.homerelay`, `minSdk 26`, and the latest stable installed SDK as compile and target SDK. Keep Android Studio's generated Gradle wrapper; do not install system Gradle.

- [ ] **Step 2: Add the required dependencies and manifest declarations**

Add Compose Material 3, Activity Result APIs, DataStore Preferences, Room with KSP, WorkManager KTX, NotificationCompat, JUnit, kotlinx-coroutines-test, AndroidX Test, Compose UI testing, Room testing, and WorkManager testing. Declare only these permissions and components:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<application android:name=".HomeRelayApplication" android:label="Home Relay">
    <activity android:name=".MainActivity" android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

- [ ] **Step 3: Write the failing launch test**

```kotlin
@RunWith(AndroidJUnit4::class)
class HomeRelayLaunchTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun launchShowsHomeRelayTitle() {
        composeRule.onNodeWithText("Home Relay").assertExists()
    }
}
```

- [ ] **Step 4: Run the instrumentation test to verify it fails**

Run: `./gradlew connectedDebugAndroidTest --tests app.maw629.homerelay.HomeRelayLaunchTest`

Expected: FAIL because no `Home Relay` semantics node exists.

- [ ] **Step 5: Implement the minimal application shell**

```kotlin
class HomeRelayApplication : Application()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HomeRelayApp() }
    }
}

@Composable
fun HomeRelayApp() {
    MaterialTheme { Text("Home Relay") }
}
```

- [ ] **Step 6: Run the launch test and assemble the debug APK**

Run: `./gradlew connectedDebugAndroidTest --tests app.maw629.homerelay.HomeRelayLaunchTest assembleDebug`

Expected: PASS and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 7: Review the generated dependency lock-in**

Confirm `compileSdk` and `targetSdk` match the latest stable platform installed in Android Studio, `minSdk` is 26, and no Google Drive or Firebase dependency was added.

### Task 2: Add Queue Models And Local Persistence

**Files:**
- Create: `app/src/main/java/app/maw629/homerelay/data/UploadItem.kt`
- Create: `app/src/main/java/app/maw629/homerelay/data/UploadDao.kt`
- Create: `app/src/main/java/app/maw629/homerelay/data/HomeRelayDatabase.kt`
- Create: `app/src/main/java/app/maw629/homerelay/domain/OutputNameFactory.kt`
- Test: `app/src/test/java/app/maw629/homerelay/domain/OutputNameFactoryTest.kt`
- Test: `app/src/androidTest/java/app/maw629/homerelay/data/UploadDaoTest.kt`

**Interfaces:**
- Produces: `UploadState`, `UploadErrorCode`, `UploadItem`, `UploadDao`, `HomeRelayDatabase`, and `OutputNameFactory.create(originalName, nowMillis, randomSuffix)`.
- Consumes: the Gradle Room and testing dependencies from Task 1.

- [ ] **Step 1: Write failing output-name tests**

```kotlin
class OutputNameFactoryTest {
    @Test fun createPreservesExtensionAndAddsTimestampAndSuffix() {
        assertEquals(
            "20260829-142501-a1b2c3-report.pdf",
            OutputNameFactory.create("report.pdf", 1_788_013_501_000, "a1b2c3")
        )
    }

    @Test fun createRemovesPathSeparatorsFromOriginalName() {
        assertEquals(
            "20260829-142501-a1b2c3-quarterly_report.pdf",
            OutputNameFactory.create("quarterly/report.pdf", 1_788_013_501_000, "a1b2c3")
        )
    }
}
```

- [ ] **Step 2: Run the local test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests app.maw629.homerelay.domain.OutputNameFactoryTest`

Expected: FAIL because `OutputNameFactory` does not exist.

- [ ] **Step 3: Implement the queue types and output-name factory**

```kotlin
enum class UploadState { QUEUED, UPLOADING, COMPLETED, NEEDS_ATTENTION, CANCELLED }

enum class UploadErrorCode {
    NONE, SOURCE_UNREADABLE, STAGING_STORAGE_FULL, DESTINATION_ACCESS_LOST,
    DESTINATION_QUOTA, DESTINATION_POLICY, WRITE_OUTCOME_UNKNOWN
}

@Entity(tableName = "upload_items")
data class UploadItem(
    @PrimaryKey val id: String,
    val originalName: String,
    val mimeType: String,
    val outputName: String,
    val stagedPath: String,
    val byteSize: Long,
    val createdAtMillis: Long,
    val retryCount: Int,
    val state: UploadState,
    val errorCode: UploadErrorCode
)

object OutputNameFactory {
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneOffset.UTC)

    fun create(originalName: String, nowMillis: Long, randomSuffix: String): String {
        val safeName = originalName
            .ifBlank { "shared-file" }
            .replace('/', '_')
            .replace('\\', '_')
            .replace('\u0000', '_')
            .trim()
            .ifBlank { "shared-file" }
        return "${formatter.format(Instant.ofEpochMilli(nowMillis))}-$randomSuffix-$safeName"
    }
}
```

- [ ] **Step 4: Write the failing Room DAO test**

```kotlin
@Test fun insertThenObserveReturnsQueuedItem() = runTest {
    dao.insert(sampleUpload(state = UploadState.QUEUED))
    assertEquals(UploadState.QUEUED, dao.observeAll().first().single().state)
}
```

- [ ] **Step 5: Implement DAO and database methods**

```kotlin
@Dao
interface UploadDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: UploadItem)

    @Query("SELECT * FROM upload_items ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<UploadItem>>

    @Query("SELECT * FROM upload_items WHERE id = :id")
    suspend fun get(id: String): UploadItem?

    @Update suspend fun update(item: UploadItem)

    @Query("DELETE FROM upload_items WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [UploadItem::class], version = 1, exportSchema = true)
abstract class HomeRelayDatabase : RoomDatabase() {
    abstract fun uploadDao(): UploadDao
}
```

Add Room type converters for `UploadState` and `UploadErrorCode` using their enum names.

- [ ] **Step 6: Run local and instrumented persistence tests**

Run: `./gradlew testDebugUnitTest --tests app.maw629.homerelay.domain.OutputNameFactoryTest connectedDebugAndroidTest --tests app.maw629.homerelay.data.UploadDaoTest`

Expected: PASS.

### Task 3: Implement Destination Selection With Storage Access Framework

**Files:**
- Create: `app/src/main/java/app/maw629/homerelay/data/DestinationStore.kt`
- Create: `app/src/main/java/app/maw629/homerelay/destination/DestinationGateway.kt`
- Create: `app/src/main/java/app/maw629/homerelay/destination/AndroidDocumentTreeGateway.kt`
- Modify: `app/src/main/java/app/maw629/homerelay/HomeRelayApplication.kt`
- Test: `app/src/test/java/app/maw629/homerelay/data/DestinationStoreTest.kt`
- Test: `app/src/androidTest/java/app/maw629/homerelay/destination/AndroidDocumentTreeGatewayTest.kt`

**Interfaces:**
- Produces: `DestinationStore.destinationTreeUri`, `DestinationStore.setDestination(uri)`, `DestinationStore.clearDestination()`, `DestinationGateway.validate(treeUri)`, and `DestinationGateway.write(treeUri, source, mimeType, outputName)`.
- Consumes: Room-independent app context from Task 1.

- [ ] **Step 1: Write the failing destination-store test**

```kotlin
@Test fun setDestinationEmitsStoredTreeUri() = runTest {
    val uri = "content://example/tree/drive%3Ahome-relay"
    store.setDestination(uri)
    assertEquals(uri, store.destinationTreeUri.first())
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew testDebugUnitTest --tests app.maw629.homerelay.data.DestinationStoreTest`

Expected: FAIL because `DestinationStore` does not exist.

- [ ] **Step 3: Implement the destination abstractions**

```kotlin
sealed interface DestinationResult {
    data object Success : DestinationResult
    data object TransientFailure : DestinationResult
    data object AccessLost : DestinationResult
    data class PermanentFailure(val errorCode: UploadErrorCode) : DestinationResult
    data object UnknownWriteOutcome : DestinationResult
}

interface DestinationGateway {
    suspend fun validate(treeUri: Uri): DestinationResult
    suspend fun write(
        treeUri: Uri,
        source: File,
        mimeType: String,
        outputName: String
    ): DestinationResult
}
```

Implement `DestinationStore` with Preferences DataStore under the key `destination_tree_uri`. Implement `AndroidDocumentTreeGateway.validate` by creating a uniquely named zero-byte probe document through `DocumentsContract.createDocument`, closing it, deleting it, and returning `Success` only if all operations succeed. Implement `write` with `DocumentsContract.createDocument`, `ContentResolver.openOutputStream`, and buffered file copy. Map `SecurityException` and `FileNotFoundException` to `AccessLost`; map an `IOException` before document creation to `TransientFailure`; map an exception after document creation to `UnknownWriteOutcome`; and map a provider-reported quota or policy rejection to `PermanentFailure(UploadErrorCode.DESTINATION_QUOTA)` or `PermanentFailure(UploadErrorCode.DESTINATION_POLICY)` respectively.

- [ ] **Step 4: Add an Android instrumentation probe test**

```kotlin
@Test fun invalidTreeUriReturnsAccessLost() = runTest {
    assertEquals(
        DestinationResult.AccessLost,
        gateway.validate(Uri.parse("content://missing.provider/tree/nope"))
    )
}
```

- [ ] **Step 5: Run the destination tests**

Run: `./gradlew testDebugUnitTest --tests app.maw629.homerelay.data.DestinationStoreTest connectedDebugAndroidTest --tests app.maw629.homerelay.destination.AndroidDocumentTreeGatewayTest`

Expected: PASS.

- [ ] **Step 6: Wire the destination dependencies into the application container**

```kotlin
class HomeRelayApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

`AppContainer` owns the database, `DestinationStore`, and `AndroidDocumentTreeGateway`. Add the WorkManager configuration and worker factory in Task 5. Do not use a static global singleton or a dependency-injection framework.

### Task 4: Parse And Stage Shared Files Safely

**Files:**
- Create: `app/src/main/java/app/maw629/homerelay/share/IncomingShare.kt`
- Create: `app/src/main/java/app/maw629/homerelay/share/ShareIntentParser.kt`
- Create: `app/src/main/java/app/maw629/homerelay/share/ShareStager.kt`
- Test: `app/src/test/java/app/maw629/homerelay/share/ShareIntentParserTest.kt`
- Test: `app/src/androidTest/java/app/maw629/homerelay/share/AndroidShareStagerTest.kt`

**Interfaces:**
- Produces: `IncomingShare`, `ShareIntentParser.parse(intent)`, `ShareStager.stage(share)`, and `StageResult`.
- Consumes: app-private files directory and Room models from Task 2.

- [ ] **Step 1: Write failing parser tests**

```kotlin
@Test fun parseSingleStreamReturnsOneFile() {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, Uri.parse("content://sender/report"))
    }
    assertEquals(1, ShareIntentParser.parse(intent).size)
}

@Test fun parsePlainTextReturnsNoFiles() {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "hello")
    }
    assertTrue(ShareIntentParser.parse(intent).isEmpty())
}
```

- [ ] **Step 2: Run parser tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests app.maw629.homerelay.share.ShareIntentParserTest`

Expected: FAIL because `ShareIntentParser` does not exist.

- [ ] **Step 3: Implement parsing and staging contracts**

```kotlin
data class IncomingShare(
    val uri: Uri,
    val displayName: String,
    val mimeType: String
)

sealed interface StageResult {
    data class Staged(val file: File, val byteSize: Long) : StageResult
    data object SourceUnreadable : StageResult
    data object StorageFull : StageResult
}

interface ShareStager {
    suspend fun stage(id: String, share: IncomingShare): StageResult
}
```

`ShareIntentParser` must collect file URIs from both `Intent.EXTRA_STREAM` and `ClipData` for `ACTION_SEND` and `ACTION_SEND_MULTIPLE`; it must deduplicate URI strings. `AndroidShareStager` must resolve the display name using `OpenableColumns.DISPLAY_NAME` with a `shared-file` fallback, create `noBackupFilesDir/pending`, and copy content with buffered streams. It must close every stream with `use` and return `StorageFull` when an I/O failure is caused by unavailable local storage.

- [ ] **Step 4: Write the failing Android staging test**

```kotlin
@Test fun stageCopiesProviderBytesIntoNoBackupStorage() = runTest {
    val result = stager.stage("item-1", sampleContentProviderShare("hello"))
    assertEquals("hello", (result as StageResult.Staged).file.readText())
}
```

- [ ] **Step 5: Run parser and staging tests**

Run: `./gradlew testDebugUnitTest --tests app.maw629.homerelay.share.ShareIntentParserTest connectedDebugAndroidTest --tests app.maw629.homerelay.share.AndroidShareStagerTest`

Expected: PASS.

### Task 5: Add Repository, Scheduler, And Upload Worker

**Files:**
- Create: `app/src/main/java/app/maw629/homerelay/domain/UploadRepository.kt`
- Create: `app/src/main/java/app/maw629/homerelay/work/UploadScheduler.kt`
- Create: `app/src/main/java/app/maw629/homerelay/work/UploadWorker.kt`
- Create: `app/src/main/java/app/maw629/homerelay/notifications/UploadNotifier.kt`
- Create: `app/src/main/res/drawable/ic_stat_home_relay.xml`
- Modify: `app/src/main/java/app/maw629/homerelay/HomeRelayApplication.kt`
- Test: `app/src/test/java/app/maw629/homerelay/domain/UploadRepositoryTest.kt`
- Test: `app/src/androidTest/java/app/maw629/homerelay/work/UploadWorkerTest.kt`

**Interfaces:**
- Produces: `UploadRepository.enqueue(staged, share)`, `retry(id)`, `cancel(id)`, `observeUploads()`, `UploadScheduler.schedule(id)`, and `UploadWorker` input key `upload_item_id`.
- Consumes: `UploadDao`, `OutputNameFactory`, `DestinationStore`, `DestinationGateway`, and `ShareStager` from Tasks 2-4.

- [ ] **Step 1: Write failing repository behavior tests**

```kotlin
@Test fun retryFromNeedsAttentionGeneratesANewOutputNameAndQueuesWork() = runTest {
    repository.retry("item-1")
    val item = dao.get("item-1")!!
    assertEquals(UploadState.QUEUED, item.state)
    assertNotEquals("old-name.pdf", item.outputName)
    assertEquals(listOf("item-1"), scheduler.scheduledIds)
}
```

- [ ] **Step 2: Run repository tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests app.maw629.homerelay.domain.UploadRepositoryTest`

Expected: FAIL because `UploadRepository` does not exist.

- [ ] **Step 3: Implement repository and scheduler behavior**

```kotlin
interface UploadScheduler {
    fun schedule(uploadItemId: String)
    fun cancel(uploadItemId: String)
}

class UploadRepository(
    private val dao: UploadDao,
    private val scheduler: UploadScheduler,
    private val newId: () -> String,
    private val nowMillis: () -> Long,
    private val randomSuffix: () -> String
) {
    suspend fun enqueue(staged: StageResult.Staged, share: IncomingShare): String {
        val id = newId()
        val item = UploadItem(
            id = id,
            originalName = share.displayName,
            mimeType = share.mimeType,
            outputName = OutputNameFactory.create(share.displayName, nowMillis(), randomSuffix()),
            stagedPath = staged.file.absolutePath,
            byteSize = staged.byteSize,
            createdAtMillis = nowMillis(),
            retryCount = 0,
            state = UploadState.QUEUED,
            errorCode = UploadErrorCode.NONE
        )
        dao.insert(item)
        scheduler.schedule(id)
        return id
    }

    suspend fun retry(id: String) {
        val item = checkNotNull(dao.get(id))
        val next = item.copy(
            state = UploadState.QUEUED,
            errorCode = UploadErrorCode.NONE,
            retryCount = item.retryCount + 1,
            outputName = OutputNameFactory.create(item.originalName, nowMillis(), randomSuffix())
        )
        dao.update(next)
        scheduler.schedule(id)
    }

    suspend fun cancel(id: String) {
        val item = checkNotNull(dao.get(id))
        scheduler.cancel(id)
        File(item.stagedPath).delete()
        dao.update(item.copy(state = UploadState.CANCELLED))
    }

    fun observeUploads(): Flow<List<UploadItem>> = dao.observeAll()
}
```

`WorkManagerUploadScheduler.schedule` creates one `OneTimeWorkRequest` per item with input data key `upload_item_id`, `NetworkType.CONNECTED`, exponential backoff, and the unique work name `upload:<itemId>`. Use `ExistingWorkPolicy.KEEP`.

- [ ] **Step 4: Create the upload-notification service**

```kotlin
class UploadNotifier(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "home_relay_uploads"
        private const val FOREGROUND_NOTIFICATION_ID = 1001
    }

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Home Relay uploads",
            NotificationManager.IMPORTANCE_LOW
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun foregroundInfo(item: UploadItem, copied: Long, total: Long): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_home_relay)
            .setContentTitle("Uploading ${item.originalName}")
            .setProgress(total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), copied.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), false)
            .setOngoing(true)
            .build()
        return ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification)
    }

    fun completed(item: UploadItem) = post(item.id.hashCode(), "Saved to Home Relay", item.originalName)

    fun needsAttention(item: UploadItem, error: UploadErrorCode) = post(
        item.id.hashCode(),
        "Home Relay needs attention",
        "${item.originalName}: ${error.name.lowercase().replace('_', ' ')}"
    )

    private fun post(id: Int, title: String, text: String) {
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(
                id,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_home_relay)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setAutoCancel(true)
                    .build()
            )
        }
    }
}
```

Create a monochrome `ic_stat_home_relay` status-bar drawable. Call `container.uploadNotifier.createChannel()` from `HomeRelayApplication.onCreate()` before background work can run.

- [ ] **Step 5: Add the WorkerFactory and WorkManager configuration**

```kotlin
class HomeRelayWorkerFactory(
    private val container: AppContainer
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = when (workerClassName) {
        UploadWorker::class.java.name -> UploadWorker(
            appContext = appContext,
            params = workerParameters,
            dao = container.database.uploadDao(),
            destinationStore = container.destinationStore,
            gateway = container.destinationGateway,
            notifier = container.uploadNotifier
        )
        else -> null
    }
}

class HomeRelayApplication : Application(), Configuration.Provider {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(HomeRelayWorkerFactory(container))
            .build()
}
```

Update `AppContainer` to construct `UploadNotifier` before WorkManager requests execute. Do not construct a second Room database or destination store inside a worker.

- [ ] **Step 6: Write the failing worker retry test**

```kotlin
@Test fun transientDestinationFailureReturnsRetryAndKeepsQueuedItem() = runTest {
    gateway.nextWriteResult = DestinationResult.TransientFailure
    val result = runWorker("item-1")
    assertTrue(result is ListenableWorker.Result.Retry)
    assertEquals(UploadState.QUEUED, dao.get("item-1")!!.state)
}
```

- [ ] **Step 7: Implement worker result mapping**

```kotlin
override suspend fun doWork(): Result {
    val itemId = inputData.getString("upload_item_id") ?: return Result.failure()
    val item = dao.get(itemId) ?: return Result.failure()
    val destinationUri = destinationStore.destinationTreeUri.firstOrNull()?.let(Uri::parse)
        ?: return needsAttention(item, UploadErrorCode.DESTINATION_ACCESS_LOST)
    if (!File(item.stagedPath).isFile) {
        return needsAttention(item, UploadErrorCode.SOURCE_UNREADABLE)
    }
    dao.update(item.copy(state = UploadState.UPLOADING, errorCode = UploadErrorCode.NONE))
    return when (val result = gateway.write(destinationUri, File(item.stagedPath), item.mimeType, item.outputName)) {
        DestinationResult.Success -> complete(item)
        DestinationResult.TransientFailure -> {
            dao.update(item.copy(state = UploadState.QUEUED, retryCount = runAttemptCount + 1))
            Result.retry()
        }
        DestinationResult.AccessLost -> needsAttention(item, UploadErrorCode.DESTINATION_ACCESS_LOST)
        is DestinationResult.PermanentFailure -> needsAttention(item, result.errorCode)
        DestinationResult.UnknownWriteOutcome -> needsAttention(item, UploadErrorCode.WRITE_OUTCOME_UNKNOWN)
    }
}

private suspend fun complete(item: UploadItem): Result {
    dao.update(item.copy(state = UploadState.COMPLETED, errorCode = UploadErrorCode.NONE))
    File(item.stagedPath).delete()
    notifier.completed(item)
    return Result.success()
}

private suspend fun needsAttention(item: UploadItem, error: UploadErrorCode): Result {
    dao.update(item.copy(state = UploadState.NEEDS_ATTENTION, errorCode = error))
    notifier.needsAttention(item, error)
    return Result.success()
}
```

`complete` updates state to `COMPLETED`, deletes the staged file, posts a completion notification, and returns `Result.success()`. `needsAttention` updates state, retains the staged file, posts an attention notification, and returns `Result.success()` so WorkManager does not retry a permanent error. If a queued file is missing, update its state to `NEEDS_ATTENTION` with `SOURCE_UNREADABLE`.

- [ ] **Step 8: Add 10 MiB foreground-progress behavior**

When `item.byteSize >= 10 * 1024 * 1024`, call `setForeground(notifier.foregroundInfo(item, bytesCopied, totalBytes))` before copying and periodically while copying. The foreground notification must use the same channel created in Task 7.

- [ ] **Step 9: Run repository and worker tests**

Run: `./gradlew testDebugUnitTest --tests app.maw629.homerelay.domain.UploadRepositoryTest connectedDebugAndroidTest --tests app.maw629.homerelay.work.UploadWorkerTest`

Expected: PASS.

### Task 6: Register The Share Target And Queue Incoming Files

**Files:**
- Create: `app/src/main/java/app/maw629/homerelay/share/ShareReceiverActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/app/maw629/homerelay/HomeRelayApplication.kt`
- Test: `app/src/androidTest/java/app/maw629/homerelay/share/ShareReceiverActivityTest.kt`

**Interfaces:**
- Consumes: `ShareIntentParser`, `ShareStager`, `UploadRepository`, `DestinationStore`, and `UploadScheduler` from Tasks 3-5.
- Produces: exported `ShareReceiverActivity` with label `Home Relay` in Android's file Share sheet.

- [ ] **Step 1: Write the failing share-activity test**

```kotlin
@Test fun singleFileShareQueuesItemAndShowsConfirmation() {
    launchActivity<ShareReceiverActivity>(sampleSendIntent())
    composeRule.onNodeWithText("Queued 1 file for Home Relay").assertExists()
}
```

- [ ] **Step 2: Run the share-activity test to verify it fails**

Run: `./gradlew connectedDebugAndroidTest --tests app.maw629.homerelay.share.ShareReceiverActivityTest`

Expected: FAIL because `ShareReceiverActivity` does not exist.

- [ ] **Step 3: Add the narrow share intent filter**

```xml
<activity
    android:name=".share.ShareReceiverActivity"
    android:exported="true"
    android:label="Home Relay">
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <action android:name="android.intent.action.SEND_MULTIPLE" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="*/*" />
    </intent-filter>
</activity>
```

- [ ] **Step 4: Implement receiver flow**

```kotlin
class ShareReceiverActivity : ComponentActivity() {
    private var queueStatus by mutableStateOf<ShareQueueStatus>(ShareQueueStatus.Preparing)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shares = ShareIntentParser.parse(intent)
        setContent { ShareQueueScreen(queueStatus) }
        lifecycleScope.launch { queueStatus = queueShares(shares) }
    }
}
```

```kotlin
sealed interface ShareQueueStatus {
    data object Preparing : ShareQueueStatus
    data class Queued(val count: Int) : ShareQueueStatus
    data object DestinationMissing : ShareQueueStatus
    data object SourceUnreadable : ShareQueueStatus
    data object StorageFull : ShareQueueStatus
}
```

Implement `queueShares(shares: List<IncomingShare>): ShareQueueStatus` on `ShareReceiverActivity`. It first checks `destinationStore.destinationTreeUri.firstOrNull()`. If no destination exists, return `DestinationMissing`. Otherwise stage every share sequentially, call `repository.enqueue(staged, share)` for every `StageResult.Staged`, and count successful queue insertions. Return `SourceUnreadable` or `StorageFull` immediately for those stage outcomes; do not create a queue item for a failed stage. Return `Queued(count)` after all files are scheduled. Do not accept `EXTRA_TEXT` as a file.

- [ ] **Step 5: Run the share target test and inspect the manifest**

Run: `./gradlew connectedDebugAndroidTest --tests app.maw629.homerelay.share.ShareReceiverActivityTest`

Expected: PASS. On a device, Android's chooser lists `Home Relay` for a PDF shared from a file manager.

### Task 7: Build Settings, Upload History, And Notification Permission UI

**Files:**
- Create: `app/src/main/java/app/maw629/homerelay/ui/HomeRelayViewModel.kt`
- Create: `app/src/main/java/app/maw629/homerelay/ui/SettingsScreen.kt`
- Create: `app/src/main/java/app/maw629/homerelay/ui/UploadsScreen.kt`
- Modify: `app/src/main/java/app/maw629/homerelay/ui/HomeRelayApp.kt`
- Modify: `app/src/main/java/app/maw629/homerelay/MainActivity.kt`
- Test: `app/src/androidTest/java/app/maw629/homerelay/ui/SettingsScreenTest.kt`
- Test: `app/src/androidTest/java/app/maw629/homerelay/ui/UploadsScreenTest.kt`

**Interfaces:**
- Produces: `HomeRelayViewModel`, `SettingsScreen`, and `UploadsScreen`.
- Consumes: destination store, gateway, repository, and queue models from Tasks 2-5.

- [ ] **Step 1: Write failing settings-screen tests**

```kotlin
@Test fun noDestinationShowsChooseDriveFolderAction() {
    composeRule.setContent { SettingsScreen(state = SettingsState(destinationName = null), onChooseFolder = {}) }
    composeRule.onNodeWithText("Choose Drive folder").assertExists()
}

@Test fun selectedDestinationShowsChangeFolderAction() {
    composeRule.setContent { SettingsScreen(state = SettingsState(destinationName = "Home Relay Inbox"), onChooseFolder = {}) }
    composeRule.onNodeWithText("Change folder").assertExists()
}
```

- [ ] **Step 2: Run UI tests to verify they fail**

Run: `./gradlew connectedDebugAndroidTest --tests app.maw629.homerelay.ui.SettingsScreenTest --tests app.maw629.homerelay.ui.UploadsScreenTest`

Expected: FAIL because the screens and state classes do not exist.

- [ ] **Step 3: Implement settings folder selection**

```kotlin
data class SettingsState(
    val destinationName: String?,
    val error: String? = null
)

@Composable
fun SettingsScreen(
    state: SettingsState,
    onChooseFolder: () -> Unit
) {
    Button(onClick = onChooseFolder) {
        Text(if (state.destinationName == null) "Choose Drive folder" else "Change folder")
    }
}

class HomeRelayViewModel(
    private val destinationStore: DestinationStore,
    private val gateway: DestinationGateway
) : ViewModel() {
    private val _settingsState = MutableStateFlow(SettingsState(destinationName = null))
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    fun selectDestination(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val result = try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, flags)
                gateway.validate(uri)
            } catch (_: SecurityException) {
                DestinationResult.AccessLost
            }
            if (result == DestinationResult.Success) {
                destinationStore.setDestination(uri.toString())
                _settingsState.value = SettingsState(destinationName = "Drive folder selected")
            } else {
                _settingsState.value = SettingsState(
                    destinationName = _settingsState.value.destinationName,
                    error = "Home Relay cannot write to that folder. Choose another writable Drive folder."
                )
            }
        }
    }
}
```

In `HomeRelayApp`, create `treePicker` with `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree())`; call `viewModel.selectDestination(context.contentResolver, uri)` only when the picker returns a non-null URI. Expose non-success validation results as Settings-screen error text and do not replace a working prior destination on validation failure.

- [ ] **Step 4: Implement recent-upload actions**

```kotlin
data class UploadRow(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val state: UploadState,
    val errorCode: UploadErrorCode
)
```

Render rows from `UploadRepository.observeUploads()`. A `NEEDS_ATTENTION` row has `Retry`; if its error is `DESTINATION_ACCESS_LOST`, it also has `Choose folder again`. A `QUEUED` row has `Cancel`. `COMPLETED` rows are read-only.

- [ ] **Step 5: Request notification permission from Settings**

Use `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` only when `Build.VERSION.SDK_INT >= 33` and `POST_NOTIFICATIONS` is not granted. The Settings screen shows `Enable upload notifications` while permission is denied and `Upload notifications enabled` after it is granted. Do not request this permission from `ShareReceiverActivity`, and do not disable queue actions if the user denies it.

- [ ] **Step 6: Run the UI test suite**

Run: `./gradlew connectedDebugAndroidTest --tests app.maw629.homerelay.ui.SettingsScreenTest --tests app.maw629.homerelay.ui.UploadsScreenTest`

Expected: PASS.

### Task 8: Verify End-To-End Drive Provider Behavior And Produce A Release APK

**Files:**
- Create: `README.md`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: the complete Home Relay implementation from Tasks 1-7.
- Produces: documented local setup, manual validation evidence, and a signed release APK suitable for sideloading.

- [ ] **Step 1: Write the manual acceptance checklist in README**

Include these exact manual checks:

```markdown
- [ ] Select `Google Drive > Home Relay Inbox`; force-stop Home Relay, reopen it, and confirm the destination remains selected.
- [ ] Reboot the phone and confirm the destination remains selected.
- [ ] Share a PDF, image, DOCX, ZIP, and multiple files from Zalo to Home Relay.
- [ ] Share a file in airplane mode, reconnect, and observe automatic retry.
- [ ] Share two same-named files and confirm unique Drive names.
- [ ] Share a file at least 10 MiB and observe a foreground progress notification.
- [ ] Remove the Drive account or delete the selected folder; confirm an item reaches `NEEDS_ATTENTION` with folder reselection.
- [ ] Deny notifications and confirm Recent Uploads still exposes queued and failed items.
- [ ] Confirm a completed item appears in Drive web and in Windows File Explorer via Drive for desktop.
```

- [ ] **Step 2: Run all automated tests before device testing**

Run: `./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug`

Expected: all tests PASS and lint has no errors.

- [ ] **Step 3: Test on a Google Play emulator**

Create a current stable Google Play emulator in Android Studio, sign into a dedicated non-production Google account, install Google Drive, select a dedicated `Home-Relay-SAF-Test` folder, and execute the README checklist except Zalo-specific sharing if Zalo is unavailable in the emulator.

- [ ] **Step 4: Test on the physical Android phone with Zalo**

Install the debug APK with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Use Zalo's actual `Share file to Other app` flow for every applicable README check. Capture `adb logcat` output for any document-provider, WorkManager, or notification failure.

- [ ] **Step 5: Configure release signing outside source control**

Use Android Studio's Generate Signed Bundle / APK flow to create a new local keystore outside the repository. Set a release signing configuration through `keystore.properties`, add `keystore.properties` and `*.jks` to `.gitignore` if Git is initialized later, and never place a keystore password or key alias password in source files.

- [ ] **Step 6: Build and install the signed release APK**

Run: `./gradlew assembleRelease`

Install the generated release APK with Android's package installer or:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Expected: Home Relay appears in Zalo's Android Share sheet, has the selected destination after relaunch, and the end-to-end checklist passes on the physical phone.

## Plan Self-Review

### Spec Coverage

- Fixed one-time destination selection and persistent tree permission: Task 3 and Task 7.
- Standard Android file Share-sheet target, including multiple files: Task 4 and Task 6.
- Private staging before sender URI expiry: Task 4.
- Persistent queue, output-name collision avoidance, retry, cancellation, and recovery: Tasks 2 and 5.
- 10 MiB foreground progress and notifications: Tasks 5 and 7.
- Settings and Recent Uploads user interface: Task 7.
- No OAuth, Drive API, cloud backend, or broad storage permission: Global Constraints and Task 3.
- Unit, worker, Compose, provider, Zalo, Windows, and signed-APK tests: Tasks 2-8.
- Sideload distribution and signing-key handling: Task 8.

### Placeholder Scan

The plan contains no unresolved requirements, future implementation markers, or unspecified error-handling steps.

### Type Consistency

- Queue state is consistently named `UploadState`; permanent recovery state is consistently `NEEDS_ATTENTION`.
- Queue records use `UploadItem.id`; WorkManager input uses `upload_item_id`; unique work uses `upload:<itemId>`.
- Destination operations consistently use `DestinationGateway.validate` and `DestinationGateway.write` with `DestinationResult`.
- Shared files consistently use `IncomingShare` and `StageResult`.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-08-29-home-relay-android.md`. Two execution options:

1. **Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?

package app.maw629.homerelay.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class UploadDaoTest {
    private lateinit var database: HomeRelayDatabase
    private lateinit var dao: UploadDao
    private val migrationDatabaseName = "upload-dao-migration-test"

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HomeRelayDatabase::class.java,
        emptyList()
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HomeRelayDatabase::class.java
        ).build()
        dao = database.uploadDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertThenObserveReturnsQueuedItem() = runTest {
        dao.insert(sampleUpload(state = UploadState.QUEUED))

        assertEquals(UploadState.QUEUED, dao.observeAll().first().single().state)
    }

    @Test
    fun reopeningPersistentQueueRequeuesInterruptedUploadsForRescheduling() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "upload-dao-${UUID.randomUUID()}.db")
        database.close()
        database = Room.databaseBuilder(context, HomeRelayDatabase::class.java, file.absolutePath).build()
        dao = database.uploadDao()
        dao.insert(sampleUpload(state = UploadState.UPLOADING))
        database.close()

        database = Room.databaseBuilder(context, HomeRelayDatabase::class.java, file.absolutePath).build()
        dao = database.uploadDao()

        assertEquals(1, dao.requeueInterruptedUploads())
        assertEquals(listOf("upload-1"), dao.queuedIds())
        assertEquals(UploadState.QUEUED, dao.get("upload-1")!!.state)
        database.close()
        file.delete()
    }

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
        assertEquals("20260831-120000-a1b2c3-provider-report.pdf", item.outputName)
        assertEquals(42L, item.byteSize)
    }

    @Test
    fun completeStagingDoesNotChangeQueuedRows() = runTest {
        dao.insert(sampleUpload(state = UploadState.QUEUED))

        assertEquals(
            0,
            dao.completeStaging(
                id = "upload-1",
                originalName = "provider-report.pdf",
                outputName = "20260831-120000-a1b2c3-provider-report.pdf",
                byteSize = 42L
            )
        )

        val item = dao.get("upload-1")!!
        assertEquals(UploadState.QUEUED, item.state)
        assertEquals("report.pdf", item.originalName)
        assertEquals("20260829-142501-a1b2c3-report.pdf", item.outputName)
        assertEquals(1_024L, item.byteSize)
    }

    @Test
    fun failStagingMarksRecoveredStagingRowsAsInterrupted() = runTest {
        dao.insert(sampleUpload(state = UploadState.STAGING))

        val stagingItem = dao.stagingItems().single()
        assertEquals("upload-1", stagingItem.id)
        assertEquals(1, dao.failStaging(stagingItem.id, UploadErrorCode.SHARE_INTERRUPTED))

        val item = dao.get("upload-1")!!
        assertEquals(UploadState.NEEDS_ATTENTION, item.state)
        assertEquals(UploadErrorCode.SHARE_INTERRUPTED, item.errorCode)
    }

    @Test
    fun migrationFromVersionOneRetainsQueuedUploadMetadata() {
        migrationHelper.createDatabase(migrationDatabaseName, 1).apply {
            execSQL(
                """
                INSERT INTO upload_items (
                    id, originalName, mimeType, outputName, stagedPath, byteSize,
                    createdAtMillis, retryCount, state, errorCode
                ) VALUES (
                    'queued-upload', 'report.pdf', 'application/pdf',
                    '20260829-142501-a1b2c3-report.pdf', '/data/user/0/app.maw629.homerelay/files/report.pdf',
                    1024, 1788013501000, 0, 'QUEUED', 'NONE'
                )
                """.trimIndent()
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            migrationDatabaseName,
            2,
            true,
            HomeRelayDatabase.MIGRATION_1_2
        ).apply {
            query("SELECT id, stagedPath, state, errorCode FROM upload_items").use { cursor ->
                cursor.moveToFirst()
                assertEquals("queued-upload", cursor.getString(0))
                assertEquals("/data/user/0/app.maw629.homerelay/files/report.pdf", cursor.getString(1))
                assertEquals("QUEUED", cursor.getString(2))
                assertEquals("NONE", cursor.getString(3))
            }
            close()
        }
    }

    private fun sampleUpload(state: UploadState) = UploadItem(
        id = "upload-1",
        originalName = "report.pdf",
        mimeType = "application/pdf",
        outputName = "20260829-142501-a1b2c3-report.pdf",
        stagedPath = "/data/user/0/app.maw629.homerelay/files/report.pdf",
        byteSize = 1_024,
        createdAtMillis = 1_788_013_501_000,
        retryCount = 0,
        state = state,
        errorCode = UploadErrorCode.NONE
    )
}

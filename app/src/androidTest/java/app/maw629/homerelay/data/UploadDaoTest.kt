package app.maw629.homerelay.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UploadDaoTest {
    private lateinit var database: HomeRelayDatabase
    private lateinit var dao: UploadDao

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

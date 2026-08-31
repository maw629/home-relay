package app.maw629.homerelay.ui

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.maw629.homerelay.HomeRelayApplication
import app.maw629.homerelay.MainActivity
import app.maw629.homerelay.data.UploadErrorCode
import app.maw629.homerelay.data.UploadItem
import app.maw629.homerelay.data.UploadState
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InterruptedShareRecoveryTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun clearQueue() = runBlocking {
        application.container.database.clearAllTables()
    }

    @After
    fun clearQueueAfterTest() = runBlocking {
        application.container.database.clearAllTables()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun recoveredStagingRowTellsSenderToShareAgainWithoutRetry() {
        val stagedPath = File(application.noBackupFilesDir, "pending/interrupted-share.staged")
        runBlocking {
            application.container.database.uploadDao().insert(
                UploadItem(
                    id = "interrupted-share",
                    originalName = "report.pdf",
                    mimeType = "application/pdf",
                    outputName = "report.pdf",
                    stagedPath = stagedPath.absolutePath,
                    byteSize = 0,
                    createdAtMillis = 1,
                    retryCount = 0,
                    state = UploadState.STAGING,
                    errorCode = UploadErrorCode.NONE
                )
            )
            assertEquals(1, application.container.uploadRepository.recoverInterruptedStaging())
        }
        ActivityScenario.launch<MainActivity>(Intent(application, MainActivity::class.java)).use {
            composeRule.waitUntilAtLeastOneExists(hasText("Share the file again."), 5_000)
            composeRule.onNodeWithText("Share the file again.").assertExists()
            composeRule.onNodeWithText("Retry").assertDoesNotExist()
        }
    }

    private val application: HomeRelayApplication
        get() = ApplicationProvider.getApplicationContext()
}

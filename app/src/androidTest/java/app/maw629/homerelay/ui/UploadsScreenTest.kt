package app.maw629.homerelay.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import app.maw629.homerelay.data.UploadErrorCode
import app.maw629.homerelay.data.UploadState
import org.junit.Rule
import org.junit.Test

class UploadsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun needsAttentionWithLostDestinationInvokesRetryAndFolderSelection() {
        var retriedId: String? = null
        var folderRequests = 0
        composeRule.setContent {
            UploadsScreen(
                uploads = listOf(
                    UploadRow(
                        id = "upload-1",
                        name = "report.pdf",
                        sizeBytes = 42,
                        state = UploadState.NEEDS_ATTENTION,
                        errorCode = UploadErrorCode.DESTINATION_ACCESS_LOST
                    )
                ),
                onRetry = { retriedId = it },
                onCancel = {},
                onChooseFolder = { folderRequests++ }
            )
        }

        composeRule.onNodeWithText("Retry").performClick()
        composeRule.onNodeWithText("Choose folder again").performClick()

        assertEquals("upload-1", retriedId)
        assertEquals(1, folderRequests)
    }

    @Test
    fun queuedUploadInvokesCancelWithItsId() {
        var cancelledId: String? = null
        composeRule.setContent {
            UploadsScreen(
                uploads = listOf(
                    UploadRow(
                        id = "upload-1",
                        name = "report.pdf",
                        sizeBytes = 42,
                        state = UploadState.QUEUED,
                        errorCode = UploadErrorCode.NONE
                    )
                ),
                onRetry = {},
                onCancel = { cancelledId = it },
                onChooseFolder = {}
            )
        }

        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals("upload-1", cancelledId)
    }

    @Test
    fun completedUploadHasNoActions() {
        composeRule.setContent {
            UploadsScreen(
                uploads = listOf(
                    UploadRow(
                        id = "upload-1",
                        name = "report.pdf",
                        sizeBytes = 42,
                        state = UploadState.COMPLETED,
                        errorCode = UploadErrorCode.NONE
                    )
                ),
                onRetry = {},
                onCancel = {},
                onChooseFolder = {}
            )
        }

        composeRule.onNodeWithText("Retry").assertDoesNotExist()
        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
    }
}

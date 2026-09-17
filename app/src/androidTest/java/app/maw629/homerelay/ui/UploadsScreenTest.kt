package app.maw629.homerelay.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
                        createdAtMillis = 1,
                        state = UploadState.NEEDS_ATTENTION,
                        errorCode = UploadErrorCode.DESTINATION_ACCESS_LOST,
                        errorMessage = "Choose the destination folder again."
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
                        createdAtMillis = 1,
                        state = UploadState.QUEUED,
                        errorCode = UploadErrorCode.NONE,
                        errorMessage = null
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
                        createdAtMillis = 1,
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

        composeRule.onNodeWithText("Retry").assertDoesNotExist()
        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun attentionUploadShowsCreationTimeAndActionableError() {
        composeRule.setContent {
            UploadsScreen(
                uploads = listOf(
                    UploadRow(
                        id = "upload-1",
                        name = "report.pdf",
                        sizeBytes = 42,
                        createdAtMillis = 1_788_013_501_000,
                        state = UploadState.NEEDS_ATTENTION,
                        errorCode = UploadErrorCode.DESTINATION_ACCESS_LOST,
                        errorMessage = "Choose the destination folder again."
                    )
                ),
                onRetry = {},
                onCancel = {},
                onChooseFolder = {}
            )
        }

        composeRule.onNodeWithText(formatMessageTime(1_788_013_501_000)).assertExists()
        composeRule.onNodeWithText("Choose the destination folder again.").assertExists()
    }

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
}

package app.maw629.homerelay.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
        composeRule.onNodeWithText("PDF • 42 bytes").assertExists()
        composeRule.onNodeWithText(formatMessageTime(1_000_000_000_000L)).assertExists()
        composeRule.onNodeWithText("Sent ✓").assertExists()
    }

    @Test
    fun fileCardRespectsMinWidth() {
        composeRule.setContent {
            UploadsScreen(
                uploads = listOf(
                    UploadRow(
                        id = "short",
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
        val minPx = with(composeRule.density) { 192.dp.toPx() }
        val maxPx = with(composeRule.density) { 320.dp.toPx() }
        val shortWidth = composeRule.onNodeWithTag("messageBubble")
            .fetchSemanticsNode().boundsInRoot.width
        assertTrue(
            "File card must be at least 192.dp wide",
            shortWidth + 1f >= minPx
        )
        assertTrue(
            "Short file card must shrink-wrap below 320.dp",
            shortWidth <= maxPx - 1f
        )
    }

    @Test
    fun fileCardRespectsMaxWidth() {
        composeRule.setContent {
            UploadsScreen(
                uploads = listOf(
                    UploadRow(
                        id = "long",
                        name = "Consular Electronic Application Form Filled.pdf",
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
        val maxPx = with(composeRule.density) { 320.dp.toPx() }
        val longWidth = composeRule.onNodeWithTag("messageBubble")
            .fetchSemanticsNode().boundsInRoot.width
        assertTrue(
            "File card must be at most 320.dp wide",
            longWidth <= maxPx + 1f
        )
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
        composeRule.onNodeWithText("PDF • 42 bytes").assertExists()
        composeRule.onNodeWithText(formatMessageTime(1_000_000_000_000L)).assertExists()
        composeRule.onNodeWithText("Sent ✓").assertExists()
    }

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

    @Test
    fun dayDividerRendersAboveItsMessages() {
        composeRule.setContent {
            UploadsScreen(
                uploads = listOf(
                    UploadRow(
                        id = "upload-1",
                        name = "solo.pdf",
                        sizeBytes = 1,
                        createdAtMillis = System.currentTimeMillis(),
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

        val headerTop = composeRule.onNodeWithTag("dayHeader")
            .fetchSemanticsNode().boundsInRoot.top
        val messageTop = composeRule.onNodeWithText("solo.pdf")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "Day divider must render above its messages",
            headerTop < messageTop
        )
    }

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

    @Test
    fun headerShowsSenderIdentity() {
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
        composeRule.onAllNodesWithText("Me").assertCountEquals(1)
    }

    @Test
    fun senderAvatarIsCircular() {
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

        val bounds = composeRule.onNodeWithTag("senderAvatar")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(bounds.width, bounds.height, 1f)
    }
}

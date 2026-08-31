package app.maw629.homerelay.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun noDestinationShowsChooseDriveFolderAction() {
        composeRule.setContent {
            SettingsScreen(state = SettingsState(destinationName = null), onChooseFolder = {})
        }

        composeRule.onNodeWithText("Choose Drive folder").assertExists()
    }

    @Test
    fun selectedDestinationShowsChangeFolderAction() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsState(destinationName = "Home Relay Inbox"),
                onChooseFolder = {}
            )
        }

        composeRule.onNodeWithText("Change folder").assertExists()
    }

    @Test
    fun deniedNotificationPermissionShowsEnableActionAndInvokesCallback() {
        var enableRequests = 0
        composeRule.setContent {
            SettingsScreen(
                state = SettingsState(destinationName = null),
                onChooseFolder = {},
                notificationsEnabled = false,
                onEnableNotifications = { enableRequests++ }
            )
        }

        composeRule.onNodeWithText("Enable upload notifications").performClick()

        assertEquals(1, enableRequests)
    }

    @Test
    fun grantedNotificationPermissionShowsEnabledState() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsState(destinationName = null),
                onChooseFolder = {},
                notificationsEnabled = true
            )
        }

        composeRule.onNodeWithText("Upload notifications enabled").assertExists()
    }
}

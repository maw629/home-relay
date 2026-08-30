package app.maw629.homerelay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    state: SettingsState,
    onChooseFolder: () -> Unit,
    notificationsEnabled: Boolean = true,
    onEnableNotifications: () -> Unit = {}
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Home Relay")
        Text("Settings")
        state.destinationName?.let { Text(it) }
        Button(onClick = onChooseFolder) {
            Text(if (state.destinationName == null) "Choose Drive folder" else "Change folder")
        }
        state.error?.let { Text(it) }
        if (notificationsEnabled) {
            Text("Upload notifications enabled")
        } else {
            Button(onClick = onEnableNotifications) {
                Text("Enable upload notifications")
            }
        }
    }
}

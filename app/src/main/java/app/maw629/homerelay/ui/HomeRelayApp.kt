package app.maw629.homerelay.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.maw629.homerelay.ui.theme.HomeRelayTheme

private enum class HomeRelayDestination { UPLOADS, SETTINGS }

@Composable
fun HomeRelayApp(viewModel: HomeRelayViewModel) {
    val context = LocalContext.current
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val uploads by viewModel.uploads.collectAsStateWithLifecycle()
    var destination by remember { mutableStateOf(HomeRelayDestination.UPLOADS) }
    var notificationsEnabled by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            viewModel.selectDestination(it) { selectedUri ->
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(selectedUri, flags)
            }
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsEnabled = granted }

    HomeRelayTheme {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = destination == HomeRelayDestination.UPLOADS,
                        onClick = { destination = HomeRelayDestination.UPLOADS },
                        icon = { Text("Uploads") },
                        label = { Text("Uploads") }
                    )
                    NavigationBarItem(
                        selected = destination == HomeRelayDestination.SETTINGS,
                        onClick = { destination = HomeRelayDestination.SETTINGS },
                        icon = { Text("Settings") },
                        label = { Text("Settings") }
                    )
                }
            }
        ) { padding ->
            when (destination) {
                HomeRelayDestination.UPLOADS -> UploadsScreen(
                    modifier = Modifier.padding(padding),
                    uploads = uploads,
                    onRetry = viewModel::retryUpload,
                    onCancel = viewModel::cancelUpload,
                    onChooseFolder = { treePicker.launch(null) }
                )
                HomeRelayDestination.SETTINGS -> SettingsScreen(
                    modifier = Modifier.padding(padding),
                    state = settingsState,
                    onChooseFolder = { treePicker.launch(null) },
                    notificationsEnabled = notificationsEnabled,
                    onEnableNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsEnabled) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }
        }
    }
}

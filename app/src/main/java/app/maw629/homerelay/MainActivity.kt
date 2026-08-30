package app.maw629.homerelay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import app.maw629.homerelay.ui.HomeRelayViewModel
import app.maw629.homerelay.ui.HomeRelayApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HomeRelayApplication).container
        val viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HomeRelayViewModel(
                    container.destinationStore,
                    container.destinationGateway,
                    container.uploadRepository
                ) as T
            }
        })[HomeRelayViewModel::class.java]
        setContent {
            HomeRelayApp(viewModel)
        }
    }
}

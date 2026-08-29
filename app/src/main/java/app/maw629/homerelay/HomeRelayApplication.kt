package app.maw629.homerelay

import android.app.Application
import androidx.room.Room
import app.maw629.homerelay.data.DestinationStore
import app.maw629.homerelay.data.HomeRelayDatabase
import app.maw629.homerelay.destination.AndroidDocumentTreeGateway

class HomeRelayApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Application) {
    val database: HomeRelayDatabase = Room.databaseBuilder(
        context,
        HomeRelayDatabase::class.java,
        "home-relay.db"
    ).build()
    val destinationStore = DestinationStore(context)
    val destinationGateway = AndroidDocumentTreeGateway(context)
}

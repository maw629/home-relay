package app.maw629.homerelay

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.room.Room
import app.maw629.homerelay.data.DestinationStore
import app.maw629.homerelay.data.HomeRelayDatabase
import app.maw629.homerelay.destination.AndroidDocumentTreeGateway
import app.maw629.homerelay.notifications.UploadNotifier
import app.maw629.homerelay.work.UploadWorker

class HomeRelayApplication : Application(), Configuration.Provider {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        initializeContainer()
        container.uploadNotifier.createChannel()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(HomeRelayWorkerFactory(initializeContainer()))
            .build()

    private fun initializeContainer(): AppContainer {
        if (!::container.isInitialized) container = AppContainer(this)
        return container
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
    val uploadNotifier = UploadNotifier(context)
}

class HomeRelayWorkerFactory(
    private val container: AppContainer
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = when (workerClassName) {
        UploadWorker::class.java.name -> UploadWorker(
            appContext,
            workerParameters,
            container.database.uploadDao(),
            container.destinationStore,
            container.destinationGateway,
            container.uploadNotifier
        )
        else -> null
    }
}

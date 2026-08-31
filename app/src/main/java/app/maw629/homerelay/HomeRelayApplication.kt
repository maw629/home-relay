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
import app.maw629.homerelay.domain.UploadRepository
import app.maw629.homerelay.notifications.UploadNotifier
import app.maw629.homerelay.share.AndroidShareStager
import app.maw629.homerelay.work.WorkManagerUploadScheduler
import app.maw629.homerelay.work.UploadWorker
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HomeRelayApplication : Application(), Configuration.Provider {
    lateinit var container: AppContainer
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        initializeContainer()
        container.uploadNotifier.createChannel()
        applicationScope.launch { container.uploadRepository.resumePending() }
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
    val shareStager = AndroidShareStager(context)
    val uploadScheduler = WorkManagerUploadScheduler(context)
    val uploadRepository = UploadRepository(
        database.uploadDao(),
        uploadScheduler,
        { UUID.randomUUID().toString() },
        { System.currentTimeMillis() },
        { UUID.randomUUID().toString().take(8) },
        uploadNotifier
    )
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

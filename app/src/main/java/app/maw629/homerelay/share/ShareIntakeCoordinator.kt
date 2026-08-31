package app.maw629.homerelay.share

import app.maw629.homerelay.data.DestinationRepository
import app.maw629.homerelay.data.UploadErrorCode
import app.maw629.homerelay.data.UploadItem
import app.maw629.homerelay.domain.UploadRepository
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ShareIntakeStatus {
    data object Preparing : ShareIntakeStatus

    data class Terminal(
        val queuedCount: Int,
        val attentionCount: Int,
        val queueUnavailableCount: Int,
        val attentionErrors: Set<UploadErrorCode>,
        val terminalAtMillis: Long
    ) : ShareIntakeStatus
}

interface ShareIntakeOperations {
    suspend fun recoverInterruptedStaging()
    suspend fun awaitRecovery(): Result<Unit>
    fun start(intakeId: String, shares: List<IncomingShare>): StateFlow<ShareIntakeStatus>
    fun observe(intakeId: String): StateFlow<ShareIntakeStatus>?
    fun release(intakeId: String)
}

class ShareIntakeCoordinator(
    private val applicationScope: CoroutineScope,
    private val stager: ShareStager,
    private val destinationRepository: DestinationRepository,
    private val uploadRepository: UploadRepository,
    private val nowMillis: () -> Long
) : ShareIntakeOperations {
    private val operations = mutableMapOf<String, MutableStateFlow<ShareIntakeStatus>>()
    private val releaseWhenTerminal = mutableSetOf<String>()
    private val recoveryGate = CompletableDeferred<Result<Unit>>()
    private val recoveryMutex = Mutex()

    override suspend fun recoverInterruptedStaging() {
        if (recoveryGate.isCompleted) return

        recoveryMutex.withLock {
            if (!recoveryGate.isCompleted) {
                recoveryGate.complete(
                    runCatching { uploadRepository.recoverInterruptedStaging() }.map { Unit }
                )
            }
        }
    }

    override suspend fun awaitRecovery(): Result<Unit> = recoveryGate.await()

    override fun start(
        intakeId: String,
        shares: List<IncomingShare>
    ): StateFlow<ShareIntakeStatus> {
        var startOperation = false
        val operation = synchronized(operations) {
            operations[intakeId] ?: MutableStateFlow<ShareIntakeStatus>(ShareIntakeStatus.Preparing).also {
                operations[intakeId] = it
                startOperation = true
            }
        }

        if (startOperation) {
            applicationScope.launchIntake(intakeId, operation, shares)
        }
        return operation
    }

    override fun observe(intakeId: String): StateFlow<ShareIntakeStatus>? = synchronized(operations) {
        operations[intakeId]
    }

    override fun release(intakeId: String) {
        synchronized(operations) {
            operations[intakeId]?.let { operation ->
                if (operation.value is ShareIntakeStatus.Terminal) {
                    operations.remove(intakeId)
                } else {
                    releaseWhenTerminal += intakeId
                }
            }
        }
    }

    private fun CoroutineScope.launchIntake(
        intakeId: String,
        operation: MutableStateFlow<ShareIntakeStatus>,
        shares: List<IncomingShare>
    ) {
        launch {
            val aggregate = Aggregate()
            try {
                if (awaitRecovery().isFailure) {
                    aggregate.queueUnavailableCount += shares.size
                } else {
                    shares.forEach { share -> stage(share, aggregate) }
                }
            } catch (_: Throwable) {
                aggregate.queueUnavailableCount += 1
            } finally {
                operation.value = ShareIntakeStatus.Terminal(
                    queuedCount = aggregate.queuedCount,
                    attentionCount = aggregate.attentionCount,
                    queueUnavailableCount = aggregate.queueUnavailableCount,
                    attentionErrors = aggregate.attentionErrors,
                    terminalAtMillis = nowMillis()
                )
                synchronized(operations) {
                    if (releaseWhenTerminal.remove(intakeId) && operations[intakeId] === operation) {
                        operations.remove(intakeId)
                    }
                }
            }
        }
    }

    private suspend fun stage(share: IncomingShare, aggregate: Aggregate) {
        val item = try {
            uploadRepository.createStaging(share) { id -> stager.pendingFile(id).absolutePath }
        } catch (_: Throwable) {
            aggregate.queueUnavailableCount++
            return
        }
        val target = File(item.stagedPath)
        val result = try {
            stager.stage(target, share)
        } catch (_: Throwable) {
            failStaging(item, UploadErrorCode.SOURCE_UNREADABLE, target, aggregate)
            return
        }

        when (result) {
            StageResult.SourceUnreadable -> failStaging(
                item,
                UploadErrorCode.SOURCE_UNREADABLE,
                target,
                aggregate
            )
            StageResult.StorageFull -> failStaging(
                item,
                UploadErrorCode.STAGING_STORAGE_FULL,
                target,
                aggregate
            )
            is StageResult.Staged -> completeStaging(item, result, aggregate)
        }
    }

    private suspend fun completeStaging(
        item: UploadItem,
        staged: StageResult.Staged,
        aggregate: Aggregate
    ) {
        val destinationAvailable = try {
            destinationRepository.destinationTreeUri.firstOrNull() != null
        } catch (_: Throwable) {
            false
        }
        if (!destinationAvailable) {
            failStaging(item, UploadErrorCode.DESTINATION_ACCESS_LOST, staged.file, aggregate)
            return
        }

        val queued = try {
            uploadRepository.completeStaging(item, staged)
        } catch (_: Throwable) {
            false
        }
        if (queued) {
            aggregate.queuedCount++
        } else {
            staged.file.delete()
            File(item.stagedPath).delete()
            aggregate.queueUnavailableCount++
        }
    }

    private suspend fun failStaging(
        item: UploadItem,
        error: UploadErrorCode,
        completedFile: File,
        aggregate: Aggregate
    ) {
        val failed = try {
            uploadRepository.failStaging(item.id, error)
        } catch (_: Throwable) {
            false
        }
        if (failed) {
            if (error != UploadErrorCode.DESTINATION_ACCESS_LOST) completedFile.delete()
            aggregate.attentionCount++
            aggregate.attentionErrors += error
        } else {
            completedFile.delete()
            File(item.stagedPath).delete()
            aggregate.queueUnavailableCount++
        }
    }

    private class Aggregate {
        var queuedCount = 0
        var attentionCount = 0
        var queueUnavailableCount = 0
        val attentionErrors = linkedSetOf<UploadErrorCode>()
    }
}

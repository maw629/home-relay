package app.maw629.homerelay.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.maw629.homerelay.data.UploadErrorCode
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ShareIntakeViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val operations: ShareIntakeOperations,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {
    private val mutableStatus = MutableStateFlow<ShareIntakeStatus>(ShareIntakeStatus.Preparing)
    val status: StateFlow<ShareIntakeStatus> = mutableStatus.asStateFlow()

    private var intakeId: String? = null
    private var statusJob: Job? = null

    val hasSavedIntake: Boolean
        get() = savedStateHandle.get<String>(INTAKE_ID_KEY) != null

    fun beginOrAttach(shares: List<IncomingShare>) {
        if (intakeId != null) return

        val savedIntakeId = savedStateHandle.get<String>(INTAKE_ID_KEY)
        if (savedIntakeId == null) {
            val newIntakeId = UUID.randomUUID().toString()
            savedStateHandle[INTAKE_ID_KEY] = newIntakeId
            intakeId = newIntakeId
            observe(operations.start(newIntakeId, shares))
            return
        }

        intakeId = savedIntakeId
        operations.observe(savedIntakeId)?.let(::observe) ?: viewModelScope.launch {
            operations.awaitRecovery()
            mutableStatus.value = ShareIntakeStatus.Terminal(
                queuedCount = 0,
                attentionCount = 1,
                queueUnavailableCount = 0,
                attentionErrors = setOf(UploadErrorCode.SHARE_INTERRUPTED),
                terminalAtMillis = nowMillis()
            )
        }
    }

    override fun onCleared() {
        intakeId?.let(operations::release)
    }

    private fun observe(operation: StateFlow<ShareIntakeStatus>) {
        statusJob?.cancel()
        statusJob = viewModelScope.launch {
            operation.collect { mutableStatus.value = it }
        }
    }

    companion object {
        const val INTAKE_ID_KEY = "share_intake_id"
    }
}

package app.maw629.homerelay.share

import android.os.SystemClock
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
    private val nowUptimeMillis: () -> Long = { SystemClock.uptimeMillis() }
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
            savedStateHandle.remove<Long>(TERMINAL_DISPLAY_START_UPTIME_KEY)
            val newIntakeId = UUID.randomUUID().toString()
            savedStateHandle[INTAKE_ID_KEY] = newIntakeId
            intakeId = newIntakeId
            observe(operations.start(newIntakeId, shares))
            return
        }

        intakeId = savedIntakeId
        operations.observe(savedIntakeId)?.let(::observe) ?: viewModelScope.launch {
            savedStateHandle.remove<Long>(TERMINAL_DISPLAY_START_UPTIME_KEY)
            operations.awaitRecovery()
            mutableStatus.value = ShareIntakeStatus.Terminal(
                queuedCount = 0,
                attentionCount = 1,
                queueUnavailableCount = 0,
                attentionErrors = setOf(UploadErrorCode.SHARE_INTERRUPTED),
                terminalAtMillis = nowUptimeMillis()
            )
        }
    }

    fun terminalDisplayStartUptime(): Long? = savedStateHandle.get(TERMINAL_DISPLAY_START_UPTIME_KEY)

    fun recordTerminalDisplayStartUptime(): Long =
        savedStateHandle.get<Long>(TERMINAL_DISPLAY_START_UPTIME_KEY)
            ?: nowUptimeMillis().also { savedStateHandle[TERMINAL_DISPLAY_START_UPTIME_KEY] = it }

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
        const val TERMINAL_DISPLAY_START_UPTIME_KEY = "terminal_display_start_uptime"
    }
}

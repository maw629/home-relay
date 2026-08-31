package app.maw629.homerelay.share

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

internal object ShareReceiverDiagnostics {
    private const val TAG = "HomeRelayShareReceiver"
    private val events = ArrayDeque<String>()

    @Synchronized
    fun event(name: String, detail: String = "") {
        val event = "uptime=${SystemClock.uptimeMillis()} event=$name $detail"
        events += event
        while (events.size > MAXIMUM_RECORDED_EVENTS) events.removeFirst()
        Log.d(TAG, event)
    }

    @Synchronized
    fun clear() = events.clear()

    @Synchronized
    fun snapshot(): String = events.joinToString(separator = " | ")

    fun probeHandler(delayMillis: Long) {
        Handler(Looper.getMainLooper()).postDelayed(
            { event("handler_fired", "delay=$delayMillis") },
            delayMillis
        )
    }

    private const val MAXIMUM_RECORDED_EVENTS = 32
}

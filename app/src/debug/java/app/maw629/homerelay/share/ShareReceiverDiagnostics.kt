package app.maw629.homerelay.share

import android.os.SystemClock
import android.util.Log

internal object ShareReceiverDiagnostics {
    private const val TAG = "HomeRelayShareReceiver"

    fun event(name: String, detail: String = "") {
        Log.d(TAG, "uptime=${SystemClock.uptimeMillis()} event=$name $detail")
    }
}

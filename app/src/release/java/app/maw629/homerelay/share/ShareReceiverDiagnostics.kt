package app.maw629.homerelay.share

internal object ShareReceiverDiagnostics {
    fun event(name: String, detail: String = "") = Unit
    fun clear() = Unit
    fun snapshot(): String = ""
    fun probeHandler(delayMillis: Long) = Unit
}

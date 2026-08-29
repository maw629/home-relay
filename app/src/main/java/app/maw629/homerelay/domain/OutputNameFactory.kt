package app.maw629.homerelay.domain

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object OutputNameFactory {
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneOffset.UTC)

    fun create(originalName: String, nowMillis: Long, randomSuffix: String): String {
        val safeName = originalName
            .ifBlank { "shared-file" }
            .replace('/', '_')
            .replace('\\', '_')
            .replace('\u0000', '_')
            .trim()
            .ifBlank { "shared-file" }
        return "${formatter.format(Instant.ofEpochMilli(nowMillis))}-$randomSuffix-$safeName"
    }
}

package app.maw629.homerelay.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class OutputNameFactoryTest {
    @Test
    fun createPreservesExtensionAndAddsTimestampAndSuffix() {
        assertEquals(
            "20260829-142501-a1b2c3-report.pdf",
            OutputNameFactory.create("report.pdf", 1_788_013_501_000, "a1b2c3")
        )
    }

    @Test
    fun createRemovesPathSeparatorsFromOriginalName() {
        assertEquals(
            "20260829-142501-a1b2c3-quarterly_report.pdf",
            OutputNameFactory.create("quarterly/report.pdf", 1_788_013_501_000, "a1b2c3")
        )
    }
}

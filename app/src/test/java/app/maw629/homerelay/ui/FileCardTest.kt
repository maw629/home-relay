package app.maw629.homerelay.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileCardTest {
    @Test
    fun shortNameUnchanged() {
        assertEquals("report.pdf", middleEllipsize("report.pdf"))
    }

    @Test
    fun longNameTruncatedWithExtensionKept() {
        val name = "Consular Electronic Application Form Filled.pdf"
        val result = middleEllipsize(name)
        assertTrue(result.length <= 28)
        assertTrue("..." in result)
        assertTrue(result.endsWith(".pdf"))
    }

    @Test
    fun extensionlessLongNameTruncated() {
        val result = middleEllipsize("a".repeat(50))
        assertEquals(28, result.length)
    }

    @Test
    fun badgeColorsPerType() {
        assertEquals(Color(0xFFD32F2F), fileBadgeColor("pdf"))
        assertEquals(Color(0xFF1976D2), fileBadgeColor("DOCX"))
        assertEquals(Color(0xFF388E3C), fileBadgeColor("xlsx"))
        assertEquals(Color(0xFFEF6C00), fileBadgeColor("ppt"))
        assertEquals(Color(0xFF7B1FA2), fileBadgeColor("zip"))
        assertEquals(Color(0xFF00897B), fileBadgeColor("jpg"))
        assertEquals(Color(0xFF616161), fileBadgeColor("xyz"))
        assertEquals(Color(0xFF616161), fileBadgeColor(""))
    }

    @Test
    fun typeLabels() {
        assertEquals("PDF", fileTypeLabel("report.pdf"))
        assertEquals("DOCX", fileTypeLabel("a.docx"))
        assertEquals("FILE", fileTypeLabel("README"))
    }
}

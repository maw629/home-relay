package app.maw629.homerelay.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FileIconTest {
    @Test
    fun pdfExtensionMapsToPdfMonogram() {
        assertEquals("PDF", fileMonogram("report.pdf"))
    }

    @Test
    fun extensionIsUppercased() {
        assertEquals("JPG", fileMonogram("photo.jpg"))
    }

    @Test
    fun missingExtensionMapsToEllipsis() {
        assertEquals("···", fileMonogram("README"))
    }

    @Test
    fun lastExtensionWins() {
        assertEquals("GZ", fileMonogram("archive.tar.gz"))
    }

    @Test
    fun longExtensionTruncatesToFourChars() {
        assertEquals("JPEG", fileMonogram("photo.jpeg2000"))
    }
}

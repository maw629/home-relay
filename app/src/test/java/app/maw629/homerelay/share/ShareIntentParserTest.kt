package app.maw629.homerelay.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShareIntentParserTest {
    @Test
    fun parseSingleStreamReturnsOneFile() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, Uri.parse("content://sender/report"))
        }

        assertEquals(
            listOf(Uri.parse("content://sender/report")),
            ShareIntentParser.parse(intent).map(IncomingShare::uri)
        )
    }

    @Test
    fun parsePlainTextReturnsNoFiles() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "hello")
        }

        assertTrue(ShareIntentParser.parse(intent).isEmpty())
    }

    @Test
    fun parseMultipleStreamsAndClipDataDeduplicatesUris() {
        val first = Uri.parse("content://sender/first")
        val second = Uri.parse("content://sender/second")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/pdf"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
            clipData = ClipData("shared", arrayOf("application/pdf"), ClipData.Item(first)).apply {
                addItem(ClipData.Item(second))
            }
        }

        assertEquals(listOf(first, second), ShareIntentParser.parse(intent).map(IncomingShare::uri))
    }

    @Test
    fun parseRejectsFileAndOtherNonContentUris() {
        val content = Uri.parse("content://sender/report")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/pdf"
            putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                arrayListOf(content, Uri.parse("file:///tmp/report.pdf"), Uri.parse("https://example.test/report.pdf"))
            )
        }

        assertEquals(listOf(content), ShareIntentParser.parse(intent).map(IncomingShare::uri))
    }
}

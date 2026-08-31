package app.maw629.homerelay.share

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidShareStagerTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val stager = AndroidShareStager(context)

    @Test
    fun pendingFileReservesPathWithoutCreatingThePrivateFile() {
        val target = stager.pendingFile("uncreated-${UUID.randomUUID()}")

        assertFalse(
            "Creating the private file before staging would make an incomplete target look durable",
            target.exists()
        )
    }

    @Test
    fun stageWritesToCallerOwnedTargetRatherThanGeneratingAnUnstoredName() = runTest {
        val target = stager.pendingFile("item-1")
        target.delete()

        val result = stager.stage(
            target,
            IncomingShare(
                Uri.parse("content://app.maw629.homerelay.share-test/report.pdf"),
                "report.pdf",
                "application/pdf"
            )
        )

        assertTrue(result is StageResult.Staged)
        result as StageResult.Staged
        assertEquals(target.canonicalFile, result.file.canonicalFile)
        assertEquals("hello", target.readText())
        assertEquals(5, result.byteSize)
        assertEquals(
            File(context.noBackupFilesDir, "pending").canonicalFile,
            target.parentFile?.canonicalFile
        )
        assertEquals("item-1.staged", target.name)
    }

    @Test
    fun stageFailureRemovesCallerOwnedTargetAndPartial() = runTest {
        val target = stager.pendingFile("item-2")
        target.parentFile!!.mkdirs()
        target.writeText("stale target")

        val result = stager.stage(
            target,
            IncomingShare(
                Uri.parse("content://app.maw629.homerelay.share-test/failed-stream.pdf"),
                "report.pdf",
                "application/pdf"
            )
        )

        assertEquals(StageResult.SourceUnreadable, result)
        assertFalse(target.exists())
        assertTrue(
            target.parentFile?.listFiles().orEmpty().none {
                it.name.startsWith("${target.name}.") && it.name.endsWith(".partial")
            }
        )
    }

    @Test
    fun stageRejectsNonContentUriBeforeOpeningIt() = runTest {
        val target = stager.pendingFile("item-file")
        val result = stager.stage(
            target,
            IncomingShare(Uri.parse("file:///tmp/report.pdf"), "report.pdf", "application/pdf")
        )

        assertEquals(StageResult.SourceUnreadable, result)
    }

    @Test
    fun stageMissingProviderDisplayNameUsesSharedFile() = runTest {
        val target = stager.pendingFile("item-3")
        val result = stager.stage(
            target,
            IncomingShare(
                Uri.parse("content://app.maw629.homerelay.share-test/missing-display-name"),
                "caller-name.pdf",
                "application/pdf"
            )
        )

        assertTrue(result is StageResult.Staged)
        assertEquals("shared-file", (result as StageResult.Staged).displayName)
    }

    @Test
    fun stageUsesProviderDisplayNameInsteadOfUriPath() = runTest {
        val target = stager.pendingFile("item-4")
        val result = stager.stage(
            target,
            IncomingShare(
                Uri.parse("content://app.maw629.homerelay.share-test/opaque-id"),
                "opaque-id",
                "application/pdf"
            )
        )

        assertTrue(result is StageResult.Staged)
        assertEquals("report.pdf", (result as StageResult.Staged).displayName)
    }
}

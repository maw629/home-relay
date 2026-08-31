package app.maw629.homerelay.share

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidShareStagerTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val stager = AndroidShareStager(context)

    @Test
    fun stageCopiesProviderBytesIntoNoBackupStorage() = runTest {
        val result = stager.stage(
            "item-1",
            IncomingShare(
                Uri.parse("content://app.maw629.homerelay.share-test/report.pdf"),
                "ignored-name.pdf",
                "application/pdf"
            )
        )

        assertTrue(result is StageResult.Staged)
        result as StageResult.Staged
        assertEquals("hello", result.file.readText())
        assertEquals(5, result.byteSize)
        assertEquals(File(context.noBackupFilesDir, "pending").canonicalFile, result.file.parentFile?.canonicalFile)
    }

    @Test
    fun stageUnreadableUriReturnsSourceUnreadable() = runTest {
        val result = stager.stage(
            "item-2",
            IncomingShare(
                Uri.parse("content://missing.provider/report.pdf"),
                "report.pdf",
                "application/pdf"
            )
        )

        assertEquals(StageResult.SourceUnreadable, result)
    }

    @Test
    fun stageMissingProviderDisplayNameUsesSharedFile() = runTest {
        val result = stager.stage(
            "item-3",
            IncomingShare(
                Uri.parse("content://app.maw629.homerelay.share-test/missing-display-name"),
                "caller-name.pdf",
                "application/pdf"
            )
        )

        assertTrue(result is StageResult.Staged)
        assertEquals("item-3-shared-file", (result as StageResult.Staged).file.name)
    }
}

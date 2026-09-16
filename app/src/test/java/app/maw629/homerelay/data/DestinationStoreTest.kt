package app.maw629.homerelay.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationStoreTest {
    @Test
    fun setDestinationEmitsStoredTreeUri() = runTest {
        // Isolate DataStore's tmp-file rename in a fresh temp directory: the
        // shared temp dir is racy for this sequence on Windows file locking.
        val dir = Files.createTempDirectory("destination-store").toFile()
        val file = File(dir, "destination-store.preferences_pb")
        val store = DestinationStore(
            PreferenceDataStoreFactory.create { file }
        )
        val uri = "content://example/tree/drive%3Ahome-relay"

        try {
            store.setDestination(uri)

            assertEquals(uri, store.destinationTreeUri.first())
        } finally {
            dir.deleteRecursively()
        }
    }
}

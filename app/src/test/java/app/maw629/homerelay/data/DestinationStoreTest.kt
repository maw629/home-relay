package app.maw629.homerelay.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationStoreTest {
    @Test
    fun setDestinationEmitsStoredTreeUri() = runTest {
        val file = File.createTempFile("destination-store", ".preferences_pb")
        val store = DestinationStore(
            PreferenceDataStoreFactory.create { file }
        )
        val uri = "content://example/tree/drive%3Ahome-relay"

        try {
            store.setDestination(uri)

            assertEquals(uri, store.destinationTreeUri.first())
        } finally {
            file.delete()
        }
    }
}

package app.maw629.homerelay.destination

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidDocumentTreeGatewayTest {
    private val gateway = AndroidDocumentTreeGateway(
        ApplicationProvider.getApplicationContext()
    )

    @Test
    fun invalidTreeUriReturnsAccessLost() = runTest {
        assertEquals(
            DestinationResult.AccessLost,
            gateway.validate(Uri.parse("content://missing.provider/tree/nope"))
        )
    }
}

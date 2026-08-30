package app.maw629.homerelay.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.destinationDataStore by preferencesDataStore(name = "destination")

interface DestinationRepository {
    val destinationTreeUri: Flow<String?>
    suspend fun setDestination(uri: String)
}

class DestinationStore(private val dataStore: DataStore<Preferences>) : DestinationRepository {
    constructor(context: Context) : this(context.applicationContext.destinationDataStore)

    override val destinationTreeUri: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[DESTINATION_TREE_URI] }

    override suspend fun setDestination(uri: String) {
        dataStore.edit { preferences -> preferences[DESTINATION_TREE_URI] = uri }
    }

    suspend fun clearDestination() {
        dataStore.edit { preferences -> preferences.remove(DESTINATION_TREE_URI) }
    }

    private companion object {
        val DESTINATION_TREE_URI = stringPreferencesKey("destination_tree_uri")
    }
}

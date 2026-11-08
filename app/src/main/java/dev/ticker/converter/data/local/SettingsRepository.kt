package dev.ticker.converter.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.ticker.converter.core.model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SettingsRepository {
    val settings: Flow<Settings>
    suspend fun update(transform: (Settings) -> Settings)
}

private val Context.tickerDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository(context: Context) : SettingsRepository {
    private val store = context.applicationContext.tickerDataStore
    private val key = stringPreferencesKey("settings_json")

    override val settings: Flow<Settings> = store.data.map { SettingsCodec.decode(it[key]) }

    override suspend fun update(transform: (Settings) -> Settings) {
        store.edit { prefs -> prefs[key] = SettingsCodec.encode(transform(SettingsCodec.decode(prefs[key]))) }
    }
}

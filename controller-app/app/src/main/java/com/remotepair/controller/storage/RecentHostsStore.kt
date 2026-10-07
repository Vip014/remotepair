package com.remotepair.controller.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.remotepair.controller.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RecentHost(val id: String, val lastUsed: Long)

class RecentHostsStore(private val ctx: Context) {
    private val KEY = stringPreferencesKey("recent_hosts")
    private val json = Json { ignoreUnknownKeys = true }

    val flow: Flow<List<RecentHost>> = ctx.dataStore.data.map { prefs ->
        val raw = prefs[KEY] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<RecentHost>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun add(h: RecentHost) {
        ctx.dataStore.edit { prefs ->
            val raw = prefs[KEY]
            val existing = raw?.let {
                runCatching { json.decodeFromString<List<RecentHost>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val merged = (listOf(h) + existing.filter { it.id != h.id }).take(10)
            prefs[KEY] = json.encodeToString(merged)
        }
    }
}

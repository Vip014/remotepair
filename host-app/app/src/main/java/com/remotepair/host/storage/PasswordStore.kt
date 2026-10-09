package com.remotepair.host.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Self-contained DataStore (own file name) so it doesn't depend on any other
// store in the app. Holds the host's connect password (empty = open host).
private val Context.pwDataStore by preferencesDataStore(name = "remotepair_host_pw")

class PasswordStore(private val ctx: Context) {
    private val KEY = stringPreferencesKey("host_password")

    val password: Flow<String> = ctx.pwDataStore.data.map { it[KEY] ?: "" }

    suspend fun set(value: String) {
        ctx.pwDataStore.edit { it[KEY] = value }
    }
}

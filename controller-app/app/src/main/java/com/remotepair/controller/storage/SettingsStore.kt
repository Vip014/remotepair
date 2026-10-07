package com.remotepair.controller.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.remotepair.controller.BuildConfig
import com.remotepair.controller.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsStore(private val ctx: Context) {
    private val SIGNALING = stringPreferencesKey("signaling_url")

    val signalingUrl: Flow<String> = ctx.dataStore.data.map { prefs ->
        prefs[SIGNALING] ?: BuildConfig.DEFAULT_SIGNALING_URL
    }

    suspend fun setSignalingUrl(url: String) {
        ctx.dataStore.edit { it[SIGNALING] = url }
    }
}

package com.remotepair.host.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.remotepair.host.BuildConfig
import com.remotepair.host.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsStore(private val ctx: Context) {
    private val SIGNALING = stringPreferencesKey("signaling_url")
    val signalingUrl: Flow<String> = ctx.dataStore.data.map { it[SIGNALING] ?: BuildConfig.DEFAULT_SIGNALING_URL }
    suspend fun setSignalingUrl(url: String) { ctx.dataStore.edit { it[SIGNALING] = url } }
}

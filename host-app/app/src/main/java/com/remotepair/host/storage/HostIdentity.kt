package com.remotepair.host.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.remotepair.host.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.random.Random

/**
 * Local host identity: a 9-digit ID and passphrase.
 *
 * Note: in production the ID should come from the signaling server to guarantee
 * uniqueness. For MVP we generate locally and the server will accept whatever
 * we register with (collision is extremely rare at this scale).
 */
class HostIdentity(private val ctx: Context) {
    private val ID = stringPreferencesKey("host_id")
    private val PASS = stringPreferencesKey("host_passphrase")

    val id: Flow<String> = ctx.dataStore.data.map { it[ID] ?: "" }
    val passphrase: Flow<String> = ctx.dataStore.data.map { it[PASS] ?: "" }

    suspend fun ensureInitialized() {
        if (ctx.dataStore.data.first()[ID].isNullOrEmpty()) {
            rotate()
        }
    }

    suspend fun rotate() {
        val newId = (1..9).joinToString("") { Random.nextInt(10).toString() }
        val newPass = generatePassphrase()
        ctx.dataStore.edit {
            it[ID] = newId
            it[PASS] = newPass
        }
    }

    private fun generatePassphrase(): String {
        val adjectives = listOf("blue", "red", "fast", "quiet", "bright", "calm", "warm", "cool")
        val nouns = listOf("fox", "owl", "cat", "wolf", "hawk", "bear", "deer", "lion")
        val num = Random.nextInt(10, 100)
        return "${adjectives.random()}-${nouns.random()}-$num"
    }
}

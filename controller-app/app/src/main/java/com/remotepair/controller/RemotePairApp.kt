package com.remotepair.controller

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore by preferencesDataStore(name = "remotepair_prefs")

class RemotePairApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: RemotePairApp
            private set
    }
}

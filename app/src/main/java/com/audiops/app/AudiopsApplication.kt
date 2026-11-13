package com.audiops.app

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/** Process-wide singletons. DataStore allows only one instance per file. */
class AudiopsApplication : Application() {
    val engine: AudioEngine by lazy { AndroidAudioEngine(this) }

    val settingsStore: DataStore<Preferences> by lazy {
        // Same file as the earlier `preferencesDataStore("settings")`, so settings carry over.
        createSettingsStore(filesDir.resolve("datastore/settings.preferences_pb").absolutePath)
    }
}

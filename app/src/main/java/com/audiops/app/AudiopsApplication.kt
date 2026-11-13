package com.audiops.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope

/** Process-wide singletons. DataStore allows only one instance per file. */
class AudiopsApplication : Application() {
    val engine: AudioEngine by lazy { AndroidAudioEngine(this) }

    val settingsStore: DataStore<Preferences> by lazy {
        // Same file as the earlier `preferencesDataStore("settings")`, so settings carry over.
        createSettingsStore(filesDir.resolve("datastore/settings.preferences_pb").absolutePath)
    }

    private var startedActivities = 0
    private var savedState: SavedStateHandle? = null
    private var viewModel: AudiopsViewModel? = null

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    startedActivities++
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivities--
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    /**
     * The one [AudiopsViewModel] of this process. It belongs to the application, not to the
     * Activity, so queued and running jobs continue when the Activity is destroyed (for
     * example when Audiops is swiped away from Recents while [JobService] keeps the process).
     * [restored] is the Activity's saved state after process death; see [saveViewModelState].
     * Main thread only.
     */
    fun viewModel(restored: Bundle?): AudiopsViewModel = viewModel ?: run {
        val handle = SavedStateHandle(restored?.keySet()?.associateWith { restored.getString(it) }.orEmpty())
        val vm = AudiopsViewModel(handle, engine, SettingsRepository(settingsStore), HistoryRepository(settingsStore))
        JobNotifications(this) { startedActivities > 0 }.watch(vm.viewModelScope, vm.state, vm::cancelAll)
        savedState = handle
        viewModel = vm
        vm
    }

    /** The ViewModel's selections (tab, tool, input, language: all strings), for the Activity's saved state. */
    fun saveViewModelState(): Bundle = Bundle().apply {
        val handle = savedState ?: return@apply
        handle.keys().forEach { key -> handle.get<String>(key)?.let { putString(key, it) } }
    }
}

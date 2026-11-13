package com.audiops.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/** Finished jobs, stored as JSON in DataStore so the Activity tab survives restarts. */
class HistoryRepository(private val store: DataStore<Preferences>) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    /** Stored jobs, newest first. Unreadable data (for example from a newer version) reads as empty. */
    suspend fun load(): List<Job> {
        val raw = store.data.first()[KEY] ?: return emptyList()
        return runCatching { json.decodeFromString<List<Job>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun save(jobs: List<Job>) {
        store.edit { it[KEY] = json.encodeToString(jobs.filter { !it.status.active }.take(MAX_JOBS)) }
    }

    companion object {
        const val MAX_JOBS = 100
        private val KEY = stringPreferencesKey("job_history_v1")
    }
}

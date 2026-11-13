package com.audiops.app

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** DataStore-backed history and settings, on real files. */
class RepositoryTest {
    private val dir = File.createTempFile("audiops-store", "").apply {
        delete()
        mkdirs()
    }
    private var stores = 0

    /** A fresh store file each call: DataStore allows one instance per file. */
    private fun newStore() = createSettingsStore(File(dir, "store-${stores++}.preferences_pb").absolutePath)

    @After fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun job(id: Int, status: JobStatus = JobStatus.DONE) = Job(
        id = "$id",
        tool = Tool.ANALYZE,
        inputId = "in",
        inputName = "in.wav",
        spec = JobSpec.Analyze,
        status = status,
        createdAt = id.toLong(),
    )

    @Test fun historyKeepsFinishedJobsInOrder() = runBlocking {
        val history = HistoryRepository(newStore())
        assertEquals(emptyList<Job>(), history.load())
        val jobs = listOf(job(3), job(2, JobStatus.RUNNING), job(1, JobStatus.FAILED), job(0, JobStatus.QUEUED))
        history.save(jobs)
        assertEquals(listOf("3", "1"), history.load().map { it.id })
    }

    @Test fun historyIsCappedAtTheLimit() = runBlocking {
        val history = HistoryRepository(newStore())
        history.save((0 until HistoryRepository.MAX_JOBS + 20).map { job(it) })
        val loaded = history.load()
        assertEquals(HistoryRepository.MAX_JOBS, loaded.size)
        assertEquals("0", loaded.first().id)
    }

    @Test fun unreadableHistoryLoadsAsEmpty() = runBlocking {
        val store = newStore()
        store.edit { it[stringPreferencesKey("job_history_v1")] = "{not json" }
        assertEquals(emptyList<Job>(), HistoryRepository(store).load())
    }

    @Test fun settingsStartWithDefaults() = runBlocking {
        assertEquals(UserSettings(), SettingsRepository(newStore()).settings.first())
    }

    @Test fun settingsAreSavedAndReadBack() = runBlocking {
        val repository = SettingsRepository(newStore())
        repository.setThemeMode(ThemeMode.DARK)
        repository.setDynamicColor(false)
        repository.setPureBlack(true)
        repository.setKeepScreenOn(false)
        repository.setDefaultLanguage(SpeechLanguage.BANGLA)
        val expected =
            UserSettings(
                ThemeMode.DARK,
                dynamicColor = false,
                pureBlack = true,
                keepScreenOn = false,
                defaultLanguage = SpeechLanguage.BANGLA,
            )
        assertEquals(expected, repository.settings.first())
    }

    @Test fun unknownEnumValuesFallBackToDefaults() = runBlocking {
        val store = newStore()
        store.edit {
            it[stringPreferencesKey("theme_mode")] = "SEPIA"
            it[stringPreferencesKey("default_language")] = "KLINGON"
        }
        val settings = SettingsRepository(store).settings.first()
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(SpeechLanguage.AUTO, settings.defaultLanguage)
        assertTrue(dir.listFiles()!!.isNotEmpty())
    }
}

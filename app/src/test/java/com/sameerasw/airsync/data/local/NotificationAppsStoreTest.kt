package com.sameerasw.airsync.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.sameerasw.airsync.domain.model.NotificationApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NotificationAppsStoreTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var store: DataStore<Preferences>

    @Before
    fun setUp() {
        store = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempDir.root, "test.preferences_pb")
        }
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun app(i: Int) = NotificationApp(
        packageName = "com.example.app$i",
        appName = "App $i",
        isEnabled = true,
        lastUpdated = 0L
    )

    // Criteria 7, 8, 21: a burst of single-package toggles must lose no update.
    @Test
    fun concurrentTogglesAllApply() = runBlocking {
        val seed = (1..240).map(::app)
        store.edit { writeNotificationApps(it, seed) }

        for (state in listOf(false, true)) {
            seed.map { app ->
                async(Dispatchers.Default) {
                    store.updateNotificationApps { apps ->
                        apps.map {
                            if (it.packageName == app.packageName) it.copy(isEnabled = state) else it
                        }
                    }
                }
            }.awaitAll()

            val stored = readNotificationApps(store.data.first())
            assertEquals(240, stored.size)
            val wrong = stored.filter { it.isEnabled != state }.map { it.packageName }
            assertTrue("${wrong.size} apps not set to $state: $wrong", wrong.isEmpty())
        }
    }

    // Criterion 19: saving the app list must not wipe app_paused.
    @Test
    fun writePreservesAppPaused() = runBlocking {
        val paused = booleanPreferencesKey("app_paused")
        store.edit {
            it[paused] = true
            writeNotificationApps(it, listOf(app(1), app(2)))
        }

        store.edit { writeNotificationApps(it, listOf(app(1))) }

        val prefs = store.data.first()
        assertEquals(true, prefs[paused])
        assertEquals(listOf("com.example.app1"), readNotificationApps(prefs).map { it.packageName })
    }
}

package com.sameerasw.airsync.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sameerasw.airsync.domain.model.NotificationApp

private fun enabledKey(pkg: String) = booleanPreferencesKey("app_${pkg}_enabled")
private fun nameKey(pkg: String) = stringPreferencesKey("app_${pkg}_name")
private fun systemKey(pkg: String) = booleanPreferencesKey("app_${pkg}_system")
private fun updatedKey(pkg: String) = stringPreferencesKey("app_${pkg}_updated")

private fun storedPackages(prefs: Preferences): List<String> =
    prefs.asMap().keys.map { it.name }
        .filter { it.startsWith("app_") && it.endsWith("_enabled") }
        .map { it.removePrefix("app_").removeSuffix("_enabled") }

internal fun readNotificationApps(prefs: Preferences): List<NotificationApp> =
    storedPackages(prefs).map { pkg ->
        NotificationApp(
            packageName = pkg,
            appName = prefs[nameKey(pkg)] ?: pkg,
            isEnabled = prefs[enabledKey(pkg)] != false,
            isSystemApp = prefs[systemKey(pkg)] == true,
            lastUpdated = prefs[updatedKey(pkg)]?.toLongOrNull() ?: 0L
        )
    }.sortedBy { it.appName }

// Removes only the four keys of each stored package, so app_paused and other settings survive.
internal fun writeNotificationApps(prefs: MutablePreferences, apps: List<NotificationApp>) {
    storedPackages(prefs).forEach { pkg ->
        prefs.remove(enabledKey(pkg))
        prefs.remove(nameKey(pkg))
        prefs.remove(systemKey(pkg))
        prefs.remove(updatedKey(pkg))
    }
    apps.forEach { app ->
        prefs[enabledKey(app.packageName)] = app.isEnabled
        prefs[nameKey(app.packageName)] = app.appName
        prefs[systemKey(app.packageName)] = app.isSystemApp
        prefs[updatedKey(app.packageName)] = app.lastUpdated.toString()
    }
}

// Read-modify-write in one edit, so it is atomic against every other edit on this DataStore.
// `transform` runs inside the transaction: keep it pure and cheap (no PackageManager, no I/O).
// Returns the list that was written.
suspend fun DataStore<Preferences>.updateNotificationApps(
    transform: (List<NotificationApp>) -> List<NotificationApp>
): List<NotificationApp> {
    var written = emptyList<NotificationApp>()
    edit { prefs ->
        written = transform(readNotificationApps(prefs))
        writeNotificationApps(prefs, written)
    }
    return written
}

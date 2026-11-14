package com.petal.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "petal_launcher")

/** Persists the whole launcher state as one JSON document inside Jetpack DataStore. */
class LauncherStore(ctx: Context) {
    private val ds = ctx.applicationContext.dataStore
    private val key = stringPreferencesKey("state_json")

    private val compact = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val pretty = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    private fun decode(raw: String?): LauncherState =
        raw?.let { runCatching { compact.decodeFromString<LauncherState>(it).sanitized() }.getOrNull() }
            ?: LauncherState()

    val stateFlow: Flow<LauncherState> = ds.data.map { decode(it[key]) }

    suspend fun edit(transform: (LauncherState) -> LauncherState) {
        ds.edit { prefs ->
            val current = decode(prefs[key])
            val next = transform(current)
            if (next != current) prefs[key] = compact.encodeToString(next)
        }
    }

    suspend fun replace(state: LauncherState) {
        ds.edit { it[key] = compact.encodeToString(state.sanitized()) }
    }

    fun encodeBackup(state: LauncherState): String = pretty.encodeToString(state)

    /** Throws on invalid JSON. */
    fun parseBackup(text: String): LauncherState = compact.decodeFromString<LauncherState>(text).sanitized()
}

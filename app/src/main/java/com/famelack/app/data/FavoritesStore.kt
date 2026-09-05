package com.famelack.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.favoritesDataStore by preferencesDataStore("famelack_favorites")
private val KEY_FAVORITES = stringPreferencesKey("ids")

/**
 * Lightweight favorites: just store channel IDs as a comma-separated string.
 * Up to ~10k entries (~50KB). Sufficient for a curated personal list.
 */
class FavoritesStore(private val context: Context) {

    val ids: Flow<Set<String>> = context.favoritesDataStore.data.map { prefs ->
        val raw = prefs[KEY_FAVORITES].orEmpty()
        if (raw.isBlank()) emptySet() else raw.split(",").toSet()
    }

    suspend fun toggle(id: String) {
        context.favoritesDataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITES].orEmpty()
                .split(",")
                .filter { it.isNotBlank() }
                .toMutableSet()
            if (!current.add(id)) current.remove(id)
            prefs[KEY_FAVORITES] = current.joinToString(",")
        }
    }

    suspend fun add(id: String) {
        context.favoritesDataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITES].orEmpty()
                .split(",")
                .filter { it.isNotBlank() }
                .toMutableSet()
            current.add(id)
            prefs[KEY_FAVORITES] = current.joinToString(",")
        }
    }

    suspend fun remove(id: String) {
        context.favoritesDataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITES].orEmpty()
                .split(",")
                .filter { it.isNotBlank() }
                .toMutableSet()
            current.remove(id)
            prefs[KEY_FAVORITES] = current.joinToString(",")
        }
    }
}

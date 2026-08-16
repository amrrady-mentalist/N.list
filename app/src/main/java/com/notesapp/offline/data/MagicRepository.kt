package com.notesapp.offline.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists the whole MagicStore (effects, active effect, lock mode,
 * background/icon overrides) as one JSON file — the native equivalent of
 * the web app's `saveMagic()` writing the whole `magic` object to
 * localStorage on every change.
 *
 * Stored under a new filename ("magic_store.json") rather than reusing the
 * old single-effect model's "magic_effect.json" — the shapes are
 * incompatible, and this is still early testing data, so a clean start
 * beats a fragile migration.
 */
class MagicRepository(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file: File = File(context.filesDir, "magic_store.json")

    /** Directory copied-in images (lock background, wallpaper, icon overrides) live in. */
    val mediaDir: File = File(context.filesDir, "magic_media").apply { mkdirs() }

    suspend fun load(): MagicStore = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext MagicStore()
        runCatching {
            json.decodeFromString<MagicStore>(file.readText())
        }.getOrDefault(MagicStore())
    }

    suspend fun save(store: MagicStore) = withContext(Dispatchers.IO) {
        file.writeText(json.encodeToString(store))
    }

    // ---- Effect CRUD -------------------------------------------------
    // Each of these loads, mutates, and saves the whole store — matching
    // the web app's pattern of always operating on the single in-memory
    // `magic` object and calling saveMagic() after every change, rather
    // than fine-grained partial updates.

    suspend fun createEffect(): MagicEffect {
        val store = load()
        val effect = MagicEffect()
        save(store.copy(effects = listOf(effect) + store.effects))
        return effect
    }

    suspend fun updateEffect(effect: MagicEffect) {
        val store = load()
        val updated = store.effects.map { if (it.id == effect.id) effect else it }
        save(store.copy(effects = updated))
    }

    suspend fun deleteEffect(effectId: String) {
        val store = load()
        save(
            store.copy(
                effects = store.effects.filterNot { it.id == effectId },
                activeEffectId = if (store.activeEffectId == effectId) null else store.activeEffectId
            )
        )
    }

    suspend fun setActiveEffect(effectId: String?) {
        val store = load()
        save(store.copy(activeEffectId = effectId))
    }

    suspend fun setLockMode(mode: LockMode) {
        val store = load()
        save(store.copy(lockMode = mode))
    }
}

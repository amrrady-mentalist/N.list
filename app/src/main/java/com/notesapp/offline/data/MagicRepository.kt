package com.notesapp.offline.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val tempFile: File = File(context.filesDir, "magic_store.json.tmp")

    /** Directory copied-in images (lock background, wallpaper, icon overrides) live in. */
    val mediaDir: File = File(context.filesDir, "magic_media").apply { mkdirs() }

    /**
     * Serializes every read-modify-write against this file. Every method
     * below is a "load the whole store, mutate it, save the whole store
     * back" sequence — that used to run with zero coordination between
     * callers. If two of these ever overlapped even slightly (the lock
     * flow silently linking a note to an effect right as the settings
     * screen saved an unrelated toggle, for instance), two concurrent
     * file.writeText() calls on the same path could interleave and leave
     * a corrupted/truncated file. The next load() would then fail to
     * parse that and silently treat it as "nothing here" (see
     * loadLocked() below), and the very next save after that would
     * persist that emptiness permanently — which is exactly what wiped
     * everything out. This mutex makes every operation here fully
     * sequential so that race can't happen.
     */
    private val mutex = Mutex()

    suspend fun load(): MagicStore = withContext(Dispatchers.IO) {
        mutex.withLock { loadLocked() }
    }

    suspend fun save(store: MagicStore) = withContext(Dispatchers.IO) {
        mutex.withLock { saveLocked(store) }
    }

    // Un-locked internals — only ever called while [mutex] is already
    // held, so the CRUD helpers below can compose a load+mutate+save into
    // one single critical section instead of taking the lock twice (once
    // for their own load, once for their own save) with a gap in between
    // where another caller could still sneak in and race them.

    private fun loadLocked(): MagicStore {
        if (!file.exists()) return MagicStore()
        return runCatching {
            json.decodeFromString<MagicStore>(file.readText())
        }.getOrElse {
            // The file exists but failed to parse — genuinely corrupted,
            // not just "first launch". Keep the bad bytes around under a
            // different name instead of silently discarding them (in case
            // they're ever worth a manual look), and importantly: nothing
            // below touches that renamed copy, so it isn't at risk of
            // being overwritten by whatever gets saved next.
            runCatching {
                file.copyTo(File(file.parentFile, "magic_store.corrupt.json"), overwrite = true)
            }
            MagicStore()
        }
    }

    private fun saveLocked(store: MagicStore) {
        // Write to a temp file and rename it over the real one, rather
        // than writing directly into the real file. file.writeText() on
        // the real file truncates it to zero bytes immediately and then
        // fills it back in gradually — a process death mid-write (a
        // low-memory kill, a crash, just backgrounding the app at the
        // wrong instant while testing) can leave a half-written,
        // unparsable file behind. Writing the full new content to a
        // separate temp file first and only then renaming it over the
        // original means the real file is always either the complete old
        // version or the complete new version — never a partial one.
        tempFile.writeText(json.encodeToString(store))
        tempFile.renameTo(file)
    }

    // ---- Effect CRUD -------------------------------------------------
    // Each of these composes loadLocked()/saveLocked() under a single lock
    // acquisition — matching the web app's pattern of always operating on
    // the single in-memory `magic` object and calling saveMagic() after
    // every change, but now as one atomic unit so no other caller's
    // load/save can land in the middle of it.

    suspend fun createEffect(): MagicEffect = withContext(Dispatchers.IO) {
        mutex.withLock {
            val store = loadLocked()
            val effect = MagicEffect()
            saveLocked(store.copy(effects = listOf(effect) + store.effects))
            effect
        }
    }

    suspend fun updateEffect(effect: MagicEffect) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val store = loadLocked()
            val updated = store.effects.map { if (it.id == effect.id) effect else it }
            saveLocked(store.copy(effects = updated))
        }
    }

    suspend fun deleteEffect(effectId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val store = loadLocked()
            saveLocked(
                store.copy(
                    effects = store.effects.filterNot { it.id == effectId },
                    activeEffectId = if (store.activeEffectId == effectId) null else store.activeEffectId
                )
            )
        }
    }

    suspend fun setActiveEffect(effectId: String?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val store = loadLocked()
            saveLocked(store.copy(activeEffectId = effectId))
        }
    }

    suspend fun setLockMode(mode: LockMode) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val store = loadLocked()
            saveLocked(store.copy(lockMode = mode))
        }
    }
}


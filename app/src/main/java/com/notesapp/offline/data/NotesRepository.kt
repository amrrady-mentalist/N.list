package com.notesapp.offline.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Simple file-based storage, the native equivalent of the old app's
 * localStorage usage. All notes live in one JSON array on disk at
 * filesDir/notes.json — no database, no schema migrations, easy to inspect
 * or back up by just pulling the file.
 *
 * Every public function is a suspend function that hops onto Dispatchers.IO,
 * so it's safe to call directly from a ViewModel without blocking the UI.
 */
class NotesRepository(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file: File = File(context.filesDir, "notes.json")

    suspend fun loadAll(): List<Note> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            json.decodeFromString<List<Note>>(file.readText())
        }.getOrDefault(emptyList())
    }

    private suspend fun saveAll(notes: List<Note>) = withContext(Dispatchers.IO) {
        file.writeText(json.encodeToString(notes))
    }

    suspend fun upsert(note: Note) {
        val current = loadAll().toMutableList()
        val index = current.indexOfFirst { it.id == note.id }
        if (index >= 0) {
            current[index] = note
        } else {
            current.add(0, note)
        }
        saveAll(current)
    }

    suspend fun delete(noteId: String) {
        val current = loadAll().filterNot { it.id == noteId }
        saveAll(current)
    }

    /** Full overwrite — used by backup restore, which replaces everything
     *  on disk with exactly what's in the backup file rather than merging
     *  it with whatever notes already exist. */
    suspend fun replaceAll(notes: List<Note>) = saveAll(notes)
}

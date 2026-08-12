package com.notesapp.offline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notesapp.offline.data.Note
import com.notesapp.offline.data.NotesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotesViewModel(private val repo: NotesRepository) : ViewModel() {

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            _notes.value = repo.loadAll().sortedWith(
                compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt }
            )
            _loaded.value = true
        }
    }

    fun getNote(id: String): Note? = _notes.value.firstOrNull { it.id == id }

    fun save(note: Note) {
        viewModelScope.launch {
            repo.upsert(note.copy(updatedAt = System.currentTimeMillis()))
            refresh()
        }
    }

    fun delete(noteId: String) {
        viewModelScope.launch {
            repo.delete(noteId)
            refresh()
        }
    }

    fun togglePin(noteId: String) {
        val note = getNote(noteId) ?: return
        save(note.copy(pinned = !note.pinned))
    }
}

class NotesViewModelFactory(private val repo: NotesRepository) :
    androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return NotesViewModel(repo) as T
    }
}

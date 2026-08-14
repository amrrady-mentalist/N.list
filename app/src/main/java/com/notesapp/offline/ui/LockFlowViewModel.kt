package com.notesapp.offline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.notesapp.offline.data.ChecklistItem
import com.notesapp.offline.data.ForceListEngine
import com.notesapp.offline.data.MagicRepository
import com.notesapp.offline.data.Note
import com.notesapp.offline.data.NotesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LockScreenState { BLACKOUT, AMBIENT, PIN }

class LockFlowViewModel(
    private val notesRepo: NotesRepository,
    private val magicRepo: MagicRepository
) : ViewModel() {

    private val _screen = MutableStateFlow(LockScreenState.BLACKOUT)
    val screen: StateFlow<LockScreenState> = _screen.asStateFlow()

    private val _pinDigits = MutableStateFlow("")
    val pinDigits: StateFlow<String> = _pinDigits.asStateFlow()

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    fun onBlackoutDoubleTap() {
        _screen.value = LockScreenState.AMBIENT
    }

    fun onAmbientSwipeUp() {
        _screen.value = LockScreenState.PIN
    }

    fun onPinAbortDoubleTap() {
        _pinDigits.value = ""
        _screen.value = LockScreenState.BLACKOUT
    }

    /** Re-arms the flow for another performance without restarting the app. */
    fun reset() {
        _pinDigits.value = ""
        _screen.value = LockScreenState.BLACKOUT
        _unlocked.value = false
    }

    fun onPinKey(key: String) {
        when (key) {
            "delete" -> _pinDigits.value = _pinDigits.value.dropLast(1)
            "empty" -> Unit
            else -> {
                if (_pinDigits.value.length < 4) {
                    _pinDigits.value += key
                    if (_pinDigits.value.length == 4) resolvePin(_pinDigits.value)
                }
            }
        }
    }

    /**
     * The actual trick: ANY 4 digits resolve and unlock — there's no
     * "correct" PIN to fail on, matching the original app. The digits are
     * only used as the force-list's positional input.
     */
    private fun resolvePin(pin: String) {
        viewModelScope.launch {
            val effect = magicRepo.load()
            if (effect.items.any { it.isNotBlank() }) {
                val relevant = ForceListEngine.relevantDigits(effect.items, pin)
                val forced = ForceListEngine.buildForcedList(effect.items, effect.forceWord, relevant)

                val allNotes = notesRepo.loadAll()
                val existing = effect.linkedNoteId?.let { id -> allNotes.firstOrNull { it.id == id } }

                val checklist = forced.map { ChecklistItem(text = it, done = false) }
                val note = (existing ?: Note(magicEffectId = "active")).copy(
                    title = effect.title,
                    checklist = checklist,
                    pinned = true,
                    archived = false,
                    updatedAt = System.currentTimeMillis()
                )
                notesRepo.upsert(note)
                if (existing == null) {
                    magicRepo.save(effect.copy(linkedNoteId = note.id))
                }
            }
            _unlocked.value = true
        }
    }
}

class LockFlowViewModelFactory(
    private val notesRepo: NotesRepository,
    private val magicRepo: MagicRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return LockFlowViewModel(notesRepo, magicRepo) as T
    }
}

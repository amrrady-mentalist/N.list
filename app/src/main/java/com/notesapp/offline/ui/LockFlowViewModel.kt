package com.notesapp.offline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.notesapp.offline.data.ChecklistItem
import com.notesapp.offline.data.EffectType
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

    private val _lockBackgroundPath = MutableStateFlow<String?>(null)
    /** Path to the classic-lock background photo, if one's set in Magic
     *  Settings — read fresh every time the flow (re)starts via reset(). */
    val lockBackgroundPath: StateFlow<String?> = _lockBackgroundPath.asStateFlow()

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
        viewModelScope.launch {
            _lockBackgroundPath.value = magicRepo.load().lockBackgroundPath
        }
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
     * Direct port of the web app's resolvePin(): ANY 4 digits resolve and
     * unlock — there's no "correct" PIN to fail on. The digits are only
     * ever used as positional/lookup input for whichever effect is active.
     *
     * - No active effect: unlocks with no note created (matches the JS,
     *   which only acts `if(fx)`).
     * - LIST effect: same force-list math as before, now keyed off the
     *   active effect specifically rather than a single global effect.
     * - WORD effect: looks up the out whose code matches the PIN's last
     *   N digits (N = the longest configured out code, min 2 — mirrors
     *   the JS's codeLen expansion), substitutes the matched word (or a
     *   "🧐" placeholder if nothing matches) into the body wherever
     *   "$$$$" appears, and creates a fresh note every time — the web
     *   app never reuses/updates a previous note for word effects, only
     *   for list effects.
     */
    private fun resolvePin(pin: String) {
        viewModelScope.launch {
            val store = magicRepo.load()
            val fx = store.activeEffect

            if (fx != null) {
                val codeLen = when (fx.type) {
                    EffectType.LIST -> ForceListEngine.codeDigits(fx.items)
                    EffectType.WORD -> (fx.outs.maxOfOrNull { it.code.length } ?: 2).coerceAtLeast(2)
                }
                val lastDigits = if (pin.length >= codeLen) pin.takeLast(codeLen) else pin

                when (fx.type) {
                    EffectType.LIST -> {
                        val relevant = ForceListEngine.relevantDigits(fx.items, lastDigits)
                        val forced = ForceListEngine.buildForcedList(fx.items, fx.forceWord, relevant)

                        val allNotes = notesRepo.loadAll()
                        val existing = fx.linkedNoteId?.let { id -> allNotes.firstOrNull { it.id == id } }

                        val checklist = forced.map { ChecklistItem(text = it, done = false) }
                        val note = (existing ?: Note(magicEffectId = fx.id)).copy(
                            title = fx.title,
                            checklist = checklist,
                            pinned = true,
                            archived = false,
                            updatedAt = System.currentTimeMillis()
                        )
                        notesRepo.upsert(note)
                        if (existing == null) {
                            magicRepo.updateEffect(fx.copy(linkedNoteId = note.id))
                        }
                    }
                    EffectType.WORD -> {
                        val target = lastDigits.toIntOrNull()
                        val match = fx.outs.firstOrNull { it.code.isNotEmpty() && it.code.toIntOrNull() == target }
                        val word = if (match != null) match.word else "\uD83E\uDDD0" // 🧐 — matches the web app's fallback
                        val note = Note(
                            title = fx.title,
                            body = fx.body.replace("$$$$", word),
                            drawingPngBase64 = match?.drawingPngBase64,
                            pinned = true,
                            archived = false,
                            magicEffectId = fx.id
                        )
                        notesRepo.upsert(note)
                    }
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

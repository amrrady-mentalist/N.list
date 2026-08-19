package com.notesapp.offline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.notesapp.offline.data.EffectType
import com.notesapp.offline.data.ForceListEngine
import com.notesapp.offline.data.LockMode
import com.notesapp.offline.data.MagicRepository
import com.notesapp.offline.data.Note
import com.notesapp.offline.data.NotesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LockScreenState { BLACKOUT, AMBIENT, PIN, HOME_SCREEN }

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

    // ---- Home Screen disguise mode ----
    private val _hsWallpaperPath = MutableStateFlow<String?>(null)
    val hsWallpaperPath: StateFlow<String?> = _hsWallpaperPath.asStateFlow()

    private val _hsNotesIconPath = MutableStateFlow<String?>(null)
    val hsNotesIconPath: StateFlow<String?> = _hsNotesIconPath.asStateFlow()

    private val _hsIconOverrides = MutableStateFlow<Map<String, String>>(emptyMap())
    val hsIconOverrides: StateFlow<Map<String, String>> = _hsIconOverrides.asStateFlow()

    private val _hsNameOverrides = MutableStateFlow<Map<String, String>>(emptyMap())
    val hsNameOverrides: StateFlow<Map<String, String>> = _hsNameOverrides.asStateFlow()

    private val _hsWidgetProvider = MutableStateFlow<String?>(null)
    val hsWidgetProvider: StateFlow<String?> = _hsWidgetProvider.asStateFlow()

    private val _hsWidgetId = MutableStateFlow(-1)
    val hsWidgetId: StateFlow<Int> = _hsWidgetId.asStateFlow()

    private val _hsRequiredDigits = MutableStateFlow(2)
    /** How many swipe-pages the fake home screen needs — one digit per
     *  page, same rule the web app used: the longest configured Word-Force
     *  out code, or the List-Force's item-count digit width, minimum 2. */
    val hsRequiredDigits: StateFlow<Int> = _hsRequiredDigits.asStateFlow()

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

    /** The fake home screen's hidden "Lock" dock icon — double-tapping it
     *  bails out of the disguise entirely (no PIN resolved, no note
     *  touched) straight back to the real note list. Reuses the same
     *  `unlocked` signal the successful-trick path uses since both cases
     *  want the exact same navigation result. */
    fun abortHomeScreenFlow() {
        _unlocked.value = true
    }

    /** Called once the performer taps the disguised Notes icon on the fake
     *  home screen's final page — feeds the digits collected via swipes
     *  into the exact same resolvePin() the classic keypad uses. */
    fun resolveHomeScreenPin(pin: String) {
        resolvePin(pin)
    }

    /** Re-arms the flow for another performance without restarting the app. */
    fun reset() {
        _pinDigits.value = ""
        _screen.value = LockScreenState.BLACKOUT
        _unlocked.value = false
        viewModelScope.launch {
            val store = magicRepo.load()
            _lockBackgroundPath.value = store.lockBackgroundPath
            _hsWallpaperPath.value = store.homeWallpaperPath
            _hsNotesIconPath.value = store.notesIconPath
            _hsIconOverrides.value = store.appIconOverrides
            _hsNameOverrides.value = store.appNameOverrides
            _hsWidgetProvider.value = store.homeWidgetProvider
            _hsWidgetId.value = store.homeWidgetId

            val fx = store.activeEffect
            _hsRequiredDigits.value = when {
                fx == null -> 2
                fx.type == EffectType.LIST -> ForceListEngine.codeDigits(fx.items)
                else -> (fx.outs.maxOfOrNull { it.code.length } ?: 2).coerceAtLeast(2)
            }

            if (store.lockMode == LockMode.HOME_SCREEN) {
                _screen.value = LockScreenState.HOME_SCREEN
            }
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

                        // A numbered plain-text list ("1 - Item"), matching
                        // the web app's <ol><li> rendering — not an actual
                        // checkbox checklist, which reads as a to-do list
                        // rather than a forced sequence of items.
                        val numbered = forced.mapIndexed { i, item -> "${i + 1} - $item" }.joinToString("\n")
                        val note = (existing ?: Note(magicEffectId = fx.id)).copy(
                            title = fx.title,
                            body = numbered,
                            checklist = emptyList(),
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

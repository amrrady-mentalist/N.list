package com.notesapp.offline.data

import kotlinx.serialization.Serializable

/**
 * A single "list force" effect — the classic mentalism setup where a
 * spectator names/lands on an item, and entering a PIN afterward silently
 * reorders a note's list so the target item sits at the position the PIN
 * digits encode.
 *
 * This is a deliberate simplification of the original app, which supported
 * multiple named effects plus a second "word outs" effect type. One active
 * list effect covers the core trick; multiple saved effects can be added
 * later if needed.
 */
@Serializable
data class MagicEffect(
    val title: String = "",
    val forceWord: String = "",
    val items: List<String> = emptyList(),
    /** id of the Note this effect's force-list is mirrored into. */
    val linkedNoteId: String? = null
)

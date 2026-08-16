package com.notesapp.offline.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * One "code → word" outcome inside a Word-type effect — a direct port of
 * the web app's `out` object ({id, code, word, drawing}). [drawing] mirrors
 * the note-editor's own drawing storage: a base64 PNG, or null.
 */
@Serializable
data class EffectOut(
    val id: String = UUID.randomUUID().toString(),
    val code: String = "",
    val word: String = "",
    val drawingPngBase64: String? = null
)

@Serializable
enum class EffectType { WORD, LIST }

/**
 * A single magic effect. Mirrors the web app's effect object exactly:
 * - WORD effects reveal a note whose title/body are built from [body],
 *   substituting `$$$$` with whichever [outs] entry's code matches the
 *   PIN's last digits (falling back to a "🧐" placeholder word if none
 *   match — same as the web app).
 * - LIST effects reveal/refresh a checklist note built from [items], with
 *   [forceWord] silently reordered to the position the PIN digits encode
 *   (see ForceListEngine — that logic is unchanged from before).
 *
 * [linkedNoteId] is only meaningful for LIST effects — the web app keeps
 * one persistent note per list effect and rewrites its content in place
 * (so re-triggering doesn't spam duplicate notes). WORD effects create a
 * fresh note every time they're triggered, same as the web app.
 */
@Serializable
data class MagicEffect(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: EffectType = EffectType.WORD,
    // Word-type fields
    val title: String = "",
    val body: String = "",
    val outs: List<EffectOut> = emptyList(),
    // List-type fields
    val forceWord: String = "",
    val items: List<String> = emptyList(),
    val linkedNoteId: String? = null
)

@Serializable
enum class LockMode { CLASSIC, HOME_SCREEN }

/**
 * Everything the magic settings screen manages, persisted as one JSON blob
 * — mirrors the web app's single `magic` object (effects, activeEffectId,
 * lockMode) rather than the earlier single-effect model.
 */
@Serializable
data class MagicStore(
    val effects: List<MagicEffect> = emptyList(),
    val activeEffectId: String? = null,
    val lockMode: LockMode = LockMode.CLASSIC,
    /** Path to a copied-in background image file for the classic PIN lock screen. */
    val lockBackgroundPath: String? = null,
    /** Path to a copied-in wallpaper image file for the (future) home-screen disguise. */
    val homeWallpaperPath: String? = null,
    /** Path to a copied-in override icon for the notes app itself, in home-screen mode. */
    val notesIconPath: String? = null,
    /** Fake-app key (e.g. "phone", "messages") -> overridden icon file path. */
    val appIconOverrides: Map<String, String> = emptyMap()
) {
    val activeEffect: MagicEffect? get() = effects.firstOrNull { it.id == activeEffectId }
}

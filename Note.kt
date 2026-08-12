package com.notesapp.offline.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Core note model.
 *
 * Kept intentionally simple for the foundation phase — title + plain body text.
 * Checklist items, rich text spans, and drawing canvas data will be added in
 * later phases as additional optional fields (this class is designed to grow
 * without breaking the on-disk JSON format, since every new field should have
 * a default value).
 */
@Serializable
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val body: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false
)

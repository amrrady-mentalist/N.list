package com.notesapp.offline.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Core note model.
 *
 * Updated to include fields mapped from the web app's features (color, 
 * archived status, drawing data, and magic effect ID).
 */
@Serializable
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val body: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val color: String = "none",
    val archived: Boolean = false,
    val drawing: String? = null,
    val magicEffectId: String? = null
)

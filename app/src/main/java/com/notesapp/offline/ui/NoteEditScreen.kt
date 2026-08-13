package com.notesapp.offline.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notesapp.offline.data.ChecklistItem
import com.notesapp.offline.data.Note
import com.notesapp.offline.data.NoteColor
import com.notesapp.offline.ui.theme.toComposeColor
import java.util.UUID

private fun decodeBase64ToBitmap(base64: String) = runCatching {
    val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

/**
 * Handles create (noteId == null), plain-text edit, checklist edit, and the
 * drawing entry point, all in one screen — mirrors the original app's
 * single editor sheet that adapts to whatever the note contains.
 * Autosaves on every change, no explicit save button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    viewModel: NotesViewModel,
    noteId: String?,
    onBack: () -> Unit,
    onOpenDrawing: (String) -> Unit
) {
    val existing = remember(noteId) { noteId?.let { viewModel.getNote(it) } }
    var current by remember(noteId) { mutableStateOf(existing ?: Note()) }
    var showColorPicker by remember { mutableStateOf(false) }

    fun persist(note: Note) {
        current = note
        if (note.title.isBlank() && note.body.isBlank() && note.checklist.isEmpty() && note.drawingPngBase64 == null) {
            return // don't write a completely empty note to disk
        }
        viewModel.save(note)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New note" else "Edit note") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showColorPicker = !showColorPicker
                    }) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    if (current.color == NoteColor.NONE) Color.Gray.copy(alpha = 0.4f)
                                    else current.color.toComposeColor()
                                )
                        )
                    }
                    if (existing != null) {
                        Text(
                            text = if (current.archived) "Unarchive" else "Archive",
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .padding(horizontal = 10.dp)
                                .clickable {
                                    viewModel.toggleArchive(current.id)
                                    onBack()
                                }
                        )
                        IconButton(onClick = {
                            viewModel.delete(current.id)
                            onBack()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (showColorPicker) {
                ColorPickerRow(
                    selected = current.color,
                    onSelect = {
                        showColorPicker = false
                        persist(current.copy(color = it))
                    }
                )
            }

            OutlinedTextField(
                value = current.title,
                onValueChange = { persist(current.copy(title = it)) },
                placeholder = { Text("Title") },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default,
                colors = TextFieldDefaults.colors(),
                modifier = Modifier.fillMaxWidth()
            )

            // Drawing thumbnail slot, if this note has one.
            current.drawingPngBase64?.let { b64 ->
                val bmp = remember(b64) { decodeBase64ToBitmap(b64) }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Drawing",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onOpenDrawing(current.id) }
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (current.isChecklist) "+ Checklist item" else "+ Checklist",
                    modifier = Modifier.clickable {
                        if (current.checklist.isEmpty()) {
                            persist(current.copy(checklist = listOf(ChecklistItem())))
                        } else {
                            persist(current.copy(checklist = current.checklist + ChecklistItem()))
                        }
                    }
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable {
                        // Persist unconditionally (bypassing persist()'s empty-note
                        // guard) so a stable note id exists for the drawing screen
                        // to save back into, even for a brand-new blank note.
                        viewModel.save(current)
                        onOpenDrawing(current.id)
                    }
                ) {
                    Text("✎", fontSize = 16.sp)
                    Text("Sketch", modifier = Modifier.padding(start = 4.dp))
                }
            }

            if (current.isChecklist) {
                ChecklistEditor(
                    items = current.checklist,
                    onChange = { persist(current.copy(checklist = it)) }
                )
            } else {
                TextField(
                    value = current.body,
                    onValueChange = { persist(current.copy(body = it)) },
                    placeholder = { Text("Start writing...") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun ColorPickerRow(selected: NoteColor, onSelect: (NoteColor) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        NoteColor.entries.forEach { c ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (c == NoteColor.NONE) Color.Gray.copy(alpha = 0.3f) else c.toComposeColor())
                    .clickable { onSelect(c) }
            )
        }
    }
}

@Composable
private fun ChecklistEditor(items: List<ChecklistItem>, onChange: (List<ChecklistItem>) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 4.dp)) {
        items(items, key = { it.id }) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (item.done) Color(0xFF4FE8C4) else Color.Transparent)
                        .clickable {
                            onChange(items.map { if (it.id == item.id) it.copy(done = !it.done) else it })
                        }
                )
                TextField(
                    value = item.text,
                    onValueChange = { text ->
                        onChange(items.map { if (it.id == item.id) it.copy(text = text) else it })
                    },
                    placeholder = { Text("List item") },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                )
                IconButton(onClick = { onChange(items.filterNot { it.id == item.id }) }) {
                    Text("×", fontSize = 20.sp)
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clickable { onChange(items + ChecklistItem()) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Add item", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

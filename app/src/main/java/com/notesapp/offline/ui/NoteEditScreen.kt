package com.notesapp.offline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.notesapp.offline.data.Note

// Matching your web app colors
val NoteColors = mapOf(
    "none" to Color(0xFF1E1E1E), // A dark placeholder for 'none'
    "violet" to Color(0xFF8B7CFF),
    "rose" to Color(0xFFFF7AA2),
    "teal" to Color(0xFF4FE8C4),
    "amber" to Color(0xFFFFC35A)
)

@Composable
fun ColorPickerRow(
    selectedColorName: String,
    onColorSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        NoteColors.forEach { (colorName, colorValue) ->
            val isSelected = selectedColorName == colorName
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colorValue)
                    .clickable { onColorSelected(colorName) }
                    .then(
                        if (isSelected) Modifier.border(2.dp, Color.White, CircleShape)
                        else Modifier
                    )
            )
        }
    }
}

/**
 * Handles both "create new" (noteId == null) and "edit existing" in one
 * screen. Saves happen on every field change (debounced by simply writing
 * the whole note back to the ViewModel) so there's no explicit save button —
 * matches the old web app's autosave-on-type behavior.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    viewModel: NotesViewModel,
    noteId: String?,
    onBack: () -> Unit
) {
    val existing = remember(noteId) { noteId?.let { viewModel.getNote(it) } }
    var current by remember(noteId) { mutableStateOf(existing ?: Note()) }
    var title by remember(noteId) { mutableStateOf(existing?.title ?: "") }
    var body by remember(noteId) { mutableStateOf(existing?.body ?: "") }
    var colorName by remember(noteId) { mutableStateOf(existing?.color ?: "none") }

    fun persist(newTitle: String = title, newBody: String = body, newColor: String = colorName) {
        title = newTitle
        body = newBody
        colorName = newColor
        
        // Don't write completely empty notes to disk.
        if (newTitle.isBlank() && newBody.isBlank()) return
        
        current = current.copy(title = newTitle, body = newBody, color = newColor)
        viewModel.save(current)
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
                    if (existing != null) {
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
            OutlinedTextField(
                value = title,
                onValueChange = { persist(newTitle = it) },
                placeholder = { Text("Title") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = TextFieldDefaults.colors(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = body,
                onValueChange = { persist(newBody = it) },
                placeholder = { Text("Start writing...") },
                colors = TextFieldDefaults.colors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 12.dp)
            )
            
            ColorPickerRow(
                selectedColorName = colorName,
                onColorSelected = { selected -> persist(newColor = selected) }
            )
        }
    }
}

package com.notesapp.offline.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notesapp.offline.data.Note

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NotesListScreen(
    viewModel: NotesViewModel,
    onOpenNote: (String?) -> Unit
) {
    val notes by viewModel.notes.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Notes") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenNote(null) }) {
                Icon(Icons.Filled.Add, contentDescription = "New note")
            }
        }
    ) { padding ->
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No notes yet — tap + to create one")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(notes, key = { it.id }) { note ->
                NoteCard(
                    note = note,
                    onClick = { onOpenNote(note.id) },
                    onTogglePin = { viewModel.togglePin(note.id) },
                    onDelete = { viewModel.delete(note.id) }
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onDelete)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = note.title.ifBlank { "Untitled" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 24.dp)
                )
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = if (note.pinned) "Unpin" else "Pin",
                        tint = if (note.pinned)
                            androidx.compose.material3.MaterialTheme.colorScheme.primary
                        else
                            androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (note.body.isNotBlank()) {
                Text(
                    text = note.body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
package com.notesapp.offline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.notesapp.offline.data.NotesRepository
import com.notesapp.offline.ui.NoteEditScreen
import com.notesapp.offline.ui.NotesListScreen
import com.notesapp.offline.ui.NotesViewModel
import com.notesapp.offline.ui.NotesViewModelFactory
import com.notesapp.offline.ui.theme.NotesNativeTheme

/**
 * Foundation phase: a single Activity hosting Compose screens. Navigation is
 * deliberately just a mutableState<Screen> instead of Navigation-Compose —
 * there are only two screens right now, and this keeps the dependency graph
 * small while we're still validating the core CRUD/storage layer. Swapping
 * in a proper nav graph later (for the lock-screen flow, tabs, etc.) is a
 * contained change limited to this file.
 */
sealed class Screen {
    data object List : Screen()
    data class Edit(val noteId: String?) : Screen()
}

class MainActivity : ComponentActivity() {

    private val viewModel: NotesViewModel by viewModels {
        NotesViewModelFactory(NotesRepository(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NotesNativeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NotesApp(viewModel)
                }
            }
        }
    }
}

@Composable
private fun NotesApp(viewModel: NotesViewModel) {
    var screen by remember { mutableStateOf<Screen>(Screen.List) }

    BackHandler(enabled = screen is Screen.Edit) {
        screen = Screen.List
    }

    when (val s = screen) {
        is Screen.List -> NotesListScreen(
            viewModel = viewModel,
            onOpenNote = { id -> screen = Screen.Edit(id) }
        )
        is Screen.Edit -> NoteEditScreen(
            viewModel = viewModel,
            noteId = s.noteId,
            onBack = { screen = Screen.List }
        )
    }
}

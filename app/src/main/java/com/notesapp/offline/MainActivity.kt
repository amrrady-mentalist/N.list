package com.notesapp.offline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.notesapp.offline.data.MagicRepository
import com.notesapp.offline.data.NotesRepository
import com.notesapp.offline.ui.DrawingScreen
import com.notesapp.offline.ui.LockFlowHost
import com.notesapp.offline.ui.LockFlowViewModel
import com.notesapp.offline.ui.LockFlowViewModelFactory
import com.notesapp.offline.ui.MagicSettingsScreen
import com.notesapp.offline.ui.NoteEditScreen
import com.notesapp.offline.ui.NotesListScreen
import com.notesapp.offline.ui.NotesViewModel
import com.notesapp.offline.ui.NotesViewModelFactory
import com.notesapp.offline.ui.theme.NotesNativeTheme

/**
 * Screen state machine for the whole app. Lock is always first — it's the
 * concealment layer the app opens into. Only after LockFlowViewModel reports
 * `unlocked` does navigation move to the real notes UI.
 *
 * Still a plain mutableState<Screen> rather than Navigation-Compose, same
 * reasoning as the foundation phase: the graph is simple and linear enough
 * that a real nav library would add ceremony without adding safety here.
 */
sealed class Screen {
    data object Lock : Screen()
    data object List : Screen()
    data class Edit(val noteId: String?) : Screen()
    data class Drawing(val noteId: String) : Screen()
    data object MagicSettings : Screen()
}

class MainActivity : ComponentActivity() {

    private val notesRepo by lazy { NotesRepository(applicationContext) }
    private val magicRepo by lazy { MagicRepository(applicationContext) }

    private val notesViewModel: NotesViewModel by viewModels {
        NotesViewModelFactory(notesRepo)
    }
    private val lockFlowViewModel: LockFlowViewModel by viewModels {
        LockFlowViewModelFactory(notesRepo, magicRepo)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NotesNativeTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NotesApp(notesViewModel, lockFlowViewModel, magicRepo)
                }
            }
        }
    }
}

@Composable
private fun NotesApp(
    notesViewModel: NotesViewModel,
    lockFlowViewModel: LockFlowViewModel,
    magicRepo: MagicRepository
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Lock) }
    val unlocked by lockFlowViewModel.unlocked.collectAsState()

    if (screen is Screen.Lock) {
        if (unlocked) {
            screen = Screen.List
        } else {
            LockFlowHost(lockFlowViewModel)
            return
        }
    }

    BackHandler(enabled = screen !is Screen.List && screen !is Screen.Lock) {
        screen = Screen.List
    }

    when (val s = screen) {
        is Screen.Lock -> Unit // handled above
        is Screen.List -> NotesListScreen(
            viewModel = notesViewModel,
            onOpenNote = { id -> screen = Screen.Edit(id) },
            onOpenMagicSettings = { screen = Screen.MagicSettings }
        )
        is Screen.Edit -> NoteEditScreen(
            viewModel = notesViewModel,
            noteId = s.noteId,
            onBack = { screen = Screen.List },
            onOpenDrawing = { noteId -> screen = Screen.Drawing(noteId) }
        )
        is Screen.Drawing -> {
            val note = notesViewModel.getNote(s.noteId)
            DrawingScreen(
                initialPngBase64 = note?.drawingPngBase64,
                onSave = { base64 ->
                    notesViewModel.saveDrawing(s.noteId, base64)
                    screen = Screen.Edit(s.noteId)
                },
                onBack = { screen = Screen.Edit(s.noteId) }
            )
        }
        is Screen.MagicSettings -> MagicSettingsScreen(
            repo = magicRepo,
            onBack = { screen = Screen.List }
        )
    }
}

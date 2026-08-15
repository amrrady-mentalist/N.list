package com.notesapp.offline

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.notesapp.offline.data.MagicRepository
import com.notesapp.offline.data.NotesRepository
import com.notesapp.offline.data.ThemeMode
import com.notesapp.offline.data.ThemeRepository
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
import com.notesapp.offline.ui.theme.resolveDarkTheme
import kotlinx.coroutines.launch

/**
 * Screen state machine for the whole app. Opens straight to List — the
 * lock/blackout flow is no longer the cold-start screen; it's entered on
 * demand by double-tapping the "Notes" title, and LockFlowViewModel.reset()
 * re-arms it each time so it can be used more than once per app session.
 *
 * Still a plain mutableState<Screen> rather than Navigation-Compose, same
 * reasoning as earlier phases: the graph is simple and linear enough that
 * a real nav library would add ceremony without adding safety here.
 */
sealed class Screen {
    data object List : Screen()
    data object Lock : Screen()
    data class Edit(val noteId: String?) : Screen()
    data class Drawing(val noteId: String) : Screen()
    data object MagicSettings : Screen()
}

/** Screens that show the (transparent) status bar; everything else goes immersive. */
private fun Screen.showsSystemBars(): Boolean =
    this is Screen.List || this is Screen.Edit || this is Screen.MagicSettings || this is Screen.Drawing

class MainActivity : ComponentActivity() {

    private val notesRepo by lazy { NotesRepository(applicationContext) }
    private val magicRepo by lazy { MagicRepository(applicationContext) }
    private val themeRepo by lazy { ThemeRepository(applicationContext) }

    private val notesViewModel: NotesViewModel by viewModels {
        NotesViewModelFactory(notesRepo)
    }
    private val lockFlowViewModel: LockFlowViewModel by viewModels {
        LockFlowViewModelFactory(notesRepo, magicRepo)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            var themeMode by remember { mutableStateOf(ThemeMode.DARK) }
            LaunchedEffect(Unit) { themeMode = themeRepo.load() }

            val isDark = resolveDarkTheme(themeMode)
            NotesNativeTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NotesApp(
                        notesViewModel = notesViewModel,
                        lockFlowViewModel = lockFlowViewModel,
                        magicRepo = magicRepo,
                        isDarkTheme = isDark,
                        onToggleTheme = {
                            themeMode = if (isDark) ThemeMode.LIGHT else ThemeMode.DARK
                            lifecycleScope.launch { themeRepo.save(themeMode) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesApp(
    notesViewModel: NotesViewModel,
    lockFlowViewModel: LockFlowViewModel,
    magicRepo: MagicRepository,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    var screen by remember { mutableStateOf<Screen>(Screen.List) }
    var editVersion by remember { mutableStateOf(0) }
    val unlocked by lockFlowViewModel.unlocked.collectAsState()

    // Immersive mode everywhere except the "real app chrome" screens.
    val view = LocalView.current
    LaunchedEffect(screen, isDarkTheme) {
        val activity = view.context as? Activity ?: return@LaunchedEffect
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, view)
        if (screen.showsSystemBars()) {
            controller.show(WindowInsetsCompat.Type.statusBars())
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            controller.isAppearanceLightStatusBars = !isDarkTheme
        } else {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
    }

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
            isDarkTheme = isDarkTheme,
            onOpenNote = { id -> screen = Screen.Edit(id) },
            onOpenMagicSettings = { screen = Screen.MagicSettings },
            onToggleTheme = onToggleTheme,
            onEnterLockFlow = {
                lockFlowViewModel.reset()
                screen = Screen.Lock
            }
        )
        is Screen.Edit -> key(s.noteId, editVersion) {
            NoteEditScreen(
                viewModel = notesViewModel,
                noteId = s.noteId,
                isDarkTheme = isDarkTheme,
                onBack = { screen = Screen.List },
                onOpenDrawing = { noteId -> screen = Screen.Drawing(noteId) }
            )
        }
        is Screen.Drawing -> {
            val note = notesViewModel.getNote(s.noteId)
            DrawingScreen(
                initialPngBase64 = note?.drawingPngBase64,
                isDarkTheme = isDarkTheme,
                onSave = { base64 ->
                    notesViewModel.saveDrawing(s.noteId, base64)
                    editVersion++ // force NoteEditScreen to re-read the fresh note on return
                    screen = Screen.Edit(s.noteId)
                },
                onBack = {
                    editVersion++
                    screen = Screen.Edit(s.noteId)
                }
            )
        }
        is Screen.MagicSettings -> MagicSettingsScreen(
            repo = magicRepo,
            onBack = { screen = Screen.List }
        )
    }
}

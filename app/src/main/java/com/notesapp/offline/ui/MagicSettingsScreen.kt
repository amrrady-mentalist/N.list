package com.notesapp.offline.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notesapp.offline.data.MagicEffect
import com.notesapp.offline.data.MagicRepository
import kotlinx.coroutines.launch

/**
 * Configures the single active force-list effect: a title (shown as the
 * revealed note's title), the list of items presented to the spectator,
 * and the word/item that should end up forced into position when the PIN
 * digits are entered during the lock flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagicSettingsScreen(
    repo: MagicRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var effect by remember { mutableStateOf(MagicEffect()) }
    var itemsText by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loadedEffect = repo.load()
        effect = loadedEffect
        itemsText = loadedEffect.items.joinToString("\n")
        loaded = true
    }

    fun persist(updated: MagicEffect) {
        effect = updated
        scope.launch { repo.save(updated) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Magic settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (!loaded) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Note title", modifier = Modifier.padding(bottom = 4.dp))
            OutlinedTextField(
                value = effect.title,
                onValueChange = { persist(effect.copy(title = it)) },
                placeholder = { Text("e.g. \"Grocery List\"") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "Force word",
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
            Text(
                "The exact item from the list below that gets moved to the position the PIN digits encode.",
                modifier = Modifier.padding(bottom = 6.dp)
            )
            OutlinedTextField(
                value = effect.forceWord,
                onValueChange = { persist(effect.copy(forceWord = it)) },
                placeholder = { Text("Must match a line below exactly") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "Items (one per line)",
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
            OutlinedTextField(
                value = itemsText,
                onValueChange = { text ->
                    itemsText = text
                    persist(effect.copy(items = text.split("\n")))
                },
                placeholder = { Text("Apple\nBanana\nCherry\n...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )

            val digits = com.notesapp.offline.data.ForceListEngine.codeDigits(effect.items)
            Text(
                "Reads the last $digits digits of the entered PIN. Any 4-digit PIN always unlocks — the digits just decide where the force word lands.",
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

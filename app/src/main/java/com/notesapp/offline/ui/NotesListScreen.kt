package com.notesapp.offline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notesapp.offline.data.Note
import com.notesapp.offline.data.NoteColor
import com.notesapp.offline.ui.theme.GlassRadius
import com.notesapp.offline.ui.theme.glassPanel
import com.notesapp.offline.ui.theme.richTextPreview
import com.notesapp.offline.ui.theme.toComposeColor

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NotesListScreen(
    viewModel: NotesViewModel,
    isDarkTheme: Boolean,
    onOpenNote: (String?) -> Unit,
    onOpenMagicSettings: () -> Unit,
    onToggleTheme: () -> Unit,
    onEnterLockFlow: () -> Unit
) {
    val notes by viewModel.notes.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val query by viewModel.query.collectAsState()

    val bgColor = if (isDarkTheme) Color.Black else Color.White
    val fgColor = if (isDarkTheme) Color.White else Color.Black

    // Hidden settings entry: typing "magic" in the search bar opens Magic
    // Settings and clears the query, instead of a visible gear icon.
    LaunchedEffect(query) {
        if (query.trim().equals("magic", ignoreCase = true)) {
            viewModel.setQuery("")
            onOpenMagicSettings()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Header — double-tap "Notes" to enter the lock/blackout flow,
            // the performer's way in without closing and reopening the app.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Notes",
                    color = fgColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 34.sp,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { onEnterLockFlow() })
                    }
                )
                IconButton(onClick = onToggleTheme) {
                    ThemeToggleIcon(isDarkTheme = isDarkTheme, fgColor = fgColor, bgColor = bgColor)
                }
            }

            // Search bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .glassPanel(radius = GlassRadius.md, tint = fgColor)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (query.isEmpty()) {
                        Text("Search notes", color = fgColor.copy(alpha = 0.34f), fontSize = 15.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = fgColor, fontSize = 15.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(fgColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Filter chips
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(NoteFilter.entries.toList()) { f ->
                    FilterChip(
                        label = f.name.lowercase().replaceFirstChar { it.uppercase() },
                        active = filter == f,
                        fgColor = fgColor,
                        onClick = { viewModel.setFilter(f) }
                    )
                }
            }

            if (notes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No notes yet — tap + to create one", color = fgColor.copy(alpha = 0.5f))
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp, 4.dp, 14.dp, 110.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            fgColor = fgColor,
                            onClick = { onOpenNote(note.id) },
                            onTogglePin = { viewModel.togglePin(note.id) },
                            onToggleArchive = { viewModel.toggleArchive(note.id) },
                            onSetColor = { viewModel.setColor(note.id, it) },
                            onDelete = { viewModel.delete(note.id) }
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = { onOpenNote(null) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(60.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "New note", tint = Color.White)
        }
    }
}

/** Hand-drawn sun/moon so it reads as a real icon rather than an emoji glyph. */
@Composable
private fun ThemeToggleIcon(isDarkTheme: Boolean, fgColor: Color, bgColor: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val radius = size.minDimension / 2.8f
        val center = Offset(size.width / 2f, size.height / 2f)
        if (isDarkTheme) {
            // Sun: circle + 8 short rays
            drawCircle(color = fgColor, radius = radius, center = center)
            val rayStart = radius * 1.35f
            val rayEnd = radius * 1.9f
            for (i in 0 until 8) {
                val angle = (i * 45f) * (Math.PI / 180f)
                val dx = kotlin.math.cos(angle).toFloat()
                val dy = kotlin.math.sin(angle).toFloat()
                drawLine(
                    color = fgColor,
                    start = Offset(center.x + dx * rayStart, center.y + dy * rayStart),
                    end = Offset(center.x + dx * rayEnd, center.y + dy * rayEnd),
                    strokeWidth = 2.dp.toPx()
                )
            }
        } else {
            // Moon: full circle, then an offset circle in the background
            // color "bites" out a crescent.
            drawCircle(color = fgColor, radius = radius, center = center)
            drawCircle(
                color = bgColor,
                radius = radius * 0.85f,
                center = Offset(center.x + radius * 0.55f, center.y - radius * 0.35f)
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FilterChip(label: String, active: Boolean, fgColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (active) fgColor.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (active) fgColor else fgColor.copy(alpha = 0.56f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: Note,
    fgColor: Color,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onSetColor: (NoteColor) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel(radius = GlassRadius.md, tint = fgColor)
                .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
                .padding(14.dp)
        ) {
            if (note.color != NoteColor.NONE) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(note.color.toComposeColor())
                )
            }

            note.drawingPngBase64?.let { b64 ->
                val bmp = remember(b64) { decodeThumb(b64) }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.title.ifBlank { "Untitled" },
                    color = fgColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onTogglePin, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = if (note.pinned) "Unpin" else "Pin",
                        tint = if (note.pinned) fgColor else fgColor.copy(alpha = 0.25f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (note.isChecklist) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    note.checklist.take(5).forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(13.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (item.done) Color(0xFF4FE8C4) else fgColor.copy(alpha = 0.15f)
                                    )
                            )
                            Text(
                                text = item.text,
                                color = fgColor.copy(alpha = if (item.done) 0.4f else 0.75f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }
            } else if (note.body.isNotBlank()) {
                Text(
                    text = richTextPreview(note.body, note.styleRuns),
                    color = fgColor.copy(alpha = 0.56f),
                    fontSize = 13.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(if (note.pinned) "Unpin" else "Pin") },
                onClick = { onTogglePin(); showMenu = false }
            )
            DropdownMenuItem(
                text = { Text(if (note.archived) "Unarchive" else "Archive") },
                onClick = { onToggleArchive(); showMenu = false }
            )
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NoteColor.entries.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (c == NoteColor.NONE) Color.Gray.copy(alpha = 0.3f) else c.toComposeColor())
                            .combinedClickable(onClick = { onSetColor(c); showMenu = false })
                    )
                }
            }
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = { onDelete(); showMenu = false }
            )
        }
    }
}

private fun decodeThumb(base64: String) = runCatching {
    val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

package com.notesapp.offline.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.notesapp.offline.data.Note
import com.notesapp.offline.data.NoteColor
import com.notesapp.offline.ui.theme.AccentA
import com.notesapp.offline.ui.theme.Danger
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

    var isSearchExpanded by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(true) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    val bgColor = if (isDarkTheme) Color.Black else Color.White
    val fgColor = if (isDarkTheme) Color.White else Color.Black

    // Hidden settings entry: typing "magic" in the search bar opens Magic
    // Settings and clears the query, instead of a visible gear icon.
    LaunchedEffect(query) {
        if (query.trim().equals("magic", ignoreCase = true)) {
            viewModel.setQuery("")
            isSearchExpanded = false
            onOpenMagicSettings()
        }
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            searchFocusRequester.requestFocus()
        }
    }

    val noteCountText = "${notes.size} ${if (notes.size == 1) "note" else "notes"}"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Top action buttons row (Search and 3-dots Menu)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    isSearchExpanded = !isSearchExpanded
                    if (!isSearchExpanded) viewModel.setQuery("")
                }) {
                    Icon(
                        imageVector = if (isSearchExpanded) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = fgColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box {
                    IconButton(onClick = { showOptionsMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More options",
                            tint = fgColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false },
                        modifier = Modifier
                            .background(if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFF7F7F7))
                            .border(1.dp, fgColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (isDarkTheme) "Light Theme" else "Dark Theme", color = fgColor, fontSize = 14.sp) },
                            onClick = {
                                showOptionsMenu = false
                                onToggleTheme()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isGridView) "List View" else "Grid View", color = fgColor, fontSize = 14.sp) },
                            onClick = {
                                isGridView = !isGridView
                                showOptionsMenu = false
                            }
                        )
                    }
                }
            }

            // Main Title & Note Count Header
            // Double-tap on "Notes" still triggers the performer lock flow!
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Notes",
                    color = fgColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 38.sp,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { onEnterLockFlow() })
                    }
                )
                Text(
                    text = noteCountText,
                    color = fgColor.copy(alpha = 0.45f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )
            }

            // Inline Search Bar (when expanded)
            AnimatedVisibility(visible = isSearchExpanded, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                        .glassPanel(radius = GlassRadius.md, tint = fgColor)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (query.isEmpty()) {
                            Text("Search notes...", color = fgColor.copy(alpha = 0.34f), fontSize = 15.sp)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = viewModel::setQuery,
                            singleLine = true,
                            textStyle = TextStyle(color = fgColor, fontSize = 15.sp),
                            cursorBrush = SolidColor(fgColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester)
                        )
                    }
                }
            }

            // Categories / Filter horizontal bar
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp),
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Layout Toggle Pill (Icon)
                item {
                    val pillBg = if (isDarkTheme) Color(0xFF262626) else Color(0xFFE8E8E8)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(pillBg)
                            .clickable { isGridView = !isGridView }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(17.dp, 16.dp)) {
                            if (isGridView) {
                                // Split / Notebook icon
                                val w = size.width
                                val h = size.height
                                drawRoundRect(
                                    color = fgColor.copy(alpha = 0.9f),
                                    topLeft = Offset(0f, 0f),
                                    size = androidx.compose.ui.geometry.Size(w, h),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx())
                                )
                                drawLine(
                                    color = fgColor.copy(alpha = 0.9f),
                                    start = Offset(w * 0.36f, 0f),
                                    end = Offset(w * 0.36f, h),
                                    strokeWidth = 1.6.dp.toPx()
                                )
                            } else {
                                // Grid icon
                                val w = size.width
                                val h = size.height
                                drawRoundRect(
                                    color = fgColor.copy(alpha = 0.9f),
                                    topLeft = Offset(0f, 0f),
                                    size = androidx.compose.ui.geometry.Size(w, h),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx())
                                )
                                drawLine(
                                    color = fgColor.copy(alpha = 0.9f),
                                    start = Offset(w / 2f, 0f),
                                    end = Offset(w / 2f, h),
                                    strokeWidth = 1.6.dp.toPx()
                                )
                                drawLine(
                                    color = fgColor.copy(alpha = 0.9f),
                                    start = Offset(0f, h / 2f),
                                    end = Offset(w, h / 2f),
                                    strokeWidth = 1.6.dp.toPx()
                                )
                            }
                        }
                    }
                }

                // Filter items styled as modern rounded rectangle pills
                val filterItems = listOf(
                    NoteFilter.ALL to "All notes",
                    NoteFilter.PINNED to "Favorites",
                    NoteFilter.CHECKLISTS to "Checklists",
                    NoteFilter.DRAWINGS to "Drawings",
                    NoteFilter.ARCHIVED to "Archived"
                )

                items(filterItems) { (f, label) ->
                    val active = filter == f
                    val pillBg = if (active) {
                        if (isDarkTheme) Color(0xFF383838) else Color(0xFFD6D6D6)
                    } else {
                        if (isDarkTheme) Color(0xFF262626) else Color(0xFFE8E8E8)
                    }
                    val textColor = if (active) fgColor else fgColor.copy(alpha = 0.65f)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(pillBg)
                            .clickable { viewModel.setFilter(f) }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = label,
                            color = textColor,
                            fontSize = 14.5.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }

            if (notes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No notes yet — tap + to create one", color = fgColor.copy(alpha = 0.45f))
                }
            } else if (isGridView) {
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
                            bgColor = bgColor,
                            onClick = { onOpenNote(note.id) },
                            onTogglePin = { viewModel.togglePin(note.id) },
                            onToggleArchive = { viewModel.toggleArchive(note.id) },
                            onDelete = { viewModel.delete(note.id) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp, 4.dp, 14.dp, 110.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            fgColor = fgColor,
                            bgColor = bgColor,
                            onClick = { onOpenNote(note.id) },
                            onTogglePin = { viewModel.togglePin(note.id) },
                            onToggleArchive = { viewModel.toggleArchive(note.id) },
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
    bgColor: Color,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val cardBg = if (bgColor == Color.Black) Color(0xFF1E1E22) else Color(0xFFF2F2F7)

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
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
                                    .clip(CircleShape)
                                    .background(
                                        if (item.done) AccentA else fgColor.copy(alpha = 0.15f)
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

        if (showMenu) {
            Popup(
                alignment = Alignment.Center,
                onDismissRequest = { showMenu = false },
                properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)
            ) {
                // A solid, opaque surface rather than the card-style glassPanel:
                // this floats over arbitrary note-card content, so a translucent
                // fill just reads as noise. Width is capped instead of stretching
                // edge-to-edge, which is what made it feel like a full-screen sheet.
                Column(
                    modifier = Modifier
                        .width(200.dp)
                        .clip(RoundedCornerShape(GlassRadius.md))
                        .background(bgColor)
                        .border(1.dp, fgColor.copy(alpha = 0.14f), RoundedCornerShape(GlassRadius.md))
                        .padding(vertical = 6.dp)
                ) {
                    GlassMenuItem(if (note.pinned) "Unpin" else "Pin", fgColor) {
                        onTogglePin(); showMenu = false
                    }
                    GlassMenuItem(if (note.archived) "Unarchive" else "Archive", fgColor) {
                        onToggleArchive(); showMenu = false
                    }
                    GlassMenuItem("Delete", Danger) {
                        onDelete(); showMenu = false
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassMenuItem(label: String, color: Color, onClick: () -> Unit) {
    Text(
        text = label,
        color = color,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    )
}

private fun decodeThumb(base64: String) = runCatching {
    val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

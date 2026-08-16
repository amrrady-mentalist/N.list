package com.notesapp.offline.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notesapp.offline.data.ChecklistItem
import com.notesapp.offline.data.Note
import com.notesapp.offline.data.NoteColor
import com.notesapp.offline.ui.theme.RunsVisualTransformation
import com.notesapp.offline.ui.theme.applyEditToRuns
import com.notesapp.offline.ui.theme.toComposeColor

/** Package-visible (not file-private) so other screens in this package —
 *  e.g. the effect editor's out-sketch thumbnails — can reuse it too. */
fun decodeBase64ToBitmap(base64: String) = runCatching {
    val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

/**
 * Handles create (noteId == null), plain-text edit, checklist edit, and the
 * drawing entry point. No top app bar, no bordered fields — everything
 * blends into the solid theme background. The formatting/checklist/sketch
 * toolbar is the LAST item in the main Column (real layout space, not an
 * overlay), so content never gets hidden behind it; imePadding() on the
 * whole Column means it rides up together with the keyboard and eases back
 * down when it closes.
 */
@Composable
fun NoteEditScreen(
    viewModel: NotesViewModel,
    noteId: String?,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    onOpenDrawing: (String) -> Unit
) {
    val existing = remember(noteId) { noteId?.let { viewModel.getNote(it) } }
    var current by remember(noteId) { mutableStateOf(existing ?: Note()) }
    var everPersisted by remember(noteId) { mutableStateOf(existing != null) }
    var showColorPicker by remember { mutableStateOf(false) }
    var bodyField by remember(noteId) { mutableStateOf(TextFieldValue(existing?.body ?: "")) }
    var activeBold by remember(noteId) { mutableStateOf(false) }
    var activeItalic by remember(noteId) { mutableStateOf(false) }
    var activeUnderline by remember(noteId) { mutableStateOf(false) }

    val bgColor = if (isDarkTheme) Color.Black else Color.White
    val fgColor = if (isDarkTheme) Color.White else Color.Black

    fun isEmpty(note: Note) =
        note.title.isBlank() && note.body.isBlank() && note.checklist.isEmpty() && note.drawingPngBase64 == null

    fun persist(note: Note) {
        current = note
        if (isEmpty(note)) return // don't write a completely empty note to disk
        everPersisted = true
        viewModel.save(note)
    }

    /** If this note ended up empty — either it never had content, or had
     *  content that got fully deleted — remove it instead of leaving a
     *  blank entry behind. Covers both a fresh note that's still blank
     *  (never persisted, nothing to delete) and one that was typed into
     *  and then fully cleared (was persisted, needs an actual delete). */
    fun handleBack() {
        if (isEmpty(current) && everPersisted) {
            viewModel.delete(current.id)
        }
        onBack()
    }

    /** Routes every body-text mutation (typing or toolbar-triggered) through the same
     *  diff engine so styleRuns always stay correctly shifted, regardless of source. */
    fun updateBody(newValue: TextFieldValue, applyActiveStyle: Boolean = true) {
        val newRuns = applyEditToRuns(
            current.styleRuns,
            bodyField.text,
            newValue.text,
            activeBold = applyActiveStyle && activeBold,
            activeItalic = applyActiveStyle && activeItalic,
            activeUnderline = applyActiveStyle && activeUnderline
        )
        bodyField = newValue
        persist(current.copy(body = newValue.text, styleRuns = newRuns))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        // Intercepts the system back gesture too, not just the on-screen
        // arrow, so an emptied-out note gets cleaned up either way.
        BackHandler(onBack = { handleBack() })

        // Minimal top row — no AppBar chrome, just icons on the solid background.
        Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { handleBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = fgColor)
            }
            Box(modifier = Modifier.weight(1f))
            IconButton(onClick = { showColorPicker = !showColorPicker }) {
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
                    color = fgColor,
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
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = fgColor)
                }
            }
        }

        if (showColorPicker) {
            ColorPickerRow(
                selected = current.color,
                fgColor = fgColor,
                onSelect = {
                    showColorPicker = false
                    persist(current.copy(color = it))
                }
            )
        }

        // Title — no border, no fill, just larger bold text blending into the page.
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            if (current.title.isEmpty()) {
                Text("Title", color = fgColor.copy(alpha = 0.32f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            BasicTextField(
                value = current.title,
                onValueChange = { persist(current.copy(title = it)) },
                singleLine = true,
                textStyle = TextStyle(color = fgColor, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(fgColor),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Drawing preview + body/checklist share a single scroll region, so
        // scrolling to read/write the note also scrolls past the preview.
        // The preview's height is tied directly to that scroll offset —
        // full-size at the top, shrinking down to a small strip as the user
        // scrolls — so it never permanently eats the whole screen the way a
        // fixed-size full-width image would.
        val bodyScrollState = rememberScrollState()
        val density = LocalDensity.current
        val maxImageHeight = 260.dp
        val minImageHeight = 64.dp
        val imageHeight = if (current.drawingPngBase64 != null) {
            with(density) {
                val maxPx = maxImageHeight.toPx()
                val minPx = minImageHeight.toPx()
                (maxPx - bodyScrollState.value).coerceIn(minPx, maxPx).toDp()
            }
        } else 0.dp

        // Tapping anywhere in this region — blank space below short text,
        // padding around the edges, the gap under a short checklist —
        // brings up the keyboard by focusing the body field. This click
        // handler sits on the outer, full-height Box; taps that land on
        // an actual child (the drawing image, a checklist row, the text
        // field itself) are consumed by that child first and never reach
        // it, so this only fires for genuinely empty space.
        val bodyFocusRequester = remember { FocusRequester() }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (!current.isChecklist) bodyFocusRequester.requestFocus()
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(bodyScrollState)
            ) {
                current.drawingPngBase64?.let { b64 ->
                    val bmp = remember(b64) { decodeBase64ToBitmap(b64) }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Drawing",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(imageHeight)
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onOpenDrawing(current.id) }
                        )
                    }
                }

                if (current.isChecklist) {
                    ChecklistEditor(
                        items = current.checklist,
                        fgColor = fgColor,
                        onChange = { persist(current.copy(checklist = it)) }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (bodyField.text.isEmpty()) {
                            Text("Note...", color = fgColor.copy(alpha = 0.28f), fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                        BasicTextField(
                            value = bodyField,
                            onValueChange = { updateBody(it) },
                            visualTransformation = RunsVisualTransformation(current.styleRuns),
                            textStyle = TextStyle(color = fgColor, fontSize = 16.sp, lineHeight = 25.sp),
                            cursorBrush = SolidColor(fgColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 200.dp)
                                .padding(top = 8.dp, bottom = 24.dp)
                                .focusRequester(bodyFocusRequester)
                        )
                    }
                }
            }
        }

        RichTextToolbar(
            showFormatting = !current.isChecklist,
            isChecklist = current.isChecklist,
            isBoldActive = activeBold,
            isItalicActive = activeItalic,
            isUnderlineActive = activeUnderline,
            onToggleBold = { activeBold = !activeBold },
            onToggleItalic = { activeItalic = !activeItalic },
            onToggleUnderline = { activeUnderline = !activeUnderline },
            onBulletLine = { updateBody(applyBulletLine(bodyField), applyActiveStyle = false) },
            onNumberedLine = { updateBody(applyNumberedLine(bodyField), applyActiveStyle = false) },
            onToggleChecklist = {
                if (current.isChecklist) {
                    persist(current.copy(checklist = emptyList()))
                } else if (current.checklist.isEmpty()) {
                    persist(current.copy(checklist = listOf(ChecklistItem())))
                } else {
                    persist(current.copy(checklist = current.checklist + ChecklistItem()))
                }
            },
            onSketch = {
                // Persist unconditionally (bypassing persist()'s empty-note
                // guard) so a stable note id exists for the drawing screen
                // to save back into, even for a brand-new blank note.
                viewModel.save(current)
                onOpenDrawing(current.id)
            },
            modifier = Modifier.padding(vertical = 10.dp),
            tint = fgColor,
            accent = androidx.compose.material3.MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ColorPickerRow(selected: NoteColor, fgColor: Color, onSelect: (NoteColor) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        NoteColor.entries.forEach { c ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (c == NoteColor.NONE) fgColor.copy(alpha = 0.15f) else c.toComposeColor())
                    .then(
                        if (c == selected) Modifier.border(2.dp, fgColor, CircleShape) else Modifier
                    )
                    .clickable { onSelect(c) }
            )
        }
    }
}

@Composable
private fun ChecklistEditor(items: List<ChecklistItem>, fgColor: Color, onChange: (List<ChecklistItem>) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (item.done) Color(0xFF4FE8C4) else Color.Transparent)
                        .border(1.5.dp, if (item.done) Color(0xFF4FE8C4) else fgColor.copy(alpha = 0.35f), CircleShape)
                        .clickable {
                            onChange(items.map { if (it.id == item.id) it.copy(done = !it.done) else it })
                        }
                )
                BasicTextField(
                    value = item.text,
                    onValueChange = { text ->
                        onChange(items.map { if (it.id == item.id) it.copy(text = text) else it })
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = fgColor,
                        fontSize = 16.sp,
                        textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    cursorBrush = SolidColor(fgColor),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                )
                IconButton(onClick = { onChange(items.filterNot { it.id == item.id }) }) {
                    Text("\u00D7", fontSize = 20.sp, color = fgColor)
                }
            }
        }
        Text(
            text = "+ Add item",
            color = fgColor.copy(alpha = 0.6f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 24.dp)
                .clickable { onChange(items + ChecklistItem()) }
        )
    }
}

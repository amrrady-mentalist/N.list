package com.notesapp.offline.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notesapp.offline.data.EffectOut
import com.notesapp.offline.data.EffectType
import com.notesapp.offline.data.ForceListEngine
import com.notesapp.offline.data.MagicEffect
import com.notesapp.offline.data.MagicRepository
import com.notesapp.offline.data.Note
import com.notesapp.offline.ui.theme.AccentA
import com.notesapp.offline.ui.theme.AccentB
import com.notesapp.offline.ui.theme.Danger
import com.notesapp.offline.ui.theme.GlassRadius
import com.notesapp.offline.ui.theme.glassPanel
import kotlinx.coroutines.launch

/**
 * Effect editor — direct functional port of the web app's #effectEditor
 * panel: name, active toggle, Word/List Force type switch, and the
 * type-specific fields (outs list for Word, items list for List Force).
 * Every field autosaves on change, same as the web app's per-input
 * `saveMagic()` calls, rather than a single "Save" button.
 */
@Composable
fun EffectEditorScreen(
    repo: MagicRepository,
    notesViewModel: NotesViewModel,
    effectId: String,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    onOpenSketch: (outId: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val bgColor = if (isDarkTheme) Color.Black else Color.White
    val fgColor = if (isDarkTheme) Color.White else Color.Black

    var effect by remember { mutableStateOf<MagicEffect?>(null) }
    var isActive by remember { mutableStateOf(false) }
    var itemsText by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(effectId) {
        val store = repo.load()
        effect = store.effects.firstOrNull { it.id == effectId }
        isActive = store.activeEffectId == effectId
        itemsText = effect?.items?.joinToString("\n") ?: ""
        loaded = true
    }

    fun persist(updated: MagicEffect) {
        effect = updated
        scope.launch { repo.updateEffect(updated) }
    }

    val current = effect
    if (!loaded || current == null) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            // Without this, the keyboard just overlaps the bottom of the
            // screen instead of the layout shrinking to make room for it —
            // this is what was hiding the last field(s) until the keyboard
            // was dismissed.
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp).glassPanel(radius = GlassRadius.lg, tint = fgColor)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = fgColor)
            }
            IconButton(
                onClick = {
                    // deleteEffect() is a suspend IO write; calling onBack()
                    // right after firing scope.launch (instead of from
                    // inside it) let the navigation happen first, which
                    // tore down this screen's rememberCoroutineScope and
                    // CANCELLED the delete before its file write ever
                    // completed — the effect would still be there when
                    // Magic Settings reloaded. onBack() now only runs once
                    // deleteEffect() has actually finished.
                    scope.launch {
                        repo.deleteEffect(current.id)
                        onBack()
                    }
                },
                modifier = Modifier.size(40.dp).glassPanel(radius = GlassRadius.lg, tint = fgColor)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete effect", tint = fgColor)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
        ) {
            item {
                BasicTextField(
                    value = current.name,
                    onValueChange = { persist(current.copy(name = it)) },
                    textStyle = TextStyle(color = fgColor, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(fgColor),
                    decorationBox = { inner ->
                        if (current.name.isEmpty()) {
                            Text("Effect name", color = fgColor.copy(alpha = 0.34f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                )

                Box(
                    modifier = Modifier
                        .then(
                            if (isActive) Modifier.border(1.dp, AccentB, RoundedCornerShape(100))
                            else Modifier.glassPanel(radius = 100.dp, tint = fgColor)
                        )
                        .clickable {
                            scope.launch {
                                val turningOn = !isActive
                                repo.setActiveEffect(if (turningOn) current.id else null)
                                isActive = turningOn

                                // Let a List Force effect's plain item list
                                // show up as a note the instant it's turned
                                // on, so it can be shown to a spectator
                                // before the PIN force ever happens. Reuses
                                // the same linked note the PIN-resolve flow
                                // later overwrites in place with the forced
                                // order, so activating never creates a
                                // duplicate — the same note just gets its
                                // content replaced when the trick resolves.
                                if (turningOn && current.type == EffectType.LIST) {
                                    val plain = ForceListEngine.actualItems(current.items)
                                    if (plain.isNotEmpty()) {
                                        val numbered = plain.mapIndexed { i, item -> "${i + 1} - $item" }.joinToString("\n")
                                        val existing = current.linkedNoteId?.let { notesViewModel.getNote(it) }
                                        val note = (existing ?: Note(magicEffectId = current.id)).copy(
                                            title = current.title,
                                            body = numbered,
                                            checklist = emptyList(),
                                            pinned = true,
                                            archived = false
                                        )
                                        notesViewModel.save(note)
                                        if (existing == null) {
                                            persist(current.copy(linkedNoteId = note.id))
                                        }
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text(
                        if (isActive) "Active effect \u2713" else "Set as active",
                        color = if (isActive) AccentB else fgColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TypePill(
                        label = "Word",
                        selected = current.type == EffectType.WORD,
                        fgColor = fgColor,
                        modifier = Modifier.weight(1f)
                    ) { persist(current.copy(type = EffectType.WORD)) }
                    TypePill(
                        label = "List Force",
                        selected = current.type == EffectType.LIST,
                        fgColor = fgColor,
                        modifier = Modifier.weight(1f)
                    ) { persist(current.copy(type = EffectType.LIST)) }
                }
            }

            if (current.type == EffectType.WORD) {
                item {
                    FieldLabel("Note title", fgColor)
                    GlassInput(
                        value = current.title,
                        onValueChange = { persist(current.copy(title = it)) },
                        placeholder = "Title for the created note (optional)",
                        fgColor = fgColor
                    )

                    FieldLabel("Note body", fgColor, topPadding = 16.dp)
                    GlassInput(
                        value = current.body,
                        onValueChange = { persist(current.copy(body = it)) },
                        placeholder = "You will choose the word: \$\$\$\$",
                        fgColor = fgColor,
                        minHeight = 110.dp,
                        singleLine = false
                    )

                    FieldLabel("Outs \u2014 code \u2192 word or sketch", fgColor, topPadding = 16.dp)
                }

                items(current.outs, key = { it.id }) { out ->
                    OutRow(
                        out = out,
                        fgColor = fgColor,
                        onCodeChange = { newCode ->
                            val digits = newCode.filter { it.isDigit() }
                            persist(current.copy(outs = current.outs.map { if (it.id == out.id) it.copy(code = digits) else it }))
                        },
                        onWordChange = { newWord ->
                            persist(current.copy(outs = current.outs.map { if (it.id == out.id) it.copy(word = newWord) else it }))
                        },
                        onRemove = {
                            persist(current.copy(outs = current.outs.filterNot { it.id == out.id }))
                        },
                        onAddSketch = { onOpenSketch(out.id) },
                        onRemoveSketch = {
                            persist(current.copy(outs = current.outs.map { if (it.id == out.id) it.copy(drawingPngBase64 = null) else it }))
                        }
                    )
                }

                item {
                    GlassTextPill("+ Add out", fgColor, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)) {
                        persist(current.copy(outs = current.outs + EffectOut()))
                    }
                }
            } else {
                item {
                    FieldLabel("Note title", fgColor)
                    GlassInput(
                        value = current.title,
                        onValueChange = { persist(current.copy(title = it)) },
                        placeholder = "Title for the created list (optional)",
                        fgColor = fgColor
                    )

                    FieldLabel("Force item", fgColor, topPadding = 16.dp)
                    GlassInput(
                        value = current.forceWord,
                        onValueChange = { persist(current.copy(forceWord = it)) },
                        placeholder = "Paste one of the items below",
                        fgColor = fgColor
                    )

                    val digits = ForceListEngine.codeDigits(current.items)
                    val nonBlank = ForceListEngine.actualItems(current.items).size
                    val hint = if (nonBlank == 0) {
                        "Enter PIN while active. Add items below to set this up."
                    } else {
                        "Reads the last $digits digits to move the force item into position (${"0".repeat(digits)} = last item)."
                    }
                    Text(hint, color = fgColor.copy(alpha = 0.34f), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))

                    FieldLabel("Items \u2014 up to 1000 (one per line)", fgColor, topPadding = 16.dp)
                    GlassInput(
                        value = itemsText,
                        onValueChange = { text ->
                            itemsText = text
                            persist(current.copy(items = text.split("\n")))
                        },
                        placeholder = "Item 1\nItem 2\nItem 3\n...",
                        fgColor = fgColor,
                        minHeight = 220.dp,
                        singleLine = false
                    )

                    val n = ForceListEngine.actualItems(itemsText.split("\n")).size
                    Text(
                        "$n " + (if (n == 1) "item" else "items"),
                        color = fgColor.copy(alpha = 0.34f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TypePill(label: String, selected: Boolean, fgColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .then(
                if (selected) {
                    Modifier
                        .clip(RoundedCornerShape(100))
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(AccentA, AccentB)))
                } else {
                    Modifier.glassPanel(radius = 100.dp, tint = fgColor)
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color(0xFF0A0A12) else fgColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FieldLabel(text: String, fgColor: Color, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text.uppercase(),
        color = fgColor.copy(alpha = 0.56f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(top = topPadding, bottom = 10.dp)
    )
}

@Composable
private fun GlassInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    fgColor: Color,
    minHeight: androidx.compose.ui.unit.Dp = 0.dp,
    singleLine: Boolean = true
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight)
            .glassPanel(radius = GlassRadius.sm, tint = fgColor)
            // For multi-line fields, the BasicTextField itself only
            // measures as tall as its current text — one line when empty —
            // so most of this visually tall box wasn't actually clickable.
            // This catches taps anywhere else in the box and focuses the
            // field directly; taps that land on the field's own (small)
            // bounds are handled by it first, same as before.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = fgColor.copy(alpha = 0.34f), fontSize = 15.sp, lineHeight = 24.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(color = fgColor, fontSize = 15.sp, lineHeight = 24.sp),
            cursorBrush = SolidColor(fgColor),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
    }
}

@Composable
private fun GlassTextPill(label: String, fgColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .glassPanel(radius = 100.dp, tint = fgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, color = fgColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OutRow(
    out: EffectOut,
    fgColor: Color,
    onCodeChange: (String) -> Unit,
    onWordChange: (String) -> Unit,
    onRemove: () -> Unit,
    onAddSketch: () -> Unit,
    onRemoveSketch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .width(62.dp)
                    .glassPanel(radius = GlassRadius.sm, tint = fgColor)
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = out.code,
                    onValueChange = onCodeChange,
                    singleLine = true,
                    textStyle = TextStyle(color = fgColor, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                    cursorBrush = SolidColor(fgColor),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text("\u2192", color = fgColor.copy(alpha = 0.5f), fontSize = 16.sp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .glassPanel(radius = GlassRadius.sm, tint = fgColor)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (out.word.isEmpty()) {
                    Text("Word (optional)", color = fgColor.copy(alpha = 0.34f), fontSize = 14.sp)
                }
                BasicTextField(
                    value = out.word,
                    onValueChange = onWordChange,
                    singleLine = true,
                    textStyle = TextStyle(color = fgColor, fontSize = 14.sp),
                    cursorBrush = SolidColor(fgColor),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Remove out", tint = fgColor.copy(alpha = 0.6f))
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 10.dp)
        ) {
            val bmp = remember(out.drawingPngBase64) { out.drawingPngBase64?.let { decodeBase64ToBitmap(it) } }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF111111)),
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    PencilGlyphIcon(fgColor.copy(alpha = 0.7f))
                }
            }
            Text(
                if (out.drawingPngBase64 != null) "Edit sketch" else "Add sketch",
                color = AccentB,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onAddSketch)
            )
            if (out.drawingPngBase64 != null) {
                Text(
                    "Remove sketch",
                    color = Danger,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable(onClick = onRemoveSketch)
                )
            }
        }

        androidx.compose.material3.HorizontalDivider(
            color = fgColor.copy(alpha = 0.14f),
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun PencilGlyphIcon(tint: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 1.4.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
        val outline = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.08f, h * 0.92f)
            lineTo(w * 0.219f, h * 0.602f)
            lineTo(w * 0.741f, h * 0.08f)
            lineTo(w * 0.92f, h * 0.259f)
            lineTo(w * 0.398f, h * 0.781f)
            close()
        }
        drawPath(outline, color = tint, style = stroke)
    }
}

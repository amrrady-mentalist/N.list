package com.notesapp.offline.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notesapp.offline.data.ChecklistItem
import com.notesapp.offline.data.EffectType
import com.notesapp.offline.data.InjectApiClient
import com.notesapp.offline.data.MagicEffect
import com.notesapp.offline.data.MagicRepository
import com.notesapp.offline.data.MathEquationEngine
import com.notesapp.offline.data.Note
import com.notesapp.offline.data.NoteColor
import com.notesapp.offline.ui.theme.RunsVisualTransformation
import com.notesapp.offline.ui.theme.applyEditToRuns
import com.notesapp.offline.ui.theme.toComposeColor
import kotlinx.coroutines.launch

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
    magicRepo: MagicRepository,
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

    // ---- Inject send/receive trigger arming ----
    // Whichever Inject-capable effect governs THIS note gets checked once
    // per note opened — Peek and Math (Sum) can each independently arm
    // their own send and/or receive behavior; Multiple Outs (WORD) can
    // additionally arm a send-only trigger. If more than one is enabled at
    // once, the note's own magicEffectId link wins; otherwise Peek takes
    // priority over Math over Multiple Outs, since only one physical
    // trigger (proximity/volume) fires per note session.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val injectApiClient = remember { InjectApiClient() }
    var triggerEffect by remember(noteId) { mutableStateOf<MagicEffect?>(null) }
    var mathEffect by remember(noteId) { mutableStateOf<MagicEffect?>(null) }
    var apiUrl by remember(noteId) { mutableStateOf<String?>(null) }
    var injectModeOn by remember(noteId) { mutableStateOf(false) }
    var mathPeekOn by remember(noteId) { mutableStateOf(false) }
    var titleBeforeMathPeek by remember(noteId) { mutableStateOf<String?>(null) }

    LaunchedEffect(noteId) {
        val store = magicRepo.load()
        injectModeOn = store.injectModeOn
        apiUrl = store.apiUrl
        mathEffect = store.effectOfType(EffectType.INJECT_SUM)?.takeIf { it.enabled }
        val candidates = listOfNotNull(
            store.effectOfType(EffectType.INJECT_PEEK)?.takeIf { it.enabled },
            store.effectOfType(EffectType.INJECT_SUM)?.takeIf { it.enabled },
            store.effectOfType(EffectType.WORD)?.takeIf { it.enabled && it.injectSendOn }
        )
        val linkedId = existing?.magicEffectId
        triggerEffect = candidates.firstOrNull { it.id == linkedId } ?: candidates.firstOrNull()
    }

    // rememberUpdatedState so the sensor/volume callbacks below (set up
    // once by the DisposableEffect and not recreated on every keystroke)
    // always read the LATEST note body when they actually fire, rather
    // than whatever it was at the moment the listener was registered.
    val latestBody by rememberUpdatedState(bodyField.text)

    fun fireInjectSend() {
        val fx = triggerEffect ?: return
        val url = apiUrl
        if (!injectModeOn || url.isNullOrBlank() || !fx.injectSendOn) return
        val valueToSend = when (fx.type) {
            // Runs the effect's configured equation (default: sum every
            // numeric line) against whatever numbers are on screen —
            // matches audience members calling out/writing a string of
            // digits each, one per line, exactly as shown on screen.
            EffectType.INJECT_SUM -> {
                val values = MathEquationEngine.lineValues(latestBody)
                MathEquationEngine.evaluate(fx.mathEquation, values)?.toString() ?: return
            }
            // Whatever's on screen, as-is — a freely-named celebrity (or
            // anything else) the spectator wrote themselves.
            EffectType.INJECT_PEEK -> latestBody.trim()
            // Multiple Outs "send" — whatever word $$$$ ended up resolving
            // to (or was hand-edited to afterward) on this note's screen.
            EffectType.WORD -> latestBody.trim()
            else -> return
        }
        if (valueToSend.isBlank()) return
        scope.launch { injectApiClient.sendValue(url, valueToSend) }
    }

    fun fireInjectReceive() {
        val fx = triggerEffect ?: return
        val url = apiUrl
        if (!injectModeOn || url.isNullOrBlank() || !fx.injectReceiveOn) return
        if (fx.type != EffectType.INJECT_PEEK && fx.type != EffectType.INJECT_SUM) return
        scope.launch {
            val value = injectApiClient.fetchValue(url) ?: return@launch
            val bodyText = bodyField.text
            val newText = when {
                // Totally empty note — just drop the latest API value
                // straight onto the screen.
                bodyText.isBlank() -> value
                // Note has writing on it AND contains the --value-- token
                // — swap just that token for the latest API value.
                bodyText.contains(INJECT_VALUE_TOKEN) -> bodyText.replace(INJECT_VALUE_TOKEN, value)
                // Has other writing with no token to replace — leave it
                // alone rather than clobbering something the performer
                // (or spectator) actually wrote.
                else -> return@launch
            }
            updateBody(TextFieldValue(newText, selection = androidx.compose.ui.text.TextRange(newText.length)), applyActiveStyle = false)
        }
    }

    fun fireInjectTrigger() {
        fireInjectSend()
        fireInjectReceive()
    }

    DisposableEffect(triggerEffect, injectModeOn, apiUrl) {
        val fx = triggerEffect
        val eligible = injectModeOn && fx != null && !apiUrl.isNullOrBlank() && (fx.injectSendOn || fx.injectReceiveOn)

        var sensorManager: SensorManager? = null
        var proximityListener: SensorEventListener? = null

        if (eligible && fx!!.sendUseProximity) {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            if (proximitySensor != null) {
                var wasFar = true
                proximityListener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val near = event.values.isNotEmpty() && event.values[0] < proximitySensor.maximumRange
                        // Only fire on the far->near transition (a wave, or
                        // setting the phone face down/against a chest) —
                        // not continuously while it stays covered.
                        if (near && wasFar) fireInjectTrigger()
                        wasFar = !near
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                sensorManager.registerListener(proximityListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }

        if (eligible && fx!!.sendUseVolumeButton) {
            VolumeTriggerBus.arm { fireInjectTrigger() }
        } else {
            VolumeTriggerBus.disarm()
        }

        onDispose {
            proximityListener?.let { sensorManager?.unregisterListener(it) }
            VolumeTriggerBus.disarm()
        }
    }

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
                // Title now lives INSIDE the scroll region instead of being
                // pinned above it — it scrolls away together with the body
                // as you read/write further down the note. Previously it
                // sat in its own fixed block outside the scrollable Column,
                // which drew a hard, static edge right where the scrolling
                // content started/ended underneath it; folding it into the
                // same scroll region removes that seam entirely, the same
                // way a "collapsing header" note app title behaves.
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

                current.drawingPngBase64?.let { b64 ->
                    val bmp = remember(b64) { decodeBase64ToBitmap(b64) }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Drawing",
                            contentScale = ContentScale.Fit,
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
            onToggleBold = {
                activeBold = !activeBold
                // Math's offline peek: on a note linked to the (enabled)
                // Math effect, the Bold button doubles as a total-reveal
                // toggle — shows the equation's result in the title, no
                // network involved, and puts the original title back when
                // tapped again.
                val mfx = mathEffect
                if (mfx != null && current.magicEffectId == mfx.id) {
                    if (!mathPeekOn) {
                        val values = MathEquationEngine.lineValues(bodyField.text)
                        val total = MathEquationEngine.evaluate(mfx.mathEquation, values)
                        if (total != null) {
                            titleBeforeMathPeek = current.title
                            mathPeekOn = true
                            persist(current.copy(title = total.toString()))
                        }
                    } else {
                        mathPeekOn = false
                        persist(current.copy(title = titleBeforeMathPeek ?: ""))
                        titleBeforeMathPeek = null
                    }
                }
            },
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

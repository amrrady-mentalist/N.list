package com.notesapp.offline.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun LockFlowHost(viewModel: LockFlowViewModel) {
    val screen by viewModel.screen.collectAsState()
    val backgroundPath by viewModel.lockBackgroundPath.collectAsState()

    when (screen) {
        LockScreenState.BLACKOUT -> BlackoutScreen(onDoubleTap = viewModel::onBlackoutDoubleTap)
        LockScreenState.AMBIENT -> AmbientScreen(backgroundPath = backgroundPath, onSwipeUp = viewModel::onAmbientSwipeUp)
        LockScreenState.PIN -> PinScreen(viewModel, backgroundPath)
    }
}

/** Renders the picked lock-background photo (if any) full-bleed behind the
 *  content, with a dark scrim on top so the clock/PIN dots/keypad stay
 *  readable — colors and stop positions match the web app's scrimAmbient /
 *  scrimPin CSS gradients exactly. */
@Composable
private fun LockBackground(path: String?, scrimStops: List<Pair<Float, Color>>) {
    val bmp = remember(path) {
        path?.let { p -> runCatching { android.graphics.BitmapFactory.decodeFile(p) }.getOrNull() }
    }
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(*scrimStops.toTypedArray()))
        )
    } else {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
    }
}

private fun doubleTapModifier(windowMillis: Long = 320L, onDoubleTap: () -> Unit): Modifier {
    var lastTap = 0L
    return Modifier.pointerInput(Unit) {
        detectTapGestures(onTap = {
            val now = System.currentTimeMillis()
            if (now - lastTap < windowMillis) {
                lastTap = 0L
                onDoubleTap()
            } else {
                lastTap = now
            }
        })
    }
}

@Composable
private fun BlackoutScreen(onDoubleTap: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(doubleTapModifier(onDoubleTap = onDoubleTap))
    )
}

@Composable
private fun AmbientScreen(backgroundPath: String?, onSwipeUp: () -> Unit) {
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            date = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now)
            kotlinx.coroutines.delay(15_000)
        }
    }

    var startY = 0f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { startY = it.y },
                    onDragEnd = {},
                    onVerticalDrag = { change, _ ->
                        val dy = change.position.y - startY
                        if (dy < -60f) onSwipeUp()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        LockBackground(
            path = backgroundPath,
            scrimStops = listOf(
                0f to Color.Black.copy(alpha = 0.35f),
                0.4f to Color.Black.copy(alpha = 0.2f),
                1f to Color.Black.copy(alpha = 0.6f)
            )
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = time,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 72.sp,
                fontWeight = FontWeight.Light
            )
            Text(
                text = date,
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun PinScreen(viewModel: LockFlowViewModel, backgroundPath: String?) {
    val pin by viewModel.pinDigits.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        LockBackground(
            path = backgroundPath,
            scrimStops = listOf(
                0f to Color.Black.copy(alpha = 0.55f),
                0.32f to Color.Black.copy(alpha = 0.25f),
                1f to Color.Black.copy(alpha = 0.75f)
            )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 90.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.then(doubleTapModifier(onDoubleTap = viewModel::onPinAbortDoubleTap)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LockGlyphIcon()
                    Text(
                        "Enter PIN",
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 20.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    PinDotsRow(pin.length)
                }
            }

            // Two weighted spacers (rather than one large one that only
            // sits above the keypad) pull the keypad up into the middle of
            // the screen instead of pinning it to the very bottom — matches
            // where it's meant to sit rather than reading as an afterthought
            // stuck to the bottom edge.
            Box(modifier = Modifier.weight(0.38f))

            PinKeypad(onKey = viewModel::onPinKey, modifier = Modifier.padding(top = 13.dp)) // +2mm (≈12.6dp) fixed shift down

            Box(modifier = Modifier.weight(0.62f))
        }
    }
}

/** A simple padlock glyph — same stroked-outline style as the rest of the
 *  app's hand-drawn icons — sitting above "Enter PIN", matching the
 *  reference lock-screen design. */
@Composable
private fun LockGlyphIcon() {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(30.dp)) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 2.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
        // Shackle (the arc on top)
        drawArc(
            color = Color.White.copy(alpha = 0.95f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.28f, h * 0.08f),
            size = androidx.compose.ui.geometry.Size(w * 0.44f, w * 0.44f),
            style = stroke
        )
        // Body
        drawRoundRect(
            color = Color.White.copy(alpha = 0.95f),
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.18f, h * 0.42f),
            size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.1f, w * 0.1f),
            style = stroke
        )
        // Keyhole
        drawCircle(
            color = Color.White.copy(alpha = 0.95f),
            radius = w * 0.05f,
            center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.64f)
        )
    }
}

@Composable
private fun PinDotsRow(filledCount: Int) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.padding(top = 20.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        repeat(4) { i ->
            val filled = i < filledCount
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (filled) Color.White else Color.White.copy(alpha = 0.25f)
                    )
                    .then(
                        if (filled) Modifier.border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                        else Modifier
                    )
            )
        }
    }
}

@Composable
private fun PinKeypad(onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    // The web app uses a fixed pixel size for each key (68px) and grid
    // gap (18px row / 26px column) — not a percentage of screen width.
    // Keys are fixed-size circles in a plain Row/Column grid rather than a
    // stretchy LazyVerticalGrid, so they stay the same size regardless of
    // screen width. From the original 15dp row / 22dp column baseline:
    // row gap +2mm (≈12.6dp) -> 28dp, column gap +1mm (≈6.3dp) -> 28dp.
    // The row (vertical) gap is laid out top-down by this Column, so the
    // added space is inserted between rows below row 1, not above it —
    // row 1 stays anchored in place and rows 2-4 shift further down,
    // never up.
    val keySize = 68.dp // 62dp + 1mm (≈6.3dp) — lands almost exactly on the web app's own 68px key size
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("empty", "0", "delete")
    )

    // Briefly highlights whichever key was tapped last — matches the
    // reference design's glowing "3" key. Purely a local UI touch, doesn't
    // need to live in the ViewModel.
    var glowingKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(glowingKey) {
        if (glowingKey != null) {
            kotlinx.coroutines.delay(220)
            glowingKey = null
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(28.dp), // red lines: +2mm (≈12.6dp) over the 15dp baseline — this is the ROW gap
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { row ->
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) { // yellow lines: +1mm (≈6.3dp) over the 22dp baseline — this is the COLUMN gap
                row.forEach { key ->
                    when (key) {
                        "empty" -> Box(modifier = Modifier.size(keySize))
                        "delete" -> Box(
                            modifier = Modifier
                                .size(keySize)
                                .clip(CircleShape)
                                .pointerInput(Unit) { detectTapGestures(onTap = { onKey("delete") }) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Delete", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                        }
                        else -> {
                            val glowing = glowingKey == key
                            val glowAlpha by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (glowing) 1f else 0f,
                                animationSpec = androidx.compose.animation.core.tween(180),
                                label = "keyGlow"
                            )
                            Box(
                                modifier = Modifier
                                    .size(keySize)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.10f + 0.10f * glowAlpha))
                                    .border(1.dp, Color.White.copy(alpha = 0.14f + 0.30f * glowAlpha), CircleShape)
                                    .then(
                                        if (glowAlpha > 0f) {
                                            Modifier.background(
                                                androidx.compose.ui.graphics.Brush.radialGradient(
                                                    listOf(Color.White.copy(alpha = 0.18f * glowAlpha), Color.Transparent)
                                                ),
                                                CircleShape
                                            )
                                        } else Modifier
                                    )
                                    .pointerInput(Unit) {
                                        detectTapGestures(onTap = {
                                            glowingKey = key
                                            onKey(key)
                                        })
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(key, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light)
                            }
                        }
                    }
                }
            }
        }
    }
}

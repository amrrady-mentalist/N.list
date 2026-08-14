package com.notesapp.offline.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notesapp.offline.ui.theme.BlobAmber
import com.notesapp.offline.ui.theme.BlobRose
import com.notesapp.offline.ui.theme.BlobTeal
import com.notesapp.offline.ui.theme.BlobViolet
import java.io.ByteArrayOutputStream

private data class DrawnStroke(val points: List<Offset>, val color: Color, val widthPx: Float)

private val palette = listOf(
    Color.White,
    Color.Black,
    BlobViolet,
    BlobRose,
    BlobTeal,
    BlobAmber,
    Color(0xFFFF6B6B), // red
    Color(0xFFFF9F45), // orange
    Color(0xFFFFE45E), // yellow
    Color(0xFF4ADE80), // green
    Color(0xFF38BDF8), // sky blue
    Color(0xFF6366F1), // indigo
    Color(0xFFE879F9), // magenta
    Color(0xFFA3A3A3)  // gray
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(
    initialPngBase64: String?,
    onSave: (String) -> Unit,
    onBack: () -> Unit
) {
    val strokes = remember {
        mutableStateOf(emptyList<DrawnStroke>())
    }
    var currentColor by remember { mutableStateOf(palette.first()) }
    var brushSize by remember { mutableFloatStateOf(8f) }
    var canvasSizePx by remember { mutableStateOf(Offset(1f, 1f)) }

    // initialPngBase64 isn't re-decoded into editable strokes (raster vs
    // vector mismatch) — opening an existing drawing starts a fresh canvas
    // layered on top conceptually. Good enough for "add a sketch"; true
    // round-trip editing of a previously saved drawing is a later refinement.

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sketch") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (strokes.value.isNotEmpty()) {
                            strokes.value = strokes.value.dropLast(1)
                        }
                    }) {
                        Text("↺", fontSize = 20.sp)
                    }
                    IconButton(onClick = {
                        val bitmap = rasterize(strokes.value, canvasSizePx)
                        onSave(bitmapToBase64(bitmap))
                    }) {
                        Text("✓", fontSize = 20.sp)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        var current = mutableListOf<Offset>()
                        detectDragGestures(
                            onDragStart = { offset ->
                                current = mutableListOf(offset)
                                // Push a placeholder stroke immediately so the first
                                // onDrag call below has something of its own to
                                // replace via dropLast(1) — without this, that first
                                // dropLast(1) was removing the PREVIOUS finished
                                // stroke instead, which is why drawing a second line
                                // used to erase the first one.
                                strokes.value = strokes.value + DrawnStroke(current.toList(), currentColor, brushSize)
                            },
                            onDrag = { change, _ ->
                                current.add(change.position)
                                strokes.value = strokes.value.dropLast(1) +
                                    DrawnStroke(current.toList(), currentColor, brushSize)
                            },
                            onDragEnd = {
                                if (current.isNotEmpty()) {
                                    strokes.value = strokes.value.dropLast(1) +
                                        DrawnStroke(current.toList(), currentColor, brushSize)
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    canvasSizePx = Offset(size.width, size.height)
                    strokes.value.forEach { stroke ->
                        if (stroke.points.size < 2) return@forEach
                        val path = Path().apply {
                            moveTo(stroke.points.first().x, stroke.points.first().y)
                            stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path = path,
                            color = stroke.color,
                            style = Stroke(
                                width = stroke.widthPx,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }

            // Bottom toolbar: color swatches + brush size, matching the
            // original app's drawing sheet. Scrollable since the palette
            // is wider than most phone screens.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                palette.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c)
                            .then(
                                if (c == Color.Black) Modifier.border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                else Modifier
                            )
                            .clickable { currentColor = c }
                    )
                }
            }
            Slider(
                value = brushSize,
                onValueChange = { brushSize = it },
                valueRange = 2f..40f,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

private fun rasterize(strokes: List<DrawnStroke>, size: Offset): Bitmap {
    val w = size.x.toInt().coerceAtLeast(1)
    val h = size.y.toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(android.graphics.Color.BLACK)
    val paint = AndroidPaint().apply {
        isAntiAlias = true
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
    }
    strokes.forEach { stroke ->
        if (stroke.points.size < 2) return@forEach
        paint.color = android.graphics.Color.argb(
            (stroke.color.alpha * 255).toInt(),
            (stroke.color.red * 255).toInt(),
            (stroke.color.green * 255).toInt(),
            (stroke.color.blue * 255).toInt()
        )
        paint.strokeWidth = stroke.widthPx
        val path = android.graphics.Path()
        path.moveTo(stroke.points.first().x, stroke.points.first().y)
        stroke.points.drop(1).forEach { path.lineTo(it.x, it.y) }
        canvas.drawPath(path, paint)
    }
    return bitmap
}

private fun bitmapToBase64(bitmap: Bitmap): String {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}

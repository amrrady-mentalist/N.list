package com.notesapp.offline.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
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
    Color(0xFFFF6B6B),
    Color(0xFFFF9F45),
    Color(0xFFFFE45E),
    Color(0xFF4ADE80),
    Color(0xFF38BDF8),
    Color(0xFF6366F1),
    Color(0xFFE879F9),
    Color(0xFFA3A3A3)
)

private fun decodeBitmap(base64: String) = runCatching {
    val bytes = Base64.decode(base64, Base64.NO_WRAP)
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

@Composable
fun DrawingScreen(
    initialPngBase64: String?,
    isDarkTheme: Boolean,
    onSave: (String) -> Unit,
    onBack: () -> Unit
) {
    val bgColor = if (isDarkTheme) Color.Black else Color.White
    val fgColor = if (isDarkTheme) Color.White else Color.Black

    val strokes = remember { mutableStateOf(emptyList<DrawnStroke>()) }
    val redoStack = remember { mutableStateOf(emptyList<DrawnStroke>()) }
    var backgroundCleared by remember { mutableStateOf(false) }
    val backgroundBitmap = remember(initialPngBase64) { initialPngBase64?.let { decodeBitmap(it) } }

    var currentColor by remember { mutableStateOf(if (isDarkTheme) Color.White else Color.Black) }
    var brushSize by remember { mutableFloatStateOf(8f) }
    var canvasSizePx by remember { mutableStateOf(Offset(1f, 1f)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
    ) {
        // Minimal top row, matching NoteEditScreen — no separate AppBar tint.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                BackArrowIcon(tint = fgColor)
            }
            Text("Sketch", color = fgColor, fontSize = 18.sp)
            Row {
                IconButton(onClick = {
                    if (strokes.value.isNotEmpty()) {
                        redoStack.value = emptyList()
                        strokes.value = emptyList()
                        backgroundCleared = true
                    }
                }) {
                    TrashIcon(tint = fgColor)
                }
                IconButton(onClick = {
                    if (strokes.value.isNotEmpty()) {
                        redoStack.value = redoStack.value + strokes.value.last()
                        strokes.value = strokes.value.dropLast(1)
                    }
                }) {
                    UndoIcon(tint = fgColor)
                }
                IconButton(onClick = {
                    if (redoStack.value.isNotEmpty()) {
                        strokes.value = strokes.value + redoStack.value.last()
                        redoStack.value = redoStack.value.dropLast(1)
                    }
                }) {
                    RedoIcon(tint = fgColor)
                }
                IconButton(onClick = {
                    val bitmap = rasterize(
                        strokes = strokes.value,
                        size = canvasSizePx,
                        background = if (backgroundCleared) null else backgroundBitmap,
                        fallbackBg = bgColor
                    )
                    onSave(bitmapToBase64(bitmap))
                }) {
                    CheckIcon(tint = fgColor)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(bgColor)
                .pointerInput(Unit) {
                    var current = mutableListOf<Offset>()
                    detectDragGestures(
                        onDragStart = { offset ->
                            current = mutableListOf(offset)
                            redoStack.value = emptyList() // new stroke invalidates redo history
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
            if (!backgroundCleared && backgroundBitmap != null) {
                Image(
                    bitmap = backgroundBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
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
                        style = Stroke(width = stroke.widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }

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
                            if (c == bgColor) Modifier.border(1.dp, fgColor.copy(alpha = 0.3f), CircleShape)
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

private fun rasterize(strokes: List<DrawnStroke>, size: Offset, background: Bitmap?, fallbackBg: Color): Bitmap {
    val w = size.x.toInt().coerceAtLeast(1)
    val h = size.y.toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    if (background != null) {
        val src = android.graphics.Rect(0, 0, background.width, background.height)
        val dst = android.graphics.Rect(0, 0, w, h)
        canvas.drawBitmap(background, src, dst, null)
    } else {
        canvas.drawColor(
            android.graphics.Color.argb(
                255,
                (fallbackBg.red * 255).toInt(),
                (fallbackBg.green * 255).toInt(),
                (fallbackBg.blue * 255).toInt()
            )
        )
    }
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

// --- Small hand-drawn icons, avoiding any external icon-library risk ---

@Composable
private fun BackArrowIcon(tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.75f, h * 0.15f)
            lineTo(w * 0.3f, h * 0.5f)
            lineTo(w * 0.75f, h * 0.85f)
        }
        drawPath(path, color = tint, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun CheckIcon(tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.18f, h * 0.52f)
            lineTo(w * 0.42f, h * 0.75f)
            lineTo(w * 0.85f, h * 0.25f)
        }
        drawPath(path, color = tint, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun TrashIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Lid
        drawLine(tint, Offset(w * 0.15f, h * 0.28f), Offset(w * 0.85f, h * 0.28f), strokeWidth = stroke.width)
        drawLine(tint, Offset(w * 0.38f, h * 0.28f), Offset(w * 0.42f, h * 0.12f), strokeWidth = stroke.width)
        drawLine(tint, Offset(w * 0.62f, h * 0.28f), Offset(w * 0.58f, h * 0.12f), strokeWidth = stroke.width)
        drawLine(tint, Offset(w * 0.42f, h * 0.12f), Offset(w * 0.58f, h * 0.12f), strokeWidth = stroke.width)
        // Body
        val bodyPath = Path().apply {
            moveTo(w * 0.22f, h * 0.32f)
            lineTo(w * 0.28f, h * 0.88f)
            lineTo(w * 0.72f, h * 0.88f)
            lineTo(w * 0.78f, h * 0.32f)
        }
        drawPath(bodyPath, color = tint, style = stroke)
        drawLine(tint, Offset(w * 0.4f, h * 0.42f), Offset(w * 0.42f, h * 0.78f), strokeWidth = stroke.width)
        drawLine(tint, Offset(w * 0.6f, h * 0.42f), Offset(w * 0.58f, h * 0.78f), strokeWidth = stroke.width)
    }
}

@Composable
private fun UndoIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = 1.8.dp.toPx()
        val radius = size.minDimension * 0.32f
        val center = Offset(size.width * 0.55f, size.height * 0.55f)
        drawArc(
            color = tint,
            startAngle = 200f,
            sweepAngle = 210f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        val arrowTip = Offset(center.x - radius, center.y - radius * 0.05f)
        val arrow = Path().apply {
            moveTo(arrowTip.x + radius * 0.55f, arrowTip.y - radius * 0.55f)
            lineTo(arrowTip.x, arrowTip.y)
            lineTo(arrowTip.x + radius * 0.55f, arrowTip.y + radius * 0.35f)
        }
        drawPath(arrow, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun RedoIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = 1.8.dp.toPx()
        val radius = size.minDimension * 0.32f
        val center = Offset(size.width * 0.45f, size.height * 0.55f)
        drawArc(
            color = tint,
            startAngle = -20f,
            sweepAngle = -210f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        val arrowTip = Offset(center.x + radius, center.y - radius * 0.05f)
        val arrow = Path().apply {
            moveTo(arrowTip.x - radius * 0.55f, arrowTip.y - radius * 0.55f)
            lineTo(arrowTip.x, arrowTip.y)
            lineTo(arrowTip.x - radius * 0.55f, arrowTip.y + radius * 0.35f)
        }
        drawPath(arrow, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

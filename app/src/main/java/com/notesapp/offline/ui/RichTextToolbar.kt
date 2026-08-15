package com.notesapp.offline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notesapp.offline.ui.theme.GlassRadius
import com.notesapp.offline.ui.theme.glassPanel

private fun lineBounds(text: String, cursor: Int): Pair<Int, Int> {
    val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', cursor).let { if (it == -1) text.length else it }
    return lineStart to lineEnd
}

/**
 * Inserts "• " at the start of the current line — unless the current line
 * is ALREADY a bullet line, in which case it starts a fresh bullet on a
 * new line below (so repeated taps build a list instead of stacking
 * bullets on one line).
 */
fun applyBulletLine(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val cursor = value.selection.min
    val (lineStart, lineEnd) = lineBounds(text, cursor)
    val currentLine = text.substring(lineStart, lineEnd)

    return if (currentLine.startsWith("\u2022 ")) {
        val insertion = "\n\u2022 "
        val newText = text.substring(0, lineEnd) + insertion + text.substring(lineEnd)
        TextFieldValue(newText, TextRange(lineEnd + insertion.length))
    } else {
        val prefix = "\u2022 "
        val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
        TextFieldValue(newText, TextRange(value.selection.min + prefix.length, value.selection.max + prefix.length))
    }
}

/**
 * Inserts "N. " at the start of the current line, auto-incrementing from
 * the previous numbered line — unless the current line ALREADY has a
 * number, in which case it starts the next number on a new line below.
 */
fun applyNumberedLine(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val cursor = value.selection.min
    val (lineStart, lineEnd) = lineBounds(text, cursor)
    val currentLine = text.substring(lineStart, lineEnd)
    val currentMatch = Regex("""^(\d+)\.\s""").find(currentLine)

    if (currentMatch != null) {
        val nextNumber = (currentMatch.groupValues[1].toIntOrNull() ?: 0) + 1
        val insertion = "\n$nextNumber. "
        val newText = text.substring(0, lineEnd) + insertion + text.substring(lineEnd)
        return TextFieldValue(newText, TextRange(lineEnd + insertion.length))
    }

    var prevNumber = 0
    if (lineStart > 0) {
        val prevLineEnd = lineStart - 1
        val prevLineStart = text.lastIndexOf('\n', (prevLineEnd - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        val prevLine = text.substring(prevLineStart, prevLineEnd)
        Regex("""^(\d+)\.\s""").find(prevLine)?.let { m ->
            prevNumber = m.groupValues[1].toIntOrNull() ?: 0
        }
    }
    val prefix = "${prevNumber + 1}. "
    val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
    return TextFieldValue(newText, TextRange(value.selection.min + prefix.length, value.selection.max + prefix.length))
}

@Composable
fun RichTextToolbar(
    showFormatting: Boolean,
    isChecklist: Boolean,
    isBoldActive: Boolean,
    isItalicActive: Boolean,
    isUnderlineActive: Boolean,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onToggleUnderline: () -> Unit,
    onBulletLine: () -> Unit,
    onNumberedLine: () -> Unit,
    onToggleChecklist: () -> Unit,
    onSketch: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    accent: Color = Color(0xFF8B7CFF)
) {
    // Arrangement.SpaceEvenly (rather than left-packed spacedBy) is what
    // actually makes the buttons look symmetrical — it distributes them
    // (and the margins on both ends) evenly across the full bar width,
    // regardless of how many are visible. The old fixed 6dp gaps were a
    // leftover from when each button had its own visible box background
    // to anchor against; without that box, fixed small gaps just look
    // arbitrary. horizontalScroll is dropped too — seven icons at most
    // comfortably fit without needing to scroll.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(radius = GlassRadius.lg, tint = tint)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showFormatting) {
            ToolbarButtonContainer(onClick = onToggleBold) {
                Text("B", color = if (isBoldActive) accent else tint, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            ToolbarButtonContainer(onClick = onToggleItalic) {
                Text("I", color = if (isItalicActive) accent else tint, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            }
            ToolbarButtonContainer(onClick = onToggleUnderline) {
                Text("U", color = if (isUnderlineActive) accent else tint, fontSize = 17.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
            }
            ToolbarButtonContainer(onClick = onBulletLine) {
                Text("\u2022", color = tint, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            ToolbarButtonContainer(onClick = onNumberedLine) {
                Text("1.", color = tint, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        ToolbarButtonContainer(onClick = onToggleChecklist) {
            ChecklistIcon(tint = if (isChecklist) accent else tint)
        }
        ToolbarButtonContainer(onClick = onSketch) {
            Text("\u270E", color = tint, fontSize = 17.sp)
        }
    }
}

/** No background at all — the button is just its icon/glyph, floating
 *  directly on the toolbar's glass panel. Active state is communicated
 *  purely through the content's own color (see call sites), not a box. */
@Composable
private fun ToolbarButtonContainer(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** A circle with a checkmark inside — the checklist toggle, drawn rather
 *  than relying on a Unicode glyph so it actually reads as an icon. */
@Composable
private fun ChecklistIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val strokeWidth = 1.6.dp.toPx()
        drawCircle(color = tint, radius = size.minDimension / 2 - strokeWidth, style = Stroke(width = strokeWidth))
        val check = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.52f)
            lineTo(size.width * 0.44f, size.height * 0.68f)
            lineTo(size.width * 0.74f, size.height * 0.34f)
        }
        drawPath(check, color = tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

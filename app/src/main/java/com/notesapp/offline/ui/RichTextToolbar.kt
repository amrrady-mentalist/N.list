package com.notesapp.offline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notesapp.offline.ui.theme.GlassRadius
import com.notesapp.offline.ui.theme.glassPanel

/** Inserts "• " (a real bullet character) at the start of the current line. */
fun applyBulletLine(value: TextFieldValue): TextFieldValue {
    val cursor = value.selection.min
    val lineStart = value.text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
    val prefix = "\u2022 "
    val newText = value.text.substring(0, lineStart) + prefix + value.text.substring(lineStart)
    return TextFieldValue(newText, TextRange(value.selection.min + prefix.length, value.selection.max + prefix.length))
}

/** Inserts "N. " at the start of the current line, auto-incrementing from the previous numbered line. */
fun applyNumberedLine(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val cursor = value.selection.min
    val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }

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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(radius = GlassRadius.lg, tint = tint)
            .padding(6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (showFormatting) {
            ToolbarButton(label = "B", weight = FontWeight.Bold, active = isBoldActive, tint = tint, accent = accent, onClick = onToggleBold)
            ToolbarButton(label = "I", italic = true, active = isItalicActive, tint = tint, accent = accent, onClick = onToggleItalic)
            ToolbarButton(label = "U", underline = true, active = isUnderlineActive, tint = tint, accent = accent, onClick = onToggleUnderline)
            ToolbarButton(label = "\u2022", tint = tint, accent = accent, onClick = onBulletLine)
            ToolbarButton(label = "1.", tint = tint, accent = accent, onClick = onNumberedLine)
        }
        ToolbarButton(label = if (isChecklist) "\u2611" else "\u2610", tint = tint, accent = accent, onClick = onToggleChecklist)
        ToolbarButton(label = "\u270E", tint = tint, accent = accent, onClick = onSketch)
    }
}

@Composable
private fun ToolbarButton(
    label: String,
    weight: FontWeight = FontWeight.Bold,
    italic: Boolean = false,
    underline: Boolean = false,
    active: Boolean = false,
    tint: Color = Color.White,
    accent: Color = Color(0xFF8B7CFF),
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) accent else tint.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) Color.White else tint,
            fontSize = 17.sp,
            fontWeight = weight,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = if (underline) TextDecoration.Underline else TextDecoration.None
        )
    }
}

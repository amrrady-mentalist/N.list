package com.notesapp.offline.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/** Wraps the current selection in [prefix]/[suffix], or inserts an empty pair at the cursor. */
private fun wrapSelection(value: TextFieldValue, prefix: String, suffix: String = prefix): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val newText = value.text.substring(0, start) + prefix + value.text.substring(start, end) + suffix + value.text.substring(end)
    val newCursor = if (start == end) start + prefix.length else end + prefix.length + suffix.length
    return TextFieldValue(newText, TextRange(newCursor))
}

/** Inserts [prefix] at the start of the line the cursor/selection currently sits on. */
private fun prefixCurrentLine(value: TextFieldValue, prefix: String): TextFieldValue {
    val cursor = value.selection.min
    val searchFrom = (cursor - 1).coerceAtLeast(0)
    val lineStart = value.text.lastIndexOf('\n', searchFrom).let { if (it == -1) 0 else it + 1 }
    val newText = value.text.substring(0, lineStart) + prefix + value.text.substring(lineStart)
    return TextFieldValue(
        newText,
        TextRange(value.selection.min + prefix.length, value.selection.max + prefix.length)
    )
}

fun applyBold(value: TextFieldValue): TextFieldValue = wrapSelection(value, "**")
fun applyItalic(value: TextFieldValue): TextFieldValue = wrapSelection(value, "_")
fun applyUnderline(value: TextFieldValue): TextFieldValue = wrapSelection(value, "~")
fun applyBulletLine(value: TextFieldValue): TextFieldValue = prefixCurrentLine(value, "- ")
fun applyNumberedLine(value: TextFieldValue): TextFieldValue = prefixCurrentLine(value, "1. ")

@Composable
fun RichTextToolbar(onAction: ((TextFieldValue) -> TextFieldValue) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(radius = GlassRadius.lg)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ToolbarButton(label = "B", weight = FontWeight.Bold) { onAction(::applyBold) }
        ToolbarButton(label = "I", italic = true) { onAction(::applyItalic) }
        ToolbarButton(label = "U", underline = true) { onAction(::applyUnderline) }
        ToolbarButton(label = "\u2022") { onAction(::applyBulletLine) }
        ToolbarButton(label = "1.") { onAction(::applyNumberedLine) }
    }
}

@Composable
private fun ToolbarButton(
    label: String,
    weight: FontWeight = FontWeight.Normal,
    italic: Boolean = false,
    underline: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = weight,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = if (underline) TextDecoration.Underline else TextDecoration.None
        )
    }
}

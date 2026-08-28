package com.notesapp.offline.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notesapp.offline.data.NoteColor
import com.notesapp.offline.ui.theme.toComposeColor

private fun lineBounds(text: String, cursor: Int): Pair<Int, Int> {
    val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', cursor).let { if (it == -1) text.length else it }
    return lineStart to lineEnd
}

/**
 * Inserts "• " at the start of the current line — unless the current line
 * is ALREADY a bullet line, in which case it starts a fresh bullet on a
 * new line below.
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
 * the previous numbered line.
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
    isKeyboardOpen: Boolean,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onToggleUnderline: () -> Unit,
    onBulletLine: () -> Unit,
    onNumberedLine: () -> Unit,
    onToggleChecklist: () -> Unit,
    onSketch: () -> Unit,
    selectedColor: NoteColor,
    onSelectColor: (NoteColor) -> Unit,
    modifier: Modifier = Modifier,
    onLongPressItalic: (() -> Unit)? = null,
    isDarkTheme: Boolean = true,
    tint: Color = Color.White,
    accent: Color = Color(0xFFEEA000)
) {
    var isAaExpanded by remember { mutableStateOf(false) }
    var isPlusExpanded by remember { mutableStateOf(false) }

    val barBg = if (isDarkTheme) Color(0xFF1E1E22) else Color(0xFFE8E8EC)
    val subPanelBg = if (isDarkTheme) Color(0xFF26262B) else Color(0xFFDADAE0)
    val pillBg = if (isDarkTheme) Color(0xFF2A2A30) else Color(0xFFD4D4DC)

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Expandable Sub-bar for Typography / Formatting (B, I, U, •, 1.)
        AnimatedVisibility(
            visible = isAaExpanded && showFormatting,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(subPanelBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolbarButtonContainer(onClick = onToggleBold) {
                    Text("B", color = if (isBoldActive) accent else tint, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                ToolbarButtonContainer(onClick = onToggleItalic, onLongClick = onLongPressItalic) {
                    Text("I", color = if (isItalicActive) accent else tint, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                }
                ToolbarButtonContainer(onClick = onToggleUnderline) {
                    Text("U", color = if (isUnderlineActive) accent else tint, fontSize = 17.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
                }
                ToolbarButtonContainer(onClick = onBulletLine) {
                    Text("•", color = tint, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                ToolbarButtonContainer(onClick = onNumberedLine) {
                    Text("1.", color = tint, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Expandable Sub-bar for (+) options: Note Color Picker & Extra Tools
        AnimatedVisibility(
            visible = isPlusExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(subPanelBg)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Color:", color = tint.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                NoteColor.entries.forEach { c ->
                    val isSelected = c == selectedColor
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (c == NoteColor.NONE) tint.copy(alpha = 0.2f) else c.toComposeColor())
                            .then(
                                if (isSelected) Modifier.background(Color.Transparent).then(
                                    Modifier.padding(2.dp).clip(CircleShape)
                                ) else Modifier
                            )
                            .clickable {
                                onSelectColor(c)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (c == NoteColor.NONE) tint else Color.White)
                            )
                        }
                    }
                }
            }
        }

        // Floating pill bottom bar when keyboard is hidden vs Full-width bar when keyboard is open
        if (!isKeyboardOpen) {
            // Floating pill capsule (Style from 2nd screenshot)
            Box(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(pillBg)
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Magic / Drawing / Pen tool
                    ToolbarButtonContainer(onClick = onSketch) {
                        MagicPenIcon(tint = tint)
                    }

                    // Checklist toggle
                    ToolbarButtonContainer(onClick = onToggleChecklist) {
                        ChecklistIcon(tint = if (isChecklist) accent else tint)
                    }

                    // Plus / Add extras
                    ToolbarButtonContainer(onClick = { isPlusExpanded = !isPlusExpanded }) {
                        PlusCircleIcon(tint = if (isPlusExpanded) accent else tint)
                    }
                }
            }
        } else {
            // Full-width bar docked right above keyboard (Style from 1st screenshot)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(barBg)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Magic Pen / Drawing icon with sparkle
                ToolbarButtonContainer(onClick = onSketch) {
                    MagicPenIcon(tint = tint)
                }

                // 2. Erase / Scribble / Clean icon
                ToolbarButtonContainer(onClick = { /* formatting or quick action */ }) {
                    ScribbleIcon(tint = tint)
                }

                // 3. Checklist circle checkmark
                ToolbarButtonContainer(onClick = onToggleChecklist) {
                    ChecklistIcon(tint = if (isChecklist) accent else tint)
                }

                // 4. Aa (Typography / Formatting dropdown toggle)
                ToolbarButtonContainer(onClick = {
                    isAaExpanded = !isAaExpanded
                    if (isAaExpanded) isPlusExpanded = false
                }) {
                    Text(
                        text = "Aa",
                        color = if (isAaExpanded || isBoldActive || isItalicActive || isUnderlineActive) accent else tint,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // 5. (+) Plus circle (Color picker & extras)
                ToolbarButtonContainer(onClick = {
                    isPlusExpanded = !isPlusExpanded
                    if (isPlusExpanded) isAaExpanded = false
                }) {
                    PlusCircleIcon(tint = if (isPlusExpanded) accent else tint)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolbarButtonContainer(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Clean monochrome Pen / Drawing icon matching the stroke style and color of the rest of the toolbar.
 */
@Composable
private fun MagicPenIcon(tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.8.dp.toPx()
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Pen barrel (slanted rectangle)
        val barrelPath = Path().apply {
            moveTo(w * 0.32f, h * 0.74f)
            lineTo(w * 0.72f, h * 0.22f)
            lineTo(w * 0.84f, h * 0.34f)
            lineTo(w * 0.44f, h * 0.86f)
            close()
        }
        drawPath(barrelPath, color = tint, style = stroke)

        // Pen tip
        val tipPath = Path().apply {
            moveTo(w * 0.32f, h * 0.74f)
            lineTo(w * 0.20f, h * 0.90f)
            lineTo(w * 0.44f, h * 0.86f)
        }
        drawPath(tipPath, color = tint, style = stroke)

        // Top eraser / cap divider line
        val capLine = Path().apply {
            moveTo(w * 0.62f, h * 0.35f)
            lineTo(w * 0.74f, h * 0.47f)
        }
        drawPath(capLine, color = tint, style = stroke)
    }
}

/**
 * Scribble / Line-slash icon matching the 2nd icon in the 1st screenshot.
 */
@Composable
private fun ScribbleIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Diagonal slash with little corner hooks
        val path = Path().apply {
            moveTo(w * 0.22f, h * 0.45f)
            lineTo(w * 0.22f, h * 0.78f)
            lineTo(w * 0.55f, h * 0.78f)
            moveTo(w * 0.45f, h * 0.22f)
            lineTo(w * 0.78f, h * 0.22f)
            lineTo(w * 0.78f, h * 0.55f)
            moveTo(w * 0.24f, h * 0.76f)
            lineTo(w * 0.76f, h * 0.24f)
        }
        drawPath(path, color = tint, style = stroke)
    }
}

/**
 * Checklist toggle circle with checkmark inside.
 */
@Composable
private fun ChecklistIcon(tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        drawCircle(color = tint, radius = size.minDimension / 2 - strokeWidth, style = Stroke(width = strokeWidth))
        val check = Path().apply {
            moveTo(size.width * 0.27f, size.height * 0.52f)
            lineTo(size.width * 0.44f, size.height * 0.69f)
            lineTo(size.width * 0.73f, size.height * 0.35f)
        }
        drawPath(check, color = tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Circle with plus (+) inside matching the last icon in the screenshots.
 */
@Composable
private fun PlusCircleIcon(tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        drawCircle(color = tint, radius = size.minDimension / 2 - strokeWidth, style = Stroke(width = strokeWidth))
        val half = size.width / 2
        val pad = size.width * 0.28f
        drawLine(tint, Offset(pad, half), Offset(size.width - pad, half), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(tint, Offset(half, pad), Offset(half, size.height - pad), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    }
}

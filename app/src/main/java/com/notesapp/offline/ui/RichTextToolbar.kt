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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
        // Expandable Sub-bar for Typography / Formatting (Underline, Checklist, •, 1.)
        AnimatedVisibility(
            visible = isAaExpanded && showFormatting,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(subPanelBg)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolbarButtonContainer(onClick = onToggleUnderline) {
                        Text("U", color = if (isUnderlineActive) accent else tint, fontSize = 17.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
                    }
                    ToolbarButtonContainer(onClick = onToggleChecklist) {
                        ChecklistIcon(tint = if (isChecklist) accent else tint)
                    }
                    ToolbarButtonContainer(onClick = onBulletLine) {
                        Text("•", color = tint, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    ToolbarButtonContainer(onClick = onNumberedLine) {
                        Text("1.", color = tint, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Expandable Sub-bar for (+) options: Note Color Picker & Extra Tools
        AnimatedVisibility(
            visible = isPlusExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .wrapContentWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(subPanelBg)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
        }

        // Floating pill bottom bar when keyboard is hidden vs Full-width bar when keyboard is open
        if (!isKeyboardOpen) {
            // Floating pill capsule matching second screenshot
            Box(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(pillBg)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pen / Pencil drawing tool
                    ToolbarButtonContainer(onClick = onSketch) {
                        PencilIcon(tint = tint)
                    }

                    // B button (Offline peek & Bold)
                    ToolbarButtonContainer(onClick = onToggleBold) {
                        Text(
                            text = "B",
                            color = if (isBoldActive) accent else tint,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // / button (Covert typing & Italic)
                    ToolbarButtonContainer(onClick = onToggleItalic, onLongClick = onLongPressItalic) {
                        Text(
                            text = "/",
                            color = if (isItalicActive) accent else tint,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic
                        )
                    }

                    // Plus / Add extras
                    ToolbarButtonContainer(onClick = { isPlusExpanded = !isPlusExpanded }) {
                        PlusCircleIcon(tint = if (isPlusExpanded) accent else tint)
                    }
                }
            }
        } else {
            // Full-width bar docked right above keyboard matching first screenshot
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(barBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Pen / Pencil icon
                ToolbarButtonContainer(onClick = onSketch) {
                    PencilIcon(tint = tint)
                }

                // 2. B (Bold & Offline Math Peek)
                ToolbarButtonContainer(onClick = onToggleBold) {
                    Text(
                        text = "B",
                        color = if (isBoldActive) accent else tint,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 3. / (Italic & Covert Typing long-press trigger)
                ToolbarButtonContainer(onClick = onToggleItalic, onLongClick = onLongPressItalic) {
                    Text(
                        text = "/",
                        color = if (isItalicActive) accent else tint,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic
                    )
                }

                // 4. Aa (Formatting expansion: Underline, Checklist, Bullets, Numbers)
                ToolbarButtonContainer(onClick = {
                    isAaExpanded = !isAaExpanded
                    if (isAaExpanded) isPlusExpanded = false
                }) {
                    Text(
                        text = "Aa",
                        color = if (isAaExpanded || isUnderlineActive || isChecklist) accent else tint,
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
 * Standard symmetrical pencil/pen icon with a clean sharp triangular tip,
 * barrel body, cap band, and rounded eraser.
 */
@Composable
private fun PencilIcon(tint: Color) {
    Canvas(modifier = Modifier.size(21.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.8.dp.toPx()
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Mathematical 45-degree angle pencil geometry
        // Point: (0.18, 0.82)
        // Tip Base Left: (0.24, 0.64), Tip Base Right: (0.36, 0.76)
        // Body Top Left: (0.64, 0.24), Body Top Right: (0.76, 0.36)
        // Cap Top: (0.75, 0.15), (0.85, 0.25)

        // 1. Sharp Triangular Tip
        val tipPath = Path().apply {
            moveTo(w * 0.16f, h * 0.84f) // Sharp point
            lineTo(w * 0.23f, h * 0.63f) // Left shoulder
            lineTo(w * 0.37f, h * 0.77f) // Right shoulder
            close()
        }
        drawPath(tipPath, color = tint, style = stroke)

        // Small filled tip lead point
        val leadPath = Path().apply {
            moveTo(w * 0.16f, h * 0.84f)
            lineTo(w * 0.20f, h * 0.73f)
            lineTo(w * 0.27f, h * 0.80f)
            close()
        }
        drawPath(leadPath, color = tint)

        // 2. Main Barrel Body (connected to shoulders)
        val bodyPath = Path().apply {
            moveTo(w * 0.23f, h * 0.63f)
            lineTo(w * 0.64f, h * 0.22f)
            lineTo(w * 0.78f, h * 0.36f)
            lineTo(w * 0.37f, h * 0.77f)
            close()
        }
        drawPath(bodyPath, color = tint, style = stroke)

        // 3. Eraser Cap & Divider Band
        val capBand = Path().apply {
            moveTo(w * 0.54f, h * 0.32f)
            lineTo(w * 0.68f, h * 0.46f)
        }
        drawPath(capBand, color = tint, style = stroke)

        val eraserCap = Path().apply {
            moveTo(w * 0.64f, h * 0.22f)
            lineTo(w * 0.72f, h * 0.14f)
            lineTo(w * 0.86f, h * 0.28f)
            lineTo(w * 0.78f, h * 0.36f)
        }
        drawPath(eraserCap, color = tint, style = stroke)
    }
}

/**
 * Checklist toggle circle with checkmark inside.
 */
@Composable
private fun ChecklistIcon(tint: Color) {
    Canvas(modifier = Modifier.size(21.dp)) {
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

package com.notesapp.offline.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Markdown-lite rich text: **bold**, _italic_, ~underline~ — deliberately
 * distinct delimiter characters (rather than classic *italic*/**bold**) so
 * a single-pass regex can parse them with no ambiguity between bold and
 * italic. List lines are plain text prefixed with "- " (bullet) or "1. "
 * (numbered) — no auto-renumbering, matching a lot of lightweight note
 * apps' actual behavior.
 *
 * This is intentionally NOT a WYSIWYG editor (Compose's TextField doesn't
 * support per-character rich formatting without a lot of custom editing
 * machinery). The raw markers stay visible and editable in the note body;
 * what changes is that they're *styled* live via VisualTransformation, and
 * fully stripped for the clean read-only preview on list cards.
 */
private val richTextRegex = Regex("""\*\*(.+?)\*\*|_(.+?)_|~(.+?)~""")

private fun bulletify(line: String): String {
    val trimmed = line.trimStart()
    val indent = line.length - trimmed.length
    return if (trimmed.startsWith("- ")) {
        line.substring(0, indent) + "•" + trimmed.substring(1)
    } else {
        line
    }
}

/** Same length as input (markers kept) — safe for OffsetMapping.Identity. */
private fun annotateKeepingMarkers(raw: String): AnnotatedString {
    val text = raw.lines().joinToString("\n") { bulletify(it) }
    return buildAnnotated(text, stripMarkers = false)
}

/** Markers and list prefixes stripped — for read-only previews only. */
fun richTextPreview(raw: String): AnnotatedString {
    val text = raw.lines().joinToString("\n") { line ->
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("- ") -> line.substring(0, line.length - trimmed.length) + "• " + trimmed.removePrefix("- ")
            else -> line
        }
    }
    return buildAnnotated(text, stripMarkers = true)
}

private fun buildAnnotated(text: String, stripMarkers: Boolean): AnnotatedString {
    return AnnotatedString.Builder().apply {
        var lastIndex = 0
        for (match in richTextRegex.findAll(text)) {
            append(text.substring(lastIndex, match.range.first))
            val bold = match.groups[1]?.value
            val italic = match.groups[2]?.value
            val underline = match.groups[3]?.value
            when {
                bold != null -> {
                    if (!stripMarkers) append("**")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
                    if (!stripMarkers) append("**")
                }
                italic != null -> {
                    if (!stripMarkers) append("_")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
                    if (!stripMarkers) append("_")
                }
                underline != null -> {
                    if (!stripMarkers) append("~")
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(underline) }
                    if (!stripMarkers) append("~")
                }
            }
            lastIndex = match.range.last + 1
        }
        append(text.substring(lastIndex))
    }.toAnnotatedString()
}

/** Applies rich styling to the editor's own text without changing its length or offsets. */
class RichTextVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(annotateKeepingMarkers(text.text), OffsetMapping.Identity)
    }
}

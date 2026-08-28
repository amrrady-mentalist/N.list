package com.notesapp.offline.data

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Tracks the in-memory state of an active covert typing session inside a note.
 */
data class CovertSessionState(
    var isArmed: Boolean = false,
    var secretBuffer: String = "",
    var capturedSecretWord: String = "",
    var hasCapturedWord: Boolean = false,
    var covertSentenceIndex: Int = 0,
    var consecutiveSpaces: Int = 0
)

/**
 * Engine that powers the Covert Typing effect:
 * 1. Line 1: Converts keyboard input keystroke-by-keystroke into the pre-saved sentence,
 *    capturing the actual typed letters into a hidden secret buffer.
 *    Double-space ("  ") locks in the secret word and triggers the Inject API send.
 * 2. Subsequent Lines: When spectators type on new lines, forces the corresponding letter
 *    of the secret word at the performer's configured position (1st, 2nd, 3rd, or last letter).
 */
object CovertTypingEngine {

    fun processEdit(
        oldValue: TextFieldValue,
        newValue: TextFieldValue,
        config: CovertTypingConfig,
        state: CovertSessionState,
        onWordCaptured: (String) -> Unit
    ): TextFieldValue {
        if (!state.isArmed && !state.hasCapturedWord) {
            return newValue
        }

        val oldText = oldValue.text
        val newText = newValue.text

        // Deletion / Backspace handling
        if (newText.length < oldText.length) {
            val deletedCount = oldText.length - newText.length
            val oldCursor = oldValue.selection.min
            val lineBreakIndex = oldText.indexOf('\n')
            val isEditingFirstLine = lineBreakIndex == -1 || oldCursor <= lineBreakIndex

            if (isEditingFirstLine && state.covertSentenceIndex > 0) {
                state.covertSentenceIndex = (state.covertSentenceIndex - deletedCount).coerceAtLeast(0)
                if (!state.hasCapturedWord && state.secretBuffer.isNotEmpty()) {
                    val dropAmount = deletedCount.coerceAtMost(state.secretBuffer.length)
                    state.secretBuffer = state.secretBuffer.dropLast(dropAmount)
                }
                state.consecutiveSpaces = 0
            }
            return newValue
        }

        // Insertion / Typing handling
        if (newText.length > oldText.length) {
            val cursor = newValue.selection.min
            val insertLength = newText.length - oldText.length
            val startInsert = (cursor - insertLength).coerceAtLeast(0)
            val insertedChars = newText.substring(startInsert, cursor)

            val lineBreakIndex = oldText.indexOf('\n')
            val isEditingFirstLine = lineBreakIndex == -1 || startInsert <= (if (lineBreakIndex == -1) oldText.length else lineBreakIndex)

            if (isEditingFirstLine && !insertedChars.contains('\n')) {
                // Line 1 typing: display preSavedSentence letter-by-letter
                val sentence = config.preSavedSentence.ifBlank { "Notes" }
                val replacementBuilder = StringBuilder()

                for (char in insertedChars) {
                    // Track secret word buffer
                    if (!state.hasCapturedWord) {
                        if (char == ' ') {
                            state.consecutiveSpaces++
                            if (state.consecutiveSpaces >= 2) {
                                val extracted = state.secretBuffer.trim()
                                if (extracted.isNotBlank()) {
                                    state.capturedSecretWord = extracted
                                    state.hasCapturedWord = true
                                    onWordCaptured(extracted)
                                }
                            }
                        } else {
                            state.consecutiveSpaces = 0
                            state.secretBuffer += char
                        }
                    }

                    // Choose character to display on screen
                    val displayChar = if (state.covertSentenceIndex < sentence.length) {
                        val c = sentence[state.covertSentenceIndex]
                        state.covertSentenceIndex++
                        c
                    } else {
                        // Beyond end of sentence: show '.' to notify performer sentence is complete
                        '.'
                    }
                    replacementBuilder.append(displayChar)
                }

                val replacedText = oldText.substring(0, startInsert) + replacementBuilder.toString() + oldText.substring(startInsert)
                val newCursor = startInsert + replacementBuilder.length
                return TextFieldValue(replacedText, TextRange(newCursor))
            } else if (isEditingFirstLine && insertedChars.contains('\n')) {
                // User pressed Enter on Line 1 to proceed to subsequent lines
                if (!state.hasCapturedWord && state.secretBuffer.isNotBlank()) {
                    val extracted = state.secretBuffer.trim()
                    if (extracted.isNotBlank()) {
                        state.capturedSecretWord = extracted
                        state.hasCapturedWord = true
                        onWordCaptured(extracted)
                    }
                }
                return newValue
            }

            // If we are on subsequent lines (Lines 2, 3, 4...) and reveal is enabled
            if (config.revealOnSubsequentLines && state.capturedSecretWord.isNotBlank()) {
                val lines = newText.split('\n')
                var currentPos = 0
                var currentLineIndex = 0
                for (i in lines.indices) {
                    val lineLenWithBreak = lines[i].length + if (i < lines.size - 1) 1 else 0
                    if (startInsert in currentPos..currentPos + lineLenWithBreak) {
                        currentLineIndex = i
                        break
                    }
                    currentPos += lineLenWithBreak
                }

                val spectatorLineIndex = currentLineIndex - 1
                if (spectatorLineIndex >= 0 && spectatorLineIndex < state.capturedSecretWord.length) {
                    val targetChar = state.capturedSecretWord[spectatorLineIndex]
                    val lineStartPos = newText.lastIndexOf('\n', (startInsert - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
                    val colIndex = startInsert - lineStartPos

                    when (config.targetLetterPosition) {
                        CovertLetterPosition.FIRST -> {
                            if (colIndex == 0 && insertedChars.isNotEmpty() && insertedChars != "\n") {
                                val forcedChar = targetChar.uppercaseChar()
                                val newInserted = forcedChar + insertedChars.drop(1)
                                val replacedText = oldText.substring(0, startInsert) + newInserted + oldText.substring(startInsert)
                                return TextFieldValue(replacedText, TextRange(startInsert + newInserted.length))
                            }
                        }
                        CovertLetterPosition.SECOND -> {
                            if (colIndex == 1 && insertedChars.isNotEmpty() && insertedChars != "\n") {
                                val forcedChar = targetChar.lowercaseChar()
                                val newInserted = forcedChar + insertedChars.drop(1)
                                val replacedText = oldText.substring(0, startInsert) + newInserted + oldText.substring(startInsert)
                                return TextFieldValue(replacedText, TextRange(startInsert + newInserted.length))
                            }
                        }
                        CovertLetterPosition.THIRD -> {
                            if (colIndex == 2 && insertedChars.isNotEmpty() && insertedChars != "\n") {
                                val forcedChar = targetChar.lowercaseChar()
                                val newInserted = forcedChar + insertedChars.drop(1)
                                val replacedText = oldText.substring(0, startInsert) + newInserted + oldText.substring(startInsert)
                                return TextFieldValue(replacedText, TextRange(startInsert + newInserted.length))
                            }
                        }
                        CovertLetterPosition.LAST -> {
                            if (insertedChars.contains('\n')) {
                                val lineEndPos = startInsert
                                if (lineEndPos > lineStartPos) {
                                    val lastCharPos = lineEndPos - 1
                                    val forcedChar = targetChar.lowercaseChar()
                                    val modifiedOldText = oldText.substring(0, lastCharPos) + forcedChar + oldText.substring(lastCharPos + 1)
                                    val replacedText = modifiedOldText.substring(0, startInsert) + insertedChars + modifiedOldText.substring(startInsert)
                                    return TextFieldValue(replacedText, TextRange(startInsert + insertedChars.length))
                                }
                            }
                        }
                    }
                }
            }
        }

        return newValue
    }
}

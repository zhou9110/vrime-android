/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.input.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class TextChangeTest {
    @Test fun oneWayInputSurvivesEditorRestartWithoutAReadRevision() {
        val password = EditorState(available = true, readable = false, revision = 9)
        assertTrue(EditorEdit("password", 0, 0, 1, oneWay = true).canApplyTo(password))
        assertTrue(EditorEdit("", 0, 0, 1, oneWay = true, backspace = true).canApplyTo(password))
        assertFalse(EditorEdit("text", 0, 0, 1).canApplyTo(password))
        assertFalse(EditorEdit("text", 0, 0, 1, oneWay = true).canApplyTo(EditorState()))
        val readable = EditorState(available = true, revision = 9)
        assertFalse(EditorEdit("text", 0, 0, 1).canApplyTo(readable))
        assertTrue(EditorEdit("text", 0, 0, 9).canApplyTo(readable))
        assertFalse(EditorEdit("text", 0, 0, 9, oneWay = true).canApplyTo(readable))
    }

    @Test fun editsPreserveSurroundingText() {
        assertEquals(TextChange(2, 2, "中文"), TextChange.between("abcd", "ab中文cd"))
        assertEquals(TextChange(2, 4, ""), TextChange.between("ab中文cd", "abcd"))
        assertEquals(TextChange(0, 2, ""), TextChange.between("中文", ""))
        assertEquals(TextChange(1, 3, "XY"), TextChange.between("abcd", "aXYd"))
    }

    @Test fun emojiReplacementDoesNotSplitSurrogates() {
        assertEquals(TextChange(1, 3, "😃"), TextChange.between("a😀b", "a😃b"))
        for ((before, after) in listOf("😀" to "", "" to "😀", "甲😀乙" to "甲😁乙", "a\\nb" to "a\\n中b")) {
            val edit = TextChange.between(before, after)
            assertEquals(after, before.replaceRange(edit.start, edit.end, edit.replacement))
        }
    }
}

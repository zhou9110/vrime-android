/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.input.remote

import kotlinx.serialization.Serializable

@Serializable
data class EditorState(
    val available: Boolean = false,
    val text: String = "",
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
    val revision: Long = 0,
    val message: String = "请在 Quest 选中输入框",
    val readable: Boolean = true
)

@Serializable
data class EditorEdit(
    val text: String, val selectionStart: Int, val selectionEnd: Int, val revision: Long,
    val oneWay: Boolean = false, val backspace: Boolean = false
) {
    // One-way operations act on the current editor, not on a previously read document.
    fun canApplyTo(state: EditorState): Boolean = state.available && oneWay != state.readable &&
        (oneWay || revision == state.revision)
}

/** Replace only the changed range, preserving surrounding text and spans. Offsets are UTF-16. */
data class TextChange(val start: Int, val end: Int, val replacement: String) {
    companion object {
        fun between(before: String, after: String): TextChange {
            var start = 0
            while (start < minOf(before.length, after.length) && before[start] == after[start]) start++
            // Do not split an emoji's surrogate pair.
            if (start > 0 && start < before.length && before[start].isLowSurrogate()) start--
            var oldEnd = before.length
            var newEnd = after.length
            while (oldEnd > start && newEnd > start && before[oldEnd - 1] == after[newEnd - 1]) {
                oldEnd--; newEnd--
            }
            if (oldEnd < before.length && oldEnd > start && before[oldEnd].isLowSurrogate()) {
                oldEnd++; newEnd++
            }
            return TextChange(start, oldEnd, after.substring(start, newEnd))
        }
    }
}

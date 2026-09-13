/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Variant
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import splitties.views.imageResource

/** Four evenly sized columns. */
private const val ColumnWidth = 0.25f

private const val LabelTextSize = 20f

/**
 * A T9 digit key. The letter group reads as the primary label, since that is what the user
 * looks for, with the digit kept in the corner to preserve the familiar keypad mapping.
 */
private class T9DigitKey(
    val digit: Int,
    val letters: String
) : KeyDef(
    Appearance.AltText(
        displayText = letters,
        altText = digit.toString(),
        textSize = LabelTextSize,
        percentWidth = ColumnWidth
    ),
    setOf(
        Behavior.Press(KeyAction.FcitxKeyAction(digit.toString()))
    ),
    arrayOf(
        Popup.AltPreview(letters, digit.toString())
    )
)

/** Syllable separator, occupying the letter-less `1` position. */
private class T9SeparatorKey : KeyDef(
    Appearance.AltText(
        displayText = "'",
        altText = "1",
        textSize = LabelTextSize,
        percentWidth = ColumnWidth
    ),
    setOf(
        Behavior.Press(KeyAction.FcitxKeyAction("'"))
    ),
    arrayOf(
        Popup.Preview("'")
    )
)

/** Clears the current composition. */
private class T9ClearKey : KeyDef(
    Appearance.Image(
        src = R.drawable.ic_baseline_undo_24,
        percentWidth = ColumnWidth,
        variant = Variant.Alternative
    ),
    setOf(
        Behavior.Press(KeyAction.SymAction(KeySym(FcitxKeyMapping.FcitxKey_Escape)))
    )
)

/**
 * Nine-key (T9) keypad, for input methods whose speller maps pinyin syllables onto digits
 * — for example the RIME schemas `yuyan_t9_pinyin` and `xiaobai_simp`.
 *
 * Each key sends its digit; the letter groups are labels only, so there is no multi-tap or
 * long-press disambiguation to perform here — the engine resolves the ambiguity and offers
 * candidates. Key `1` carries no letters in the standard assignment and is therefore
 * repurposed as the syllable separator (apostrophe), which is what RIME's speller expects.
 */
@SuppressLint("ViewConstructor")
class T9Keyboard(
    context: Context,
    theme: Theme,
) : BaseKeyboard(context, theme, Layout) {

    companion object {
        const val Name = "T9"

        /** Standard phone assignment of letters to digits. Key `1` holds no letters. */
        private val LetterGroups = mapOf(
            2 to "abc", 3 to "def",
            4 to "ghi", 5 to "jkl", 6 to "mno",
            7 to "pqrs", 8 to "tuv", 9 to "wxyz"
        )

        private fun digit(d: Int) = T9DigitKey(d, LetterGroups.getValue(d))

        val Layout: List<List<KeyDef>> = listOf(
            listOf(
                T9SeparatorKey(),
                digit(2),
                digit(3),
                BackspaceKey(ColumnWidth)
            ),
            listOf(
                digit(4),
                digit(5),
                digit(6),
                T9ClearKey()
            ),
            listOf(
                digit(7),
                digit(8),
                digit(9),
                LayoutSwitchKey("ABC", TextKeyboard.Name, ColumnWidth)
            ),
            listOf(
                LayoutSwitchKey(
                    "!?#",
                    PickerWindow.Key.Symbol.name,
                    ColumnWidth,
                    Variant.AltForeground
                ),
                // percentWidth 0f fills the remaining column; keeping the upstream SpaceKey
                // type preserves its swipe-to-move-cursor gesture and sound effect.
                SpaceKey(),
                LayoutSwitchKey("123", NumberKeyboard.Name, ColumnWidth, Variant.AltForeground),
                ReturnKey(ColumnWidth)
            )
        )
    }

    private val `return`: ImageKeyView by lazy { findViewById(R.id.button_return) }

    override fun onReturnDrawableUpdate(returnDrawable: Int) {
        `return`.img.imageResource = returnDrawable
    }
}

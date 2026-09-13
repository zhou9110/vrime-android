/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.input.popup

/** All coordinates are window-relative; callers convert to their own view/gesture origins. */
internal object PopupPlacement {
    fun top(desiredTop: Int, height: Int, windowTop: Int, windowBottom: Int): Int =
        desiredTop.coerceIn(windowTop, (windowBottom - height).coerceAtLeast(windowTop))
}

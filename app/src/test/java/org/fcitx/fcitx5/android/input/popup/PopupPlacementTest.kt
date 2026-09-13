/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.input.popup

import org.junit.Assert.assertEquals
import org.junit.Test

class PopupPlacementTest {
    @Test fun topRowPreviewNeverStartsOutsideWindow() {
        assertEquals(0, PopupPlacement.top(-16, 100, 0, 400))
    }

    @Test fun multirowLongPressAtTopUsesVisibleSpace() {
        assertEquals(0, PopupPlacement.top(-102, 144, 0, 400))
    }

    @Test fun movedWindowKeepsGestureAndDrawingOriginsAligned() {
        val triggerTop = 90
        val popupTop = PopupPlacement.top(-4, 96, 80, 480)
        assertEquals(80, popupTop)
        val gestureOffset = popupTop - triggerTop
        assertEquals(-10, gestureOffset)
        // A gesture 14 px below the trigger origin lies 24 px into the moved popup.
        assertEquals(24, 14 - gestureOffset)
    }

    @Test fun bottomEdgeAndNormalPlacement() {
        assertEquals(304, PopupPlacement.top(370, 96, 0, 400))
        assertEquals(180, PopupPlacement.top(180, 96, 0, 400))
    }

    @Test fun transientUnmeasuredWindowDoesNotThrow() {
        assertEquals(0, PopupPlacement.top(-116, 48, 0, 0))
    }
}

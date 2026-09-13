/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Point
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.ImageView
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.core.view.updateLayoutParams
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.daemon.launchOnReady
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceProvider
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcaster
import org.fcitx.fcitx5.android.input.broadcast.PreeditEmptyStateComponent
import org.fcitx.fcitx5.android.input.broadcast.PunctuationComponent
import org.fcitx.fcitx5.android.input.broadcast.ReturnKeyDrawableComponent
import org.fcitx.fcitx5.android.input.candidates.horizontal.HorizontalCandidateComponent
import org.fcitx.fcitx5.android.input.keyboard.CommonKeyActionListener
import org.fcitx.fcitx5.android.input.keyboard.KeyboardHeightPercentBase.DisplayMetrics
import org.fcitx.fcitx5.android.input.keyboard.KeyboardHeightPercentBase.RealSize
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.picker.emojiPicker
import org.fcitx.fcitx5.android.input.picker.emoticonPicker
import org.fcitx.fcitx5.android.input.picker.symbolPicker
import org.fcitx.fcitx5.android.input.popup.PopupComponent
import org.fcitx.fcitx5.android.input.preedit.PreeditComponent
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.fcitx.fcitx5.android.utils.unset
import org.fcitx.fcitx5.android.utils.windowManager
import org.mechdancer.dependency.DynamicScope
import org.mechdancer.dependency.manager.wrapToUniqueComponent
import org.mechdancer.dependency.plusAssign
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable
import timber.log.Timber

@SuppressLint("ViewConstructor")
class InputView(
    service: FcitxInputMethodService,
    fcitx: FcitxConnection,
    theme: Theme
) : BaseInputView(service, fcitx, theme, service.getSharedPreferences("floating_keyboard", 0)
    .getBoolean("enabled", false)) {

    private val keyBorder by ThemeManager.prefs.keyBorder

    private val customBackground = imageView {
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private val placeholderOnClickListener = OnClickListener { }

    // use clickable view as padding, so MotionEvent can be split to padding view and keyboard view
    private val leftPaddingSpace = view(::View) {
        setOnClickListener(placeholderOnClickListener)
    }
    private val rightPaddingSpace = view(::View) {
        setOnClickListener(placeholderOnClickListener)
    }
    private val bottomPaddingSpace = view(::View) {
        // height as keyboardBottomPadding
        // bottomMargin as WindowInsets (Navigation Bar) offset
        setOnClickListener(placeholderOnClickListener)
    }

    private val scope = DynamicScope()
    private val broadcaster = InputBroadcaster()
    private val popup = PopupComponent()
    private val punctuation = PunctuationComponent()
    private val returnKeyDrawable = ReturnKeyDrawableComponent()
    private val preeditEmptyState = PreeditEmptyStateComponent()
    private val preedit = PreeditComponent()
    private val commonKeyActionListener = CommonKeyActionListener()
    private val windowManager = InputWindowManager()
    private val kawaiiBar = KawaiiBarComponent()
    private val horizontalCandidate = HorizontalCandidateComponent()
    private val keyboardWindow = KeyboardWindow()
    private val symbolPicker = symbolPicker()
    private val emojiPicker = emojiPicker()
    private val emoticonPicker = emoticonPicker()

    private fun setupScope() {
        scope += this@InputView.wrapToUniqueComponent()
        scope += service.wrapToUniqueComponent()
        scope += fcitx.wrapToUniqueComponent()
        scope += theme.wrapToUniqueComponent()
        scope += themedContext.wrapToUniqueComponent()
        scope += broadcaster
        scope += popup
        scope += punctuation
        scope += returnKeyDrawable
        scope += preeditEmptyState
        scope += preedit
        scope += commonKeyActionListener
        scope += windowManager
        scope += kawaiiBar
        scope += horizontalCandidate
        broadcaster.onScopeSetupFinished(scope)
    }

    private val keyboardPrefs = AppPrefs.getInstance().keyboard

    private val focusChangeResetKeyboard by keyboardPrefs.focusChangeResetKeyboard

    private val keyboardHeightPercent = keyboardPrefs.keyboardHeightPercent
    private val keyboardHeightPercentLandscape = keyboardPrefs.keyboardHeightPercentLandscape
    private val keyboardSidePadding = keyboardPrefs.keyboardSidePadding
    private val keyboardSidePaddingLandscape = keyboardPrefs.keyboardSidePaddingLandscape
    private val keyboardBottomPadding = keyboardPrefs.keyboardBottomPadding
    private val keyboardBottomPaddingLandscape = keyboardPrefs.keyboardBottomPaddingLandscape

    private val advancedPrefs = AppPrefs.getInstance().advanced
    private val keyboardHeightPercentBase = advancedPrefs.keyboardHeightPercentBase

    private val keyboardSizePrefs = listOf(
        keyboardHeightPercent,
        keyboardHeightPercentLandscape,
        keyboardSidePadding,
        keyboardSidePaddingLandscape,
        keyboardBottomPadding,
        keyboardBottomPaddingLandscape,
        keyboardHeightPercentBase,
    )

    private val keyboardHeightPx: Int
        get() {
            val baseType = keyboardHeightPercentBase.getValue()
            val base = when (baseType) {
                DisplayMetrics -> resources.displayMetrics.heightPixels
                RealSize -> Point().also {
                    @Suppress("DEPRECATION")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        context.display
                    } else {
                        context.windowManager.defaultDisplay
                    }.getRealSize(it)
                }.y
            }
            val percent = when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> keyboardHeightPercentLandscape
                else -> keyboardHeightPercent
            }.getValue()
            Timber.d("keyboardHeightPx get(): baseType=${baseType}, base=${base}, percent=${percent}")
            return base * percent / 100
        }

    private val keyboardSidePaddingPx: Int
        get() {
            val value = when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> keyboardSidePaddingLandscape
                else -> keyboardSidePadding
            }.getValue()
            return dp(value)
        }

    private val keyboardBottomPaddingPx: Int
        get() {
            val value = when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> keyboardBottomPaddingLandscape
                else -> keyboardBottomPadding
            }.getValue()
            return dp(value)
        }

    @Keep
    private val onKeyboardSizeChangeListener = ManagedPreferenceProvider.OnChangeListener { key ->
        if (keyboardSizePrefs.any { it.key == key }) {
            updateKeyboardSize()
        }
    }

    private val floatingPrefs = service.getSharedPreferences("floating_keyboard", 0)
    var isFloating = floatingPrefs.getBoolean("enabled", false)
        private set
    private var floatingScale = floatingPrefs.getFloat("scale", 0.65f).coerceIn(0.45f, 0.9f)
    private var floatingX = floatingPrefs.getFloat("x", 0.5f).coerceIn(0f, 1f)
    private var floatingY = floatingPrefs.getFloat("y", 0f).coerceIn(0f, 1f)
    private val floatingControls = android.widget.LinearLayout(themedContext).apply {
        id = View.generateViewId()
        setBackgroundColor(theme.barColor)
        gravity = android.view.Gravity.CENTER_VERTICAL
    }

    fun toggleFloating() {
        isFloating = !isFloating
        saveFloating()
        service.recreateKeyboardView()
    }

    val popupScale: Float get() = if (isFloating) floatingScale else 1f

    private fun saveFloating() {
        floatingPrefs.edit().putBoolean("enabled", isFloating)
            .putFloat("scale", floatingScale).putFloat("x", floatingX)
            .putFloat("y", floatingY).apply()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingControls() {
        val drag = android.widget.TextView(themedContext).apply {
            text = context.getString(org.fcitx.fcitx5.android.R.string.floating_drag)
            gravity = android.view.Gravity.CENTER
            setTextColor(theme.keyTextColor)
        }
        floatingControls.addView(drag, android.widget.LinearLayout.LayoutParams(0, -1, 1f))
        var startX = 0f
        var startY = 0f
        var initialX = 0f
        var initialY = 0f
        drag.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startX = e.rawX; startY = e.rawY
                    initialX = floatingX; initialY = floatingY
                    popup.dismissAll()
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    floatingX = (initialX + (e.rawX - startX) /
                        (width - keyboardView.width).coerceAtLeast(1)).coerceIn(0f, 1f)
                    floatingY = (initialY - (e.rawY - startY) / floatingTravel()).coerceIn(0f, 1f)
                    updateFloatingLayout()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> saveFloating()
            }
            true
        }
        fun button(label: String, description: Int, action: () -> Unit) {
            floatingControls.addView(android.widget.Button(themedContext).apply {
                text = label
                contentDescription = context.getString(description)
                setPadding(0, 0, 0, 0)
                setOnClickListener { popup.dismissAll(); action() }
            }, android.widget.LinearLayout.LayoutParams(dp(48), -1))
        }
        button("−", org.fcitx.fcitx5.android.R.string.floating_smaller) {
            floatingScale = (floatingScale - 0.05f).coerceAtLeast(0.45f)
            saveFloating(); updateKeyboardSize()
        }
        button("+", org.fcitx.fcitx5.android.R.string.floating_larger) {
            floatingScale = (floatingScale + 0.05f).coerceAtMost(0.9f)
            saveFloating(); updateKeyboardSize()
        }
        button("↔", org.fcitx.fcitx5.android.R.string.floating_restore) { toggleFloating() }
        addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (isFloating && (r - l != or - ol || b - t != ob - ot)) updateFloatingLayout()
        }
    }

    private fun floatingTravel() = (height - keyboardView.height - dp(40) - preedit.ui.root.height)
        .coerceAtLeast(1).toFloat()

    private fun updateFloatingLayout() {
        if (isFloating && height > 0) {
            val navigationInset = (bottomPaddingSpace.layoutParams as? LayoutParams)?.bottomMargin ?: 0
            val available = (height - dp(KawaiiBarComponent.HEIGHT + 40) - keyboardBottomPaddingPx -
                navigationInset - preedit.ui.root.height).coerceAtLeast(1)
            val desired = (keyboardHeightPx * floatingScale).toInt().coerceAtMost(available)
            if (windowManager.view.layoutParams.height != desired) {
                windowManager.view.updateLayoutParams { height = desired }
            }
        }
        val panelWidth = if (isFloating) (width * floatingScale).toInt()
            .coerceAtLeast(dp(320).coerceAtMost(width)).coerceAtLeast(1) else matchParent
        floatingControls.visibility = if (isFloating) VISIBLE else GONE
        arrayOf(keyboardView, preedit.ui.root, floatingControls).forEach { view ->
            view.updateLayoutParams<LayoutParams> {
                width = panelWidth
                horizontalBias = if (isFloating) floatingX else 0.5f
            }
        }
        keyboardView.updateLayoutParams<LayoutParams> {
            bottomMargin = if (isFloating) (floatingY * floatingTravel()).toInt() else 0
        }
        preedit.ui.root.updateLayoutParams<LayoutParams> {
            bottomToTop = if (isFloating) floatingControls.id else keyboardView.id
        }
    }

    fun floatingTouchableRegion(region: android.graphics.Region) {
        region.setEmpty()
        val location = IntArray(2)
        fun include(view: View) {
            if (view.visibility != VISIBLE || view.width == 0 || view.height == 0) return
            view.getLocationInWindow(location)
            region.op(android.graphics.Rect(location[0], location[1],
                location[0] + view.width, location[1] + view.height), android.graphics.Region.Op.UNION)
        }
        include(keyboardView)
        include(floatingControls)
        include(preedit.ui.root)
        for (i in 0 until popup.root.childCount) include(popup.root.getChildAt(i))
    }

    val keyboardView: View

    init {
        // MUST call before any operation
        setupScope()

        // restore punctuation mapping in case of InputView recreation
        fcitx.launchOnReady {
            punctuation.updatePunctuationMapping(it.statusAreaActionsCached)
        }

        // make sure KeyboardWindow's view has been created before it receives any broadcast
        windowManager.addEssentialWindow(keyboardWindow, createView = true)
        windowManager.addEssentialWindow(symbolPicker)
        windowManager.addEssentialWindow(emojiPicker)
        windowManager.addEssentialWindow(emoticonPicker)
        // show KeyboardWindow by default
        windowManager.attachWindow(KeyboardWindow)

        broadcaster.onImeUpdate(fcitx.runImmediately { inputMethodEntryCached })

        customBackground.imageDrawable = theme.backgroundDrawable(keyBorder)

        keyboardView = constraintLayout {
            // allow MotionEvent to be delivered to keyboard while pressing on padding views.
            // although it should be default for apps targeting Honeycomb (3.0, API 11) and higher,
            // but it's not the case on some devices ... just set it here
            isMotionEventSplittingEnabled = true
            add(customBackground, lParams {
                centerVertically()
                centerHorizontally()
            })
            add(kawaiiBar.view, lParams(matchParent, dp(KawaiiBarComponent.HEIGHT)) {
                topOfParent()
                centerHorizontally()
            })
            add(leftPaddingSpace, lParams {
                below(kawaiiBar.view)
                startOfParent()
                bottomOfParent()
            })
            add(rightPaddingSpace, lParams {
                below(kawaiiBar.view)
                endOfParent()
                bottomOfParent()
            })
            add(windowManager.view, lParams {
                below(kawaiiBar.view)
                above(bottomPaddingSpace)
                /**
                 * set start and end constrain in [updateKeyboardSize]
                 */
            })
            add(bottomPaddingSpace, lParams {
                startToEndOf(leftPaddingSpace)
                endToStartOf(rightPaddingSpace)
                bottomOfParent()
            })
        }

        add(preedit.ui.root, lParams(matchParent, wrapContent) {
            above(keyboardView)
            centerHorizontally()
        })
        add(keyboardView, lParams(matchParent, wrapContent) {
            centerHorizontally()
            bottomOfParent()
        })
        add(floatingControls, lParams(matchParent, dp(40)) {
            above(keyboardView)
            centerHorizontally()
        })
        setupFloatingControls()
        arrayOf(keyboardView, preedit.ui.root).forEach { child ->
            child.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
                if (isFloating && (r - l != or - ol || b - t != ob - ot)) updateFloatingLayout()
            }
        }
        updateKeyboardSize()
        add(popup.root, lParams(matchParent, matchParent) {
            centerVertically()
            centerHorizontally()
        })

        keyboardPrefs.registerOnChangeListener(onKeyboardSizeChangeListener)
        advancedPrefs.registerOnChangeListener(onKeyboardSizeChangeListener)
    }

    private fun updateKeyboardSize() {
        popup.dismissAll()
        minimumHeight = if (isFloating) keyboardHeightPx + dp(KawaiiBarComponent.HEIGHT) + keyboardBottomPaddingPx else 0
        windowManager.view.updateLayoutParams {
            height = if (isFloating) (keyboardHeightPx * floatingScale).toInt() else keyboardHeightPx
        }
        bottomPaddingSpace.updateLayoutParams {
            height = keyboardBottomPaddingPx
        }
        val sidePadding = keyboardSidePaddingPx
        if (sidePadding == 0) {
            // hide side padding space views when unnecessary
            leftPaddingSpace.visibility = GONE
            rightPaddingSpace.visibility = GONE
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToEnd = unset
                endToStart = unset
                startOfParent()
                endOfParent()
            }
        } else {
            leftPaddingSpace.visibility = VISIBLE
            rightPaddingSpace.visibility = VISIBLE
            leftPaddingSpace.updateLayoutParams {
                width = sidePadding
            }
            rightPaddingSpace.updateLayoutParams {
                width = sidePadding
            }
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToStart = unset
                endToEnd = unset
                startToEndOf(leftPaddingSpace)
                endToStartOf(rightPaddingSpace)
            }
        }
        preedit.ui.root.setPadding(sidePadding, 0, sidePadding, 0)
        kawaiiBar.view.setPadding(sidePadding, 0, sidePadding, 0)
        updateFloatingLayout()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        bottomPaddingSpace.updateLayoutParams<LayoutParams> {
            bottomMargin = getNavBarBottomInset(insets)
        }
        return insets
    }

    /**
     * called when [InputView] is about to show, or restart
     */
    fun startInput(info: EditorInfo, capFlags: CapabilityFlags, restarting: Boolean = false) {
        broadcaster.onStartInput(info, capFlags)
        returnKeyDrawable.updateDrawableOnEditorInfo(info)
        if (focusChangeResetKeyboard || !restarting) {
            windowManager.attachWindow(KeyboardWindow)
        }
    }

    override fun onStartHandleFcitxEvent() {
        val inputPanelData = fcitx.runImmediately { inputPanelCached }
        val inputMethodEntry = fcitx.runImmediately { inputMethodEntryCached }
        val statusAreaActions = fcitx.runImmediately { statusAreaActionsCached }
        arrayOf(
            FcitxEvent.InputPanelEvent(inputPanelData),
            FcitxEvent.IMChangeEvent(inputMethodEntry),
            FcitxEvent.StatusAreaEvent(
                FcitxEvent.StatusAreaEvent.Data(statusAreaActions, inputMethodEntry)
            )
        ).forEach { handleFcitxEvent(it) }
    }

    override fun handleFcitxEvent(it: FcitxEvent<*>) {
        when (it) {
            is FcitxEvent.CandidateListEvent -> {
                broadcaster.onCandidateUpdate(it.data)
            }
            is FcitxEvent.ClientPreeditEvent -> {
                preeditEmptyState.updatePreeditEmptyState(clientPreedit = it.data)
                broadcaster.onClientPreeditUpdate(it.data)
            }
            is FcitxEvent.InputPanelEvent -> {
                preeditEmptyState.updatePreeditEmptyState(preedit = it.data.preedit)
                broadcaster.onInputPanelUpdate(it.data)
            }
            is FcitxEvent.IMChangeEvent -> {
                broadcaster.onImeUpdate(it.data)
            }
            is FcitxEvent.StatusAreaEvent -> {
                punctuation.updatePunctuationMapping(it.data.actions)
                broadcaster.onStatusAreaUpdate(it.data.actions)
            }
            else -> {}
        }
    }

    fun updateSelection(start: Int, end: Int) {
        broadcaster.onSelectionUpdate(start, end)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun handleInlineSuggestions(response: InlineSuggestionsResponse): Boolean {
        return kawaiiBar.handleInlineSuggestions(response)
    }

    override fun onDetachedFromWindow() {
        advancedPrefs.unregisterOnChangeListener(onKeyboardSizeChangeListener)
        keyboardPrefs.unregisterOnChangeListener(onKeyboardSizeChangeListener)
        // clear DynamicScope, implies that InputView should not be attached again after detached.
        scope.clear()
        super.onDetachedFromWindow()
    }

}

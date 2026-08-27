package com.termux.lite

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.OverScroller
import com.termux.terminal.TerminalEmulator
import com.termux.view.TerminalView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Intercepts vertical finger motion to provide continuous smooth pixel scrolling
 * for terminal transcript history and full-range fling for TUI apps.
 * Taps, pinch-zoom, and text selection stay on [TerminalView].
 */
class TerminalScrollHost(context: Context) : FrameLayout(context) {

    val terminal: TerminalView = TerminalView(context, null as AttributeSet?)
    var onUrlTap: ((String) -> Unit)? = null
    private var maybeTap = false
    private val scroller = OverScroller(context)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFling = ViewConfiguration.get(context).scaledMaximumFlingVelocity

    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var intercepting = false
    private var remainder = 0f
    private var scrollOffsetY = 0f
    private var velocityTracker: VelocityTracker? = null

    private var flingLastY = 0
    private var flingSeed: MotionEvent? = null
    private var flingMode = FlingMode.History

    private enum class FlingMode { History, Mouse, Arrows }

    init {
        addView(
            terminal,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        isClickable = true
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        abortFling()
        if (terminal.topRow >= 0 || scrollOffsetY <= 0.01f) {
            scrollOffsetY = 0f
            terminal.resetScroll()
        } else {
            val maxScroll = maxTranscriptScroll()
            scrollOffsetY = scrollOffsetY.coerceIn(0f, maxScroll)
            applySmoothScrollOffset(scrollOffsetY, lineSpacing())
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            if (terminal.topRow >= 0 || scrollOffsetY <= 0.01f) {
                scrollOffsetY = 0f
                terminal.resetScroll()
            } else {
                val maxScroll = maxTranscriptScroll()
                scrollOffsetY = scrollOffsetY.coerceIn(0f, maxScroll)
                applySmoothScrollOffset(scrollOffsetY, lineSpacing())
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                abortFling()
                intercepting = false
                maybeTap = true
                downX = ev.x
                downY = ev.y
                lastY = ev.y
                remainder = 0f
                val spacing = lineSpacing()
                if (spacing > 0f && (terminal.topRow < 0 || terminal.subRowOffsetPx > 0f)) {
                    scrollOffsetY = (-terminal.topRow * spacing + terminal.subRowOffsetPx).coerceIn(0f, maxTranscriptScroll())
                } else if (terminal.topRow >= 0) {
                    scrollOffsetY = 0f
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                return false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                intercepting = false
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (ev.pointerCount > 1) return false
                if (terminal.isSelectingText) return false
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) maybeTap = false
                if (abs(dy) > touchSlop && abs(dy) > abs(dx) * 1.15f) {
                    intercepting = true
                    lastY = ev.y
                    remainder = 0f
                    val cancel = MotionEvent.obtain(ev)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    terminal.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                intercepting = false
            }
        }
        return false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (AppState.drawerOpen) {
            if (ev.actionMasked == MotionEvent.ACTION_UP) {
                AppState.pendingDrawerClose = true
            }
            return true
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                maybeTap = true
                downX = ev.x
                downY = ev.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(ev.x - downX) > touchSlop || abs(ev.y - downY) > touchSlop) {
                    maybeTap = false
                }
            }
            MotionEvent.ACTION_UP -> {
                if (maybeTap && !intercepting && !terminal.isSelectingText) {
                    val url = UrlAtTap.find(terminal, ev)
                    Log.i(
                        "TermuxLite",
                        "tap(host): url=$url intercepting=$intercepting selecting=${terminal.isSelectingText}"
                    )
                    if (url != null) {
                        val cancel = MotionEvent.obtain(ev)
                        cancel.action = MotionEvent.ACTION_CANCEL
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                        maybeTap = false
                        onUrlTap?.invoke(url)
                        return true
                    }
                }
                maybeTap = false
            }
            MotionEvent.ACTION_CANCEL -> maybeTap = false
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!intercepting && event.actionMasked != MotionEvent.ACTION_DOWN) {
            return super.onTouchEvent(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                ensureTracker().addMovement(event)
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                ensureTracker().addMovement(event)
                if (event.pointerCount > 1) return true
                val dy = lastY - event.y
                lastY = event.y
                applyScroll(event, dy)
                return true
            }
            MotionEvent.ACTION_UP -> {
                ensureTracker().addMovement(event)
                ensureTracker().computeCurrentVelocity(1000, maxFling.toFloat())
                val vy = ensureTracker().getYVelocity(event.getPointerId(0))
                recycleTracker()
                intercepting = false
                if (abs(vy) >= minFling) {
                    startFling(event, -vy)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                recycleTracker()
                intercepting = false
                abortFling()
                return true
            }
        }
        return true
    }

    override fun computeScroll() {
        if (!scroller.computeScrollOffset()) return
        when (flingMode) {
            FlingMode.History -> {
                val newY = scroller.currY.toFloat()
                val maxScroll = maxTranscriptScroll()
                scrollOffsetY = newY.coerceIn(0f, maxScroll)
                applySmoothScrollOffset(scrollOffsetY, lineSpacing())
            }
            FlingMode.Mouse, FlingMode.Arrows -> {
                val newY = scroller.currY
                val diff = newY - flingLastY
                flingLastY = newY
                if (diff != 0) {
                    applyRows(flingSeed, diff)
                }
            }
        }
        if (!scroller.isFinished) {
            postInvalidateOnAnimation()
        }
    }

    private fun startFling(event: MotionEvent, velocityY: Float) {
        abortFling()
        val emu = terminal.mEmulator ?: return
        val spacing = lineSpacing()
        if (spacing <= 0f) return
        flingSeed?.recycle()
        flingSeed = MotionEvent.obtain(event)
        val vy = velocityY.coerceIn(-maxFling.toFloat(), maxFling.toFloat())

        flingMode = when {
            emu.isMouseTrackingActive -> FlingMode.Mouse
            emu.isAlternateBufferActive -> FlingMode.Arrows
            else -> FlingMode.History
        }
        when (flingMode) {
            FlingMode.History -> {
                val maxScroll = maxTranscriptScroll()
                // Fling operates directly in pixels for smooth deceleration
                scroller.fling(
                    0,
                    scrollOffsetY.roundToInt(),
                    0,
                    (-vy).roundToInt(),
                    0,
                    0,
                    0,
                    maxScroll.roundToInt()
                )
            }
            FlingMode.Mouse, FlingMode.Arrows -> {
                val rowsPerSecond = (vy / spacing).roundToInt()
                val cap = emu.mRows * 4
                scroller.fling(0, 0, 0, rowsPerSecond, 0, 0, -cap, cap)
                flingLastY = 0
            }
        }
        postInvalidateOnAnimation()
    }

    private fun applyScroll(event: MotionEvent, distanceY: Float) {
        val emu = terminal.mEmulator ?: return
        val spacing = lineSpacing()
        if (spacing <= 0f) return

        val isTui = emu.isMouseTrackingActive || emu.isAlternateBufferActive
        if (isTui) {
            val total = distanceY + remainder
            val rows = (total / spacing).toInt()
            remainder = total - rows * spacing
            if (rows != 0) applyRows(event, rows)
        } else {
            // Dragging down (distanceY < 0) scrolls up into history (scrollOffsetY increases).
            // Dragging up (distanceY > 0) scrolls down towards live prompt (scrollOffsetY decreases).
            val maxScroll = maxTranscriptScroll()
            scrollOffsetY = (scrollOffsetY - distanceY).coerceIn(0f, maxScroll)
            applySmoothScrollOffset(scrollOffsetY, spacing)
        }
    }

    private fun applySmoothScrollOffset(offsetY: Float, spacing: Float) {
        if (spacing <= 0f) return
        if (offsetY <= 0.001f) {
            terminal.setSmoothScroll(0, 0f)
            return
        }
        val exactRows = offsetY / spacing
        val rowCount = exactRows.toInt()
        val subOffset = offsetY - rowCount * spacing
        terminal.setSmoothScroll(-rowCount, subOffset)
    }

    private fun applyRows(event: MotionEvent?, rowsDown: Int) {
        if (rowsDown == 0) return
        val tv = terminal
        val emu = tv.mEmulator ?: return
        val up = rowsDown < 0
        val amount = abs(rowsDown)
        when {
            emu.isMouseTrackingActive -> {
                val button = if (up) {
                    TerminalEmulator.MOUSE_WHEELUP_BUTTON
                } else {
                    TerminalEmulator.MOUSE_WHEELDOWN_BUTTON
                }
                val cr = if (event != null) {
                    tv.getColumnAndRow(event, false)
                } else {
                    intArrayOf(1, 1)
                }
                val x = cr[0] + 1
                val y = cr[1] + 1
                repeat(amount) {
                    emu.sendMouseEvent(button, x, y, true)
                }
            }
            emu.isAlternateBufferActive -> {
                val session = tv.mTermSession ?: return
                val seq = if (up) "\u001b[A" else "\u001b[B"
                repeat(amount) { session.write(seq) }
            }
            else -> {
                val spacing = lineSpacing()
                scrollOffsetY = (scrollOffsetY + rowsDown * spacing).coerceIn(0f, maxTranscriptScroll())
                applySmoothScrollOffset(scrollOffsetY, spacing)
            }
        }
    }

    private fun maxTranscriptScroll(): Float {
        val spacing = lineSpacing()
        val emu = terminal.mEmulator ?: return 0f
        return emu.screen.activeTranscriptRows * spacing
    }

    private fun lineSpacing(): Float {
        val r = terminal.mRenderer
        if (r != null && r.fontLineSpacing > 0) return r.fontLineSpacing.toFloat()
        val emu = terminal.mEmulator ?: return 0f
        val rows = emu.mRows.coerceAtLeast(1)
        val h = terminal.height
        return if (h > 0) h.toFloat() / rows else 0f
    }

    private fun abortFling() {
        if (!scroller.isFinished) scroller.abortAnimation()
        flingSeed?.recycle()
        flingSeed = null
    }

    private fun ensureTracker(): VelocityTracker {
        val existing = velocityTracker
        if (existing != null) return existing
        val created = VelocityTracker.obtain()
        velocityTracker = created
        return created
    }

    private fun recycleTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    override fun onDetachedFromWindow() {
        abortFling()
        recycleTracker()
        super.onDetachedFromWindow()
    }
}

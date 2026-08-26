package com.termux.lite

import android.content.Context
import android.util.AttributeSet
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
 * Intercepts vertical finger motion so terminal history and TUI apps (Grok CLI)
 * get a full-range fling instead of Termux's 0.25× / ±half-screen scroll.
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
    private var velocityTracker: VelocityTracker? = null

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
        if (terminal.topRow >= 0) {
            terminal.topRow = 0
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed && terminal.topRow >= 0) {
            terminal.topRow = 0
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
        val newY = scroller.currY
        val diff = newY - flingLastY
        flingLastY = newY
        if (diff != 0) {
            applyRows(flingSeed, diff)
        }
        if (!scroller.isFinished) {
            postInvalidateOnAnimation()
        }
    }

    private var flingLastY = 0
    private var flingSeed: MotionEvent? = null
    private var flingMode = FlingMode.History

    private enum class FlingMode { History, Mouse, Arrows }

    private fun startFling(event: MotionEvent, velocityY: Float) {
        abortFling()
        val emu = terminal.mEmulator ?: return
        val spacing = lineSpacing()
        if (spacing <= 0f) return
        flingSeed?.recycle()
        flingSeed = MotionEvent.obtain(event)
        val vy = velocityY.coerceIn(-maxFling.toFloat(), maxFling.toFloat())
        // OverScroller Y-axis here is measured in terminal ROWS, so convert the
        // px/s finger velocity to rows/s — otherwise a normal flick reads as
        // thousands of rows/s and slams into the top/bottom bound.
        val rowsPerSecond = (vy / spacing).roundToInt()
        flingMode = when {
            emu.isMouseTrackingActive -> FlingMode.Mouse
            emu.isAlternateBufferActive -> FlingMode.Arrows
            else -> FlingMode.History
        }
        when (flingMode) {
            FlingMode.History -> {
                val min = -emu.screen.activeTranscriptRows
                scroller.fling(0, terminal.topRow, 0, rowsPerSecond, 0, 0, min, 0)
                flingLastY = terminal.topRow
            }
            FlingMode.Mouse, FlingMode.Arrows -> {
                val cap = emu.mRows * 4
                scroller.fling(0, 0, 0, rowsPerSecond, 0, 0, -cap, cap)
                flingLastY = 0
            }
        }
        postInvalidateOnAnimation()
    }

    private fun applyScroll(event: MotionEvent, distanceY: Float) {
        val spacing = lineSpacing()
        if (spacing <= 0f) return
        val total = distanceY + remainder
        val rows = (total / spacing).toInt()
        remainder = total - rows * spacing
        if (rows != 0) applyRows(event, rows)
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
                // One arrow per row keeps TUI scrolling proportional to the
                // finger; page-up/down bursts made fast drags jump whole pages.
                val seq = if (up) "\u001b[A" else "\u001b[B"
                repeat(amount) { session.write(seq) }
            }
            else -> {
                val min = -emu.screen.activeTranscriptRows
                tv.topRow = (tv.topRow + rowsDown).coerceIn(min, 0)
                tv.invalidate()
            }
        }
    }

    private fun lineSpacing(): Float {
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

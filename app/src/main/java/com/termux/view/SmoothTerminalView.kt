package com.termux.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import com.termux.terminal.TerminalEmulator

/**
 * TerminalView subclass that supports continuous, sub-row pixel scrolling.
 */
class SmoothTerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TerminalView(context, attrs) {

    private var smoothRenderer: SmoothTerminalRenderer? = null

    /**
     * Vertical pixel offset within the current top line row in [0, lineSpacing).
     * When > 0, the text content is shifted downwards by this many pixels so that
     * the bottom of row (mTopRow - 1) is partially visible at the top edge.
     */
    var subRowOffsetPx: Float = 0f
        private set

    /**
     * Updates both the base integer top row and the sub-row pixel offset,
     * then schedules a redraw.
     */
    fun setSmoothScroll(topRow: Int, subRowOffset: Float) {
        val emu = mEmulator
        val min = if (emu != null) -emu.screen.activeTranscriptRows else 0
        mTopRow = topRow.coerceIn(min, 0)
        subRowOffsetPx = if (mTopRow <= min) 0f else subRowOffset.coerceAtLeast(0f)
        invalidate()
    }

    /**
     * Resets scrolling to the bottom (live terminal output).
     */
    fun resetScroll() {
        mTopRow = 0
        subRowOffsetPx = 0f
        invalidate()
    }

    /**
     * Returns the current single line spacing in pixels.
     */
    fun lineSpacing(): Float {
        val r = mRenderer ?: return 0f
        return r.fontLineSpacing.toFloat()
    }

    /**
     * Returns the number of available history rows in the transcript buffer.
     */
    fun activeTranscriptRows(): Int {
        val emu = mEmulator ?: return 0
        return emu.screen.activeTranscriptRows
    }

    private fun ensureSmoothRenderer(): SmoothTerminalRenderer? {
        val baseRenderer = mRenderer ?: return null
        var current = smoothRenderer
        if (current == null || current.mTextSize != baseRenderer.mTextSize || current.mTypeface != baseRenderer.mTypeface) {
            current = SmoothTerminalRenderer(baseRenderer.mTextSize, baseRenderer.mTypeface)
            smoothRenderer = current
        }
        return current
    }

    override fun onDraw(canvas: Canvas) {
        val emu = mEmulator
        if (emu == null) {
            canvas.drawColor(-0x1000000)
            return
        }

        val renderer = ensureSmoothRenderer()
        if (renderer == null) {
            super.onDraw(canvas)
            return
        }

        val sel = mDefaultSelectors
        mTextSelectionCursorController?.getSelectors(sel)

        renderer.renderSmooth(
            emu,
            canvas,
            mTopRow,
            subRowOffsetPx,
            sel[0],
            sel[1],
            sel[2],
            sel[3]
        )

        renderTextSelection()
    }

    override fun getColumnAndRow(event: MotionEvent, relativeToScroll: Boolean): IntArray {
        val r = mRenderer ?: return super.getColumnAndRow(event, relativeToScroll)
        val col = (event.x / r.fontWidth).toInt()
        var row = ((event.y - subRowOffsetPx - r.mFontLineSpacingAndAscent) / r.fontLineSpacing).toInt()
        if (relativeToScroll) {
            row += mTopRow
        }
        return intArrayOf(col, row)
    }

    override fun getPointY(cy: Int): Int {
        val r = mRenderer ?: return super.getPointY(cy)
        return Math.round((cy - mTopRow) * r.fontLineSpacing + subRowOffsetPx)
    }

    override fun getCursorY(y: Float): Int {
        val r = mRenderer ?: return super.getCursorY(y)
        return (((y - subRowOffsetPx - 40f) / r.fontLineSpacing) + mTopRow).toInt()
    }

    override fun onScreenUpdated(skipScrolling: Boolean) {
        val emu = mEmulator ?: return

        val rowsInHistory = emu.screen.activeTranscriptRows
        if (mTopRow < -rowsInHistory) {
            mTopRow = -rowsInHistory
            subRowOffsetPx = 0f
        }

        var shouldSkip = skipScrolling
        if (isSelectingText || emu.isAutoScrollDisabled) {
            val rowShift = emu.scrollCounter
            if (-mTopRow + rowShift > rowsInHistory) {
                if (isSelectingText) stopTextSelectionMode()
                if (emu.isAutoScrollDisabled) {
                    mTopRow = -rowsInHistory
                    subRowOffsetPx = 0f
                    shouldSkip = true
                }
            } else {
                shouldSkip = true
                mTopRow -= rowShift
                decrementYTextSelectionCursors(rowShift)
            }
        }

        if (!shouldSkip && mTopRow == 0 && subRowOffsetPx == 0f) {
            // User was at the bottom, stay at the bottom.
            mTopRow = 0
            subRowOffsetPx = 0f
        }

        emu.clearScrollCounter()
        invalidate()
    }
}

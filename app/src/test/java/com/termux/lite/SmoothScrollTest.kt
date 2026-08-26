package com.termux.lite

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.floor

class SmoothScrollTest {

    @Test
    fun `smooth scroll offset calculation at exact boundaries`() {
        val spacing = 40f
        val maxTranscriptRows = 100
        val maxScrollY = maxTranscriptRows * spacing

        // At bottom
        var offsetY = 0f
        var exactRows = offsetY / spacing
        var rowCount = exactRows.toInt()
        var subOffset = offsetY - rowCount * spacing
        assertEquals(0, rowCount)
        assertEquals(0f, subOffset, 0.001f)

        // Half a line up
        offsetY = 20f
        exactRows = offsetY / spacing
        rowCount = exactRows.toInt()
        subOffset = offsetY - rowCount * spacing
        assertEquals(0, rowCount)
        assertEquals(20f, subOffset, 0.001f)

        // Exactly one line up
        offsetY = 40f
        exactRows = offsetY / spacing
        rowCount = exactRows.toInt()
        subOffset = offsetY - rowCount * spacing
        assertEquals(1, rowCount)
        assertEquals(0f, subOffset, 0.001f)

        // 2.5 lines up
        offsetY = 100f
        exactRows = offsetY / spacing
        rowCount = exactRows.toInt()
        subOffset = offsetY - rowCount * spacing
        assertEquals(2, rowCount)
        assertEquals(20f, subOffset, 0.001f)

        // At max transcript top
        offsetY = maxScrollY
        exactRows = offsetY / spacing
        rowCount = exactRows.toInt()
        subOffset = offsetY - rowCount * spacing
        assertEquals(100, rowCount)
        assertEquals(0f, subOffset, 0.001f)
    }

    @Test
    fun `continuity across line boundary`() {
        val spacing = 40f
        val baseAscent = 45f

        // Just before 1-row boundary (39.9f)
        val y1 = 39.9f
        val rows1 = y1.toInt() / spacing.toInt()
        val sub1 = y1 - rows1 * spacing
        val topRow1 = -rows1
        val baselineRow0_1 = baseAscent + sub1

        // Just after 1-row boundary (40.1f)
        val y2 = 40.1f
        val rows2 = y2.toInt() / spacing.toInt()
        val sub2 = y2 - rows2 * spacing
        val topRow2 = -rows2
        val baselineRow0_2 = baseAscent + spacing + sub2

        assertEquals(0, topRow1)
        assertEquals(-1, topRow2)
        assertEquals(84.9f, baselineRow0_1, 0.01f)
        assertEquals(85.1f, baselineRow0_2, 0.01f)
        // Delta matches the 0.2f delta continuously!
        assertEquals(0.2f, baselineRow0_2 - baselineRow0_1, 0.01f)
    }
}

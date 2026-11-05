package com.motrixlite.downloader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentPlannerTest {

    @Test
    fun `plans contiguous segments covering the whole file`() {
        val total = 12L * 1024 * 1024
        val segments = SegmentPlanner.plan(total, 8)
        assertTrue(segments.size in 2..8)
        assertEquals(total, segments.sumOf { it.length })
        assertEquals(0L, segments.first().start)
        assertEquals(total - 1, segments.last().end)
        segments.zipWithNext().forEach { (a, b) ->
            assertEquals(a.end + 1, b.start)
        }
    }

    @Test
    fun `small files and single connection use one segment`() {
        assertEquals(1, SegmentPlanner.plan(1024 * 1024L, 8).size)
        assertEquals(1, SegmentPlanner.plan(10 * 1024 * 1024L, 1).size)
    }

    @Test
    fun `unknown size yields single open segment`() {
        val segments = SegmentPlanner.plan(-1, 8)
        assertEquals(1, segments.size)
        assertEquals(-1L, segments[0].end)
    }

    @Test
    fun `empty file is handled by caller and planner returns single range`() {
        val segments = SegmentPlanner.plan(0, 8)
        assertEquals(1, segments.size)
    }
}
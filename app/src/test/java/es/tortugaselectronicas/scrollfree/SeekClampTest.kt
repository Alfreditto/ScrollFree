package es.tortugaselectronicas.scrollfree

import es.tortugaselectronicas.scrollfree.ui.player.SEEK_STEP_MS
import es.tortugaselectronicas.scrollfree.ui.player.clampSeekTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class SeekClampTest {

    @Test
    fun `forward seek within duration moves exactly 10s`() {
        val target = clampSeekTarget(positionMs = 5_000L, deltaMs = SEEK_STEP_MS, durationMs = 60_000L)
        assertEquals(15_000L, target)
    }

    @Test
    fun `backward seek near start is clamped to zero`() {
        val target = clampSeekTarget(positionMs = 5_000L, deltaMs = -SEEK_STEP_MS, durationMs = 60_000L)
        assertEquals(0L, target)
    }

    @Test
    fun `forward seek near end is clamped to duration`() {
        val target = clampSeekTarget(positionMs = 55_000L, deltaMs = SEEK_STEP_MS, durationMs = 60_000L)
        assertEquals(60_000L, target)
    }

    @Test
    fun `negative delta below zero stays at zero`() {
        val target = clampSeekTarget(positionMs = 3_000L, deltaMs = -20_000L, durationMs = 60_000L)
        assertEquals(0L, target)
    }

    @Test
    fun `unknown duration only clamps lower bound`() {
        val target = clampSeekTarget(positionMs = 1_000L, deltaMs = -SEEK_STEP_MS, durationMs = 0L)
        assertEquals(0L, target)
    }
}

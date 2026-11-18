package app.veltrix.island.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesAndSortsTimestampedLines() {
        val lines = LrcParser.parse("""
            [00:02.50]Second line
            [00:01.2]First line
            [00:03:125]Third line
        """.trimIndent())

        assertEquals(listOf("First line", "Second line", "Third line"), lines.map { it.text })
        assertEquals(1_200L, lines[0].timeMs)
        assertEquals(2_500L, lines[1].timeMs)
        assertEquals(3_125L, lines[2].timeMs)
    }

    @Test
    fun supportsMultipleTimestampsOnOneLyricLine() {
        val lines = LrcParser.parse("[00:01.00][00:02.50]Repeated line")
        assertEquals(2, lines.size)
        assertEquals(1_000L, lines[0].timeMs)
        assertEquals(2_500L, lines[1].timeMs)
        assertEquals(listOf("Repeated line", "Repeated line"), lines.map { it.text })
    }

    @Test
    fun picksLatestLineAtPlaybackPosition() {
        val lines = listOf(
            LrcLine(1_000L, "One"),
            LrcLine(2_000L, "Two"),
            LrcLine(3_000L, "Three")
        )

        assertEquals(0, LrcParser.activeIndex(lines, 1_999L))
        assertEquals(1, LrcParser.activeIndex(lines, 2_000L))
        assertEquals(2, LrcParser.activeIndex(lines, 9_000L))
        assertEquals(-1, LrcParser.activeIndex(emptyList(), 9_000L))
    }
}

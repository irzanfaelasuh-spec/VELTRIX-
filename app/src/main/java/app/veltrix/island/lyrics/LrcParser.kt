package app.veltrix.island.lyrics

data class LrcLine(val timeMs: Long, val text: String)

object LrcParser {
    private val stamp = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")

    fun parse(source: String): List<LrcLine> {
        val output = mutableListOf<LrcLine>()
        source.lineSequence().forEach { raw ->
            val line = raw.trim()
            val matches = stamp.findAll(line).toList()
            if (matches.isEmpty() || matches.first().range.first != 0) return@forEach
            val textStart = matches.last().range.last + 1
            val text = line.substring(textStart).trim()
            if (text.isEmpty()) return@forEach

            matches.forEach timestampLoop@{ match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: return@timestampLoop
                val seconds = match.groupValues[2].toLongOrNull() ?: return@timestampLoop
                val fractionRaw = match.groupValues[3]
                val fraction = when (fractionRaw.length) {
                    1 -> (fractionRaw.toLongOrNull() ?: 0L) * 100L
                    2 -> (fractionRaw.toLongOrNull() ?: 0L) * 10L
                    3 -> fractionRaw.toLongOrNull() ?: 0L
                    else -> 0L
                }
                output += LrcLine(minutes * 60_000L + seconds * 1_000L + fraction, text)
            }
        }
        return output.sortedBy { it.timeMs }
    }

    fun activeIndex(lines: List<LrcLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        return lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
    }
}

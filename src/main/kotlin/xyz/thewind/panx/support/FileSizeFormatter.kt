package xyz.thewind.panx.support

import java.util.Locale
import kotlin.math.abs

object FileSizeFormatter {
    private val units = listOf("B", "KB", "MB", "GB", "TB")

    fun format(bytes: Long): String {
        var value = abs(bytes.toDouble())
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex += 1
        }
        val signed = if (bytes < 0) -value else value
        return String.format(Locale.US, "%.2f %s", signed, units[unitIndex])
    }
}

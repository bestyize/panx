package xyz.thewind.panx.support

object FilenameSanitizer {
    fun sanitize(raw: String): String = raw
        .trim()
        .replace("\\", "_")
        .replace("/", "_")
        .replace(Regex("\\s+"), " ")
        .take(255)
}

package com.majeedar.beandoc.ml

object Labels {
    /** One label per line, in model output order. Tolerates CRLF line endings and blank lines. */
    fun parse(text: String): List<String> {
        val labels = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        require(labels.isNotEmpty()) { "labels.txt is empty" }
        require(labels.toSet().size == labels.size) { "labels.txt contains duplicate labels: $labels" }
        return labels
    }
}

package com.majeedar.beandoc.ml

import org.junit.Assert.assertEquals
import org.junit.Test

class LabelsTest {
    private val expected = listOf("angular_leaf_spot", "bean_rust", "healthy")

    @Test
    fun parsesUnixLineEndings() {
        assertEquals(expected, Labels.parse("angular_leaf_spot\nbean_rust\nhealthy"))
    }

    @Test
    fun trimsWindowsLineEndingsAndTrailingNewline() {
        assertEquals(expected, Labels.parse("angular_leaf_spot\r\nbean_rust\r\nhealthy\r\n"))
    }

    @Test
    fun skipsBlankLinesAndSurroundingSpaces() {
        assertEquals(expected, Labels.parse("\n  angular_leaf_spot \n\nbean_rust\n healthy\n\n"))
    }

    @Test
    fun acceptsAnyNumberOfLabels() {
        assertEquals(4, Labels.parse("a\nb\nc\nnot_bean_leaf").size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyFile() {
        Labels.parse(" \r\n \n")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicates() {
        Labels.parse("healthy\nbean_rust\nhealthy")
    }
}

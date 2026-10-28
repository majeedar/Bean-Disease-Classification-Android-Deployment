package com.majeedar.beandoc.data

import com.majeedar.beandoc.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiseaseInfoTest {
    private val strings = mapOf(
        "bean_rust_title" to "Bean rust",
        "bean_rust_what_you_see" to "Rusty spots.",
        "bean_rust_how_it_spreads" to "Wind.",
    )
    private val arrays = mapOf(
        "bean_rust_do_now" to listOf("Remove leaves."),
        "bean_rust_prevent" to listOf("Rotate crops.", "Space plants."),
    )

    private fun lookup(label: String) = DiseaseInfo.lookup(label, strings::get, arrays::get)

    @Test
    fun knownLabelHasSectionsInOrder() {
        val info = lookup("bean_rust")
        assertEquals("Bean rust", info.title)
        assertTrue(info.hasAdvice)
        assertEquals(
            listOf(R.string.section_what_you_see, R.string.section_how_it_spreads, R.string.section_do_now, R.string.section_prevent),
            info.sections.map { it.heading },
        )
        assertEquals("Rusty spots.", info.sections[0].text)
        assertNull(info.sections[2].text)
        assertEquals(listOf("Rotate crops.", "Space plants."), info.sections[3].bullets)
    }

    @Test
    fun unknownLabelFallsBackWithoutCrashing() {
        val info = lookup("not_bean_leaf")
        assertEquals("Not bean leaf", info.title)
        assertFalse(info.hasAdvice)
        assertTrue(info.sections.isEmpty())
    }

    @Test
    fun emptyEntriesAreSkipped() {
        val info = DiseaseInfo.lookup(
            "x",
            string = { if (it == "x_what_you_see") " " else null },
            array = { if (it == "x_do_now") emptyList() else null },
        )
        assertFalse(info.hasAdvice)
    }
}

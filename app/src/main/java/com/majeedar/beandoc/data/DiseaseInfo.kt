package com.majeedar.beandoc.data

import android.annotation.SuppressLint
import android.content.res.Resources
import androidx.annotation.StringRes
import com.majeedar.beandoc.R

/**
 * Advice for one label, read from strings.xml by naming convention so a new label in labels.txt
 * only needs new strings, not code. Any label without strings falls back to a generic message.
 */
data class DiseaseInfo(
    val label: String,
    val title: String,
    val sections: List<Section>,
    val hasAdvice: Boolean,
) {
    data class Section(@StringRes val heading: Int, val text: String?, val bullets: List<String>)

    companion object {
        /** Section order and the string / string-array suffix each one uses. */
        private val SECTIONS = listOf(
            SectionKey("what_you_see", R.string.section_what_you_see, isList = false),
            SectionKey("how_it_spreads", R.string.section_how_it_spreads, isList = false),
            SectionKey("do_now", R.string.section_do_now, isList = true),
            SectionKey("prevent", R.string.section_prevent, isList = true),
            SectionKey("keep_healthy", R.string.section_keep_healthy, isList = true),
        )

        private class SectionKey(val suffix: String, @StringRes val heading: Int, val isList: Boolean)

        /** Pure lookup, testable without Android: [string] and [array] return null for missing names. */
        fun lookup(
            label: String,
            string: (String) -> String?,
            array: (String) -> List<String>?,
        ): DiseaseInfo {
            val sections = SECTIONS.mapNotNull { key ->
                val name = "${label}_${key.suffix}"
                if (key.isList) {
                    array(name)?.takeIf { it.isNotEmpty() }?.let { Section(key.heading, null, it) }
                } else {
                    string(name)?.takeIf { it.isNotBlank() }?.let { Section(key.heading, it, emptyList()) }
                }
            }
            return DiseaseInfo(
                label = label,
                title = string("${label}_title") ?: fallbackTitle(label),
                sections = sections,
                hasAdvice = sections.isNotEmpty(),
            )
        }

        /** "not_bean_leaf" → "Not bean leaf". */
        fun fallbackTitle(label: String): String =
            label.replace('_', ' ').trim().replaceFirstChar { it.uppercaseChar() }

        @SuppressLint("DiscouragedApi") // names come from labels.txt; resources kept by res/raw/keep.xml
        fun forLabel(resources: Resources, packageName: String, label: String): DiseaseInfo = lookup(
            label = label,
            string = { name ->
                resources.getIdentifier(name, "string", packageName).takeIf { it != 0 }?.let(resources::getString)
            },
            array = { name ->
                resources.getIdentifier(name, "array", packageName).takeIf { it != 0 }
                    ?.let { resources.getStringArray(it).toList() }
            },
        )
    }
}

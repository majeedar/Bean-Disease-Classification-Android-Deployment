package com.majeedar.beandoc.ml

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfidenceLevelTest {
    @Test
    fun veryLikelyFromThresholdUp() {
        assertEquals(ConfidenceLevel.VERY_LIKELY, ConfidenceLevel.from(0.85f))
        assertEquals(ConfidenceLevel.VERY_LIKELY, ConfidenceLevel.from(0.97f))
        assertEquals(ConfidenceLevel.VERY_LIKELY, ConfidenceLevel.from(1f))
    }

    @Test
    fun likelyBetweenThresholds() {
        assertEquals(ConfidenceLevel.LIKELY, ConfidenceLevel.from(0.70f))
        assertEquals(ConfidenceLevel.LIKELY, ConfidenceLevel.from(0.78f))
        assertEquals(ConfidenceLevel.LIKELY, ConfidenceLevel.from(0.8499f))
    }

    @Test
    fun notSureBelowSeventyPercent() {
        assertEquals(ConfidenceLevel.NOT_SURE, ConfidenceLevel.from(0.6999f))
        assertEquals(ConfidenceLevel.NOT_SURE, ConfidenceLevel.from(0.34f))
        assertEquals(ConfidenceLevel.NOT_SURE, ConfidenceLevel.from(0f))
    }
}

package com.majeedar.beandoc.ml

import org.junit.Assert.assertEquals
import org.junit.Test

class PredictionTest {
    private val labels = listOf("angular_leaf_spot", "bean_rust", "healthy")

    @Test
    fun argmaxFindsLargestValue() {
        assertEquals(0, argmax(floatArrayOf(0.9f, 0.05f, 0.05f)))
        assertEquals(1, argmax(floatArrayOf(0.01f, 0.98f, 0.01f)))
        assertEquals(2, argmax(floatArrayOf(0.2f, 0.3f, 0.5f)))
    }

    @Test
    fun argmaxPicksFirstOnTie() {
        assertEquals(0, argmax(floatArrayOf(0.5f, 0.5f, 0f)))
    }

    @Test
    fun predictionUsesTopLabelAndKeepsAllProbabilities() {
        val p = Prediction.from(labels, floatArrayOf(0.01f, 0.98f, 0.01f))
        assertEquals("bean_rust", p.label)
        assertEquals(0.98f, p.confidence, 1e-6f)
        assertEquals(labels, p.probabilities.keys.toList())
        assertEquals(1f, p.probabilities.values.sum(), 1e-5f)
        assertEquals(ConfidenceLevel.VERY_LIKELY, p.confidenceLevel)
    }

    @Test
    fun lowTopProbabilityIsNotSure() {
        val p = Prediction.from(labels, floatArrayOf(0.4f, 0.35f, 0.25f))
        assertEquals("angular_leaf_spot", p.label)
        assertEquals(ConfidenceLevel.NOT_SURE, p.confidenceLevel)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutputThatIsNotADistribution() {
        Prediction.from(labels, floatArrayOf(2f, 3f, 1f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLabelCountMismatch() {
        Prediction.from(labels, floatArrayOf(0.5f, 0.5f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun argmaxRejectsEmpty() {
        argmax(floatArrayOf())
    }
}

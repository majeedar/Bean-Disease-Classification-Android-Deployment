package com.majeedar.beandoc.ml

import kotlin.math.abs

data class Prediction(
    val label: String,
    val confidence: Float,
    /** Label to probability, in model output order. */
    val probabilities: Map<String, Float>,
) {
    val confidenceLevel: ConfidenceLevel get() = ConfidenceLevel.from(confidence)

    companion object {
        private const val SUM_TOLERANCE = 0.01f

        fun from(labels: List<String>, probabilities: FloatArray): Prediction {
            require(labels.size == probabilities.size) {
                "Model returned ${probabilities.size} values but there are ${labels.size} labels"
            }
            val sum = probabilities.sum()
            require(abs(sum - 1f) <= SUM_TOLERANCE) {
                "Model output is not a probability distribution (sum = $sum)"
            }
            val top = argmax(probabilities)
            return Prediction(
                label = labels[top],
                confidence = probabilities[top],
                probabilities = labels.zip(probabilities.toList()).toMap(),
            )
        }
    }
}

fun argmax(values: FloatArray): Int {
    require(values.isNotEmpty()) { "argmax of an empty array" }
    var best = 0
    for (i in 1 until values.size) {
        if (values[i] > values[best]) best = i
    }
    return best
}

enum class ConfidenceLevel {
    VERY_LIKELY,
    LIKELY,
    NOT_SURE;

    companion object {
        const val VERY_LIKELY_THRESHOLD = 0.85f
        const val LIKELY_THRESHOLD = 0.70f

        fun from(confidence: Float): ConfidenceLevel = when {
            confidence >= VERY_LIKELY_THRESHOLD -> VERY_LIKELY
            confidence >= LIKELY_THRESHOLD -> LIKELY
            else -> NOT_SURE
        }
    }
}

package com.majeedar.beandoc.ml

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ImagePreprocessorTest {
    @Test
    fun bufferHasNhwcFloat32Size() {
        val size = ImagePreprocessor.INPUT_SIZE
        val buffer = ImagePreprocessor.toInputBuffer(IntArray(500 * 500), side = 500)
        assertEquals(size * size * 3 * 4, buffer.capacity())
        assertEquals(ByteOrder.nativeOrder(), buffer.order())
        assertEquals(0, buffer.position())
    }

    @Test
    fun writesRgbInOrderAsRawZeroTo255() {
        // 2×2 image at its own size (no resampling): distinct R, G, B; alpha must be ignored.
        val pixels = intArrayOf(
            0xFF102030.toInt(), 0x80FF0000.toInt(),
            0xFF00FF00.toInt(), 0xFF0000FF.toInt(),
        )
        val floats = ImagePreprocessor.toInputBuffer(pixels, side = 2, outSize = 2).floats(12)
        val expected = floatArrayOf(
            16f, 32f, 48f, 255f, 0f, 0f,
            0f, 255f, 0f, 0f, 0f, 255f,
        )
        // Raw values, NOT divided by 255 and NOT mean-subtracted.
        expected.forEachIndexed { i, v -> assertEquals("float $i", v, floats[i], 0f) }
    }

    // Expected values below come from tf.image.resize(method="bilinear", antialias=False), TF 2.17.

    @Test
    fun downscaleMatchesTensorFlowOddSizes() = assertMatchesTf(
        side = 5, outSize = 3,
        values = intArrayOf(0, 37, 74, 111, 148, 185, 222, 3, 40, 77, 114, 151, 188, 225, 6, 43, 80, 117, 154, 191, 228, 9, 46, 83, 120),
        red = floatArrayOf(74f, 50.3333f, 112f, 126.3333f, 188f, 79f, 121.7778f, 69.6667f, 131.3333f),
        blue = floatArrayOf(6f, 11f, 16f, 31f, 36f, 41f, 56f, 61f, 66f),
    )

    @Test
    fun downscaleMatchesTensorFlowHalving() = assertMatchesTf(
        side = 4, outSize = 2,
        values = intArrayOf(0, 37, 74, 111, 148, 185, 222, 3, 40, 77, 114, 151, 188, 225, 6, 43),
        red = floatArrayOf(92.5f, 102.5f, 132.5f, 78.5f),
        blue = floatArrayOf(7.5f, 13.5f, 31.5f, 37.5f),
    )

    @Test
    fun upscaleMatchesTensorFlow() = assertMatchesTf(
        side = 2, outSize = 3,
        values = intArrayOf(0, 37, 74, 111),
        red = floatArrayOf(0f, 18.5f, 37f, 37f, 55.5f, 74f, 74f, 92.5f, 111f),
        blue = floatArrayOf(0f, 1.5f, 3f, 3f, 4.5f, 6f, 6f, 7.5f, 9f),
    )

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongPixelCount() {
        ImagePreprocessor.toInputBuffer(IntArray(10), side = 4)
    }

    /** Builds R = v, G = 255 - v, B = (7v) mod 256, as in the Python script that produced the expected values. */
    private fun assertMatchesTf(side: Int, outSize: Int, values: IntArray, red: FloatArray, blue: FloatArray) {
        val pixels = IntArray(values.size) { i ->
            val v = values[i]
            (0xFF shl 24) or (v shl 16) or ((255 - v) shl 8) or (v * 7 % 256)
        }
        val floats = ImagePreprocessor.toInputBuffer(pixels, side, outSize).floats(outSize * outSize * 3)
        for (i in 0 until outSize * outSize) {
            assertEquals("red $i", red[i], floats[i * 3], 1e-3f)
            assertEquals("green $i", 255f - red[i], floats[i * 3 + 1], 1e-3f)
            assertEquals("blue $i", blue[i], floats[i * 3 + 2], 1e-3f)
        }
    }

    private fun ByteBuffer.floats(count: Int) = FloatArray(count) { float }
}

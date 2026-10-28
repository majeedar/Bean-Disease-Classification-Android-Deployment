package com.majeedar.beandoc.ml

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteOrder

class ImagePreprocessorTest {
    @Test
    fun bufferHasNhwcFloat32Size() {
        val size = ImagePreprocessor.INPUT_SIZE
        val buffer = ImagePreprocessor.toInputBuffer(IntArray(size * size))
        assertEquals(size * size * 3 * 4, buffer.capacity())
        assertEquals(ByteOrder.nativeOrder(), buffer.order())
        assertEquals(0, buffer.position())
    }

    @Test
    fun writesRgbInOrderAsRawZeroTo255() {
        // 2×2 image: opaque pixels with distinct R, G, B; alpha must be ignored.
        val pixels = intArrayOf(
            0xFF102030.toInt(), 0x80FF0000.toInt(),
            0xFF00FF00.toInt(), 0xFF0000FF.toInt(),
        )
        val buffer = ImagePreprocessor.toInputBuffer(pixels, size = 2)
        val floats = FloatArray(12) { buffer.float }
        val expected = floatArrayOf(
            16f, 32f, 48f, 255f, 0f, 0f,
            0f, 255f, 0f, 0f, 0f, 255f,
        )
        // Raw values, NOT divided by 255 and NOT mean-subtracted.
        expected.forEachIndexed { i, v -> assertEquals("float $i", v, floats[i], 0f) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongPixelCount() {
        ImagePreprocessor.toInputBuffer(IntArray(10), size = 4)
    }
}

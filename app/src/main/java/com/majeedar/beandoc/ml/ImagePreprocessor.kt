package com.majeedar.beandoc.ml

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure Kotlin (no android.* imports) so it runs in JVM unit tests.
 * Bitmap work (decode, EXIF, crop, resize) lives in [ImageLoader].
 */
object ImagePreprocessor {
    const val INPUT_SIZE = 224
    private const val CHANNELS = 3
    private const val BYTES_PER_FLOAT = 4

    /**
     * ARGB pixels of a [size]×[size] image, row by row → float32 NHWC RGB buffer.
     * Values stay raw 0–255: the model normalises internally, so do NOT divide by 255.
     */
    fun toInputBuffer(pixels: IntArray, size: Int = INPUT_SIZE): ByteBuffer {
        require(pixels.size == size * size) {
            "Expected ${size * size} pixels for a ${size}x$size image, got ${pixels.size}"
        }
        val buffer = ByteBuffer.allocateDirect(size * size * CHANNELS * BYTES_PER_FLOAT)
            .order(ByteOrder.nativeOrder())
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF).toFloat())
            buffer.putFloat(((pixel shr 8) and 0xFF).toFloat())
            buffer.putFloat((pixel and 0xFF).toFloat())
        }
        buffer.rewind()
        return buffer
    }
}

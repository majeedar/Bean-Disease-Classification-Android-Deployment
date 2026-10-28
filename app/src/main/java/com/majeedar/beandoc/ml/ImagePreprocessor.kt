package com.majeedar.beandoc.ml

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Pure Kotlin (no android.* imports) so it runs in JVM unit tests.
 * Bitmap work (decode, EXIF, crop) lives in [ImageLoader].
 */
object ImagePreprocessor {
    const val INPUT_SIZE = 224
    private const val CHANNELS = 3
    private const val BYTES_PER_FLOAT = 4

    /**
     * ARGB pixels of a [side]×[side] square, row by row → float32 NHWC RGB buffer of [outSize]×[outSize].
     *
     * Resizes with exactly the maths of `tf.image.resize(method="bilinear", antialias=False)`
     * (half-pixel centers), which training and tools/make_parity_set.py use, and keeps the
     * interpolated values as floats like TF does. Do not resize with createScaledBitmap: its sampling
     * is implementation-defined and not guaranteed to match, while this port is unit-tested against TF.
     *
     * Values stay raw 0–255: the model normalises internally, so do NOT divide by 255.
     */
    fun toInputBuffer(pixels: IntArray, side: Int, outSize: Int = INPUT_SIZE): ByteBuffer {
        require(side > 0 && pixels.size == side * side) {
            "Expected ${side * side} pixels for a ${side}x$side image, got ${pixels.size}"
        }
        val buffer = ByteBuffer.allocateDirect(outSize * outSize * CHANNELS * BYTES_PER_FLOAT)
            .order(ByteOrder.nativeOrder())
        val axis = interpolationWeights(side, outSize)

        for (y in 0 until outSize) {
            val top = axis.lower[y] * side
            val bottom = axis.upper[y] * side
            val dy = axis.lerp[y]
            for (x in 0 until outSize) {
                val left = axis.lower[x]
                val right = axis.upper[x]
                val dx = axis.lerp[x]
                val tl = pixels[top + left]
                val tr = pixels[top + right]
                val bl = pixels[bottom + left]
                val br = pixels[bottom + right]
                for (shift in RGB_SHIFTS) {
                    val t = channel(tl, shift) + (channel(tr, shift) - channel(tl, shift)) * dx
                    val b = channel(bl, shift) + (channel(br, shift) - channel(bl, shift)) * dx
                    buffer.putFloat(t + (b - t) * dy)
                }
            }
        }
        buffer.rewind()
        return buffer
    }

    private val RGB_SHIFTS = intArrayOf(16, 8, 0)

    private fun channel(pixel: Int, shift: Int): Float = ((pixel shr shift) and 0xFF).toFloat()

    private class Axis(val lower: IntArray, val upper: IntArray, val lerp: FloatArray)

    /** Port of TF's compute_interpolation_weights with half_pixel_centers = true. */
    private fun interpolationWeights(inSize: Int, outSize: Int): Axis {
        val scale = inSize.toFloat() / outSize
        val lower = IntArray(outSize)
        val upper = IntArray(outSize)
        val lerp = FloatArray(outSize)
        for (i in 0 until outSize) {
            val source = (i + 0.5f) * scale - 0.5f
            val sourceFloor = floor(source)
            lower[i] = maxOf(sourceFloor.toInt(), 0)
            upper[i] = minOf(ceil(source).toInt(), inSize - 1)
            lerp[i] = source - sourceFloor
        }
        return Axis(lower, upper, lerp)
    }
}

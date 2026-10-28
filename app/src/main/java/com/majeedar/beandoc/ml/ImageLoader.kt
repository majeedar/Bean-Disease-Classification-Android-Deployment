package com.majeedar.beandoc.ml

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import java.io.InputStream

/**
 * Turns a photo into the square the model sees: decode (downsampled) → EXIF rotation → center crop.
 * [ImagePreprocessor] then resizes it to 224×224. Together they match tools/make_parity_set.py,
 * which in turn matches training.
 */
class ImageLoader(private val contentResolver: ContentResolver) {

    /** Returns the center square (ARGB_8888, shortest side ≥ 2 × [size] when the photo allows). Call off the main thread. */
    @Throws(IOException::class)
    fun load(uri: Uri, size: Int = ImagePreprocessor.INPUT_SIZE): Bitmap {
        val decoded = decode(uri, size)
        val rotated = applyExifOrientation(decoded, readOrientation(uri))
        val (left, top, side) = centerSquare(rotated.width, rotated.height)
        val cropped = Bitmap.createBitmap(rotated, left, top, side, side)
        if (cropped !== rotated) rotated.recycle()
        return cropped
    }

    private fun decode(uri: Uri, size: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image: $uri")

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, size * DECODE_MIN_FACTOR)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return open(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Could not decode image: $uri")
    }

    private fun readOrientation(uri: Uri): Int = try {
        open(uri).use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    } catch (e: IOException) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun open(uri: Uri): InputStream =
        contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")

    companion object {
        /** Decode to at least 2× the model size so the final bilinear step stays close to training. */
        private const val DECODE_MIN_FACTOR = 2

        /** Largest power-of-two sample size that keeps the shortest side ≥ [minSide]. */
        fun sampleSize(width: Int, height: Int, minSide: Int): Int {
            val shortest = minOf(width, height)
            var sample = 1
            while (shortest / (sample * 2) >= minSide) sample *= 2
            return sample
        }

        /** Same integer math as the Python reference: offsets (w - s) / 2, (h - s) / 2. */
        fun centerSquare(width: Int, height: Int): Triple<Int, Int, Int> {
            val side = minOf(width, height)
            return Triple((width - side) / 2, (height - side) / 2, side)
        }

        fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> return bitmap
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.setRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.setRotate(-90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
                else -> return bitmap
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
            if (rotated !== bitmap) bitmap.recycle()
            return rotated
        }

        fun Bitmap.toPixels(): IntArray {
            val pixels = IntArray(width * height)
            getPixels(pixels, 0, width, 0, 0, width, height)
            return pixels
        }
    }
}

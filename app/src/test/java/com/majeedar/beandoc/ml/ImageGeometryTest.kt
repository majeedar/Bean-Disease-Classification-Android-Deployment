package com.majeedar.beandoc.ml

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageGeometryTest {
    @Test
    fun parityAndTrainingSizedImagesAreNotSubsampled() {
        assertEquals(1, ImageLoader.sampleSize(500, 500, 448))
        assertEquals(1, ImageLoader.sampleSize(750, 500, 448))
    }

    @Test
    fun largePhonePhotosKeepShortestSideAboveMinimum() {
        assertEquals(4, ImageLoader.sampleSize(4000, 3000, 448)) // 3000 / 4 = 750
        assertEquals(2, ImageLoader.sampleSize(1280, 960, 448)) // 960 / 2 = 480
        assertEquals(1, ImageLoader.sampleSize(1280, 800, 448)) // 800 / 2 = 400 < 448
    }

    @Test
    fun centerSquareMatchesPythonCrop() {
        assertEquals(Triple(125, 0, 500), ImageLoader.centerSquare(750, 500))
        assertEquals(Triple(0, 125, 500), ImageLoader.centerSquare(500, 750))
        assertEquals(Triple(0, 0, 224), ImageLoader.centerSquare(224, 224))
        assertEquals(Triple(0, 0, 10), ImageLoader.centerSquare(11, 10)) // (11 - 10) / 2 = 0
    }
}

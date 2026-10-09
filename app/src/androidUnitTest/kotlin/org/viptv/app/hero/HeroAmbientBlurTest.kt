package org.viptv.app.hero

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeroAmbientBlurTest {
    @Test fun flatArtworkKeepsItsColorAndOpacityIncludingAtBorders() {
        val pixels = IntArray(15) { 0x80406080.toInt() }
        assertContentEquals(pixels, blurAmbientPixels(pixels, 5, 3, 8))
    }

    @Test fun aBrightFeatureSpreadsSymmetricallyWithoutChangingTheSharpSource() {
        val pixels = IntArray(21 * 21) { 0xff000000.toInt() }
        pixels[10 * 21 + 10] = 0xffffffff.toInt()
        val blurred = blurAmbientPixels(pixels, 21, 21, 1)
        assertEquals(0xffffffff.toInt(), pixels[10 * 21 + 10])
        val center = blurred[10 * 21 + 10] and 255
        assertTrue(center in 1..254)
        assertTrue((blurred[10 * 21 + 9] and 255) > 0)
        assertEquals(blurred[10 * 21 + 9], blurred[10 * 21 + 11])
        assertEquals(blurred[9 * 21 + 10], blurred[11 * 21 + 10])
    }

    @Test fun tinyArtworkIsSafeWhenTheBlurRadiusExceedsItsDimensions() {
        assertContentEquals(intArrayOf(0xff123456.toInt()), blurAmbientPixels(intArrayOf(0xff123456.toInt()), 1, 1, 8))
    }
}

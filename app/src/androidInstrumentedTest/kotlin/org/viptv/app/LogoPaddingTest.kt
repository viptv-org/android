package org.viptv.app

import android.graphics.Bitmap
import android.graphics.Color
import coil.size.Size
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LogoPaddingTest {
    @Test fun transparentMarginsAreRemovedWithoutRescalingVisiblePixels() = runBlocking {
        val bitmap = Bitmap.createBitmap(12, 10, Bitmap.Config.ARGB_8888)
        for (y in 2..6) for (x in 4..7) bitmap.setPixel(x, y, Color.WHITE)
        val result = TrimLogoPadding.transform(bitmap, Size.ORIGINAL)
        assertEquals(4, result.width)
        assertEquals(5, result.height)
        assertEquals(Color.WHITE, result.getPixel(0, 0))
    }
    @Test fun emptyAndOpaqueLogosRemainValidBitmaps() = runBlocking {
        val empty = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        assertSame(empty, TrimLogoPadding.transform(empty, Size.ORIGINAL))
        empty.eraseColor(Color.WHITE)
        assertSame(empty, TrimLogoPadding.transform(empty, Size.ORIGINAL))
    }
}

package org.viptv.app

import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals

class ArtworkImagesTest {
    @Test fun publicArtworkIsResizedBeforeDownloadAndUsesStableViewportBuckets() {
        val original = "https://image.tmdb.org/t/p/original/public-poster.jpg"
        val result = ArtworkImages.transport(original, 310, 175, true).toHttpUrl()
        assertEquals("wsrv.nl", result.host)
        assertEquals(original, result.queryParameter("url"))
        assertEquals("320", result.queryParameter("w"))
        assertEquals("240", result.queryParameter("h"))
        assertEquals("webp", result.queryParameter("output"))
        assertEquals("65", result.queryParameter("q"))
        assertEquals(result.toString(), ArtworkImages.transport(original, 318, 180, true))
        val huge = ArtworkImages.transport(original, 8000, 8000, false).toHttpUrl()
        assertEquals("1280", huge.queryParameter("w"))
        assertEquals("960", huge.queryParameter("h"))
        assertEquals("inside", huge.queryParameter("fit"))
    }

    @Test fun localPrivateAndCredentialBearingImagesNeverGoToAThirdParty() {
        for (url in listOf("file:///android_asset/avatar.png", "https://192.168.1.1/art.jpg",
            "https://provider.example/private/art.jpg", "https://image.tmdb.org.evil.example/art.jpg",
            "https://private:password@image.tmdb.org/art.jpg", "https://image.tmdb.org/art.jpg?api_key=private",
            "https://image.tmdb.org:8080/art.jpg", "not a URL")) {
            assertEquals(url, ArtworkImages.transport(url, 320, 180, true))
        }
    }
}

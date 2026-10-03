package es.tortugaselectronicas.scrollfree

import es.tortugaselectronicas.scrollfree.util.Platform
import es.tortugaselectronicas.scrollfree.util.UrlUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {

    @Test
    fun `extractFirstUrl extracts url from shared text`() {
        val text = "Mira este vídeo! https://vt.tiktok.com/ZSxxxxxx/ "
        assertEquals("https://vt.tiktok.com/ZSxxxxxx/", UrlUtils.extractFirstUrl(text))
    }

    @Test
    fun `extractFirstUrl trims trailing punctuation`() {
        val text = "mira esto https://x.com/user/status/123)."
        assertEquals("https://x.com/user/status/123", UrlUtils.extractFirstUrl(text))
    }

    @Test
    fun `extractFirstUrl returns null when there is no url`() {
        assertNull(UrlUtils.extractFirstUrl("sin enlace alguno"))
        assertNull(UrlUtils.extractFirstUrl(null))
        assertNull(UrlUtils.extractFirstUrl(""))
    }

    @Test
    fun `isValidUrl accepts http and https`() {
        assertTrue(UrlUtils.isValidUrl("https://www.instagram.com/reel/abc/"))
        assertTrue(UrlUtils.isValidUrl("http://example.com"))
    }

    @Test
    fun `isValidUrl rejects non urls`() {
        assertFalse(UrlUtils.isValidUrl("example.com"))
        assertFalse(UrlUtils.isValidUrl("ftp://example.com"))
        assertFalse(UrlUtils.isValidUrl(null))
        assertFalse(UrlUtils.isValidUrl("   "))
    }

    @Test
    fun `detectPlatform recognises known hosts`() {
        assertEquals(Platform.TIKTOK, UrlUtils.detectPlatform("https://www.tiktok.com/@x/video/1"))
        assertEquals(Platform.TIKTOK, UrlUtils.detectPlatform("https://vm.tiktok.com/abc/"))
        assertEquals(Platform.INSTAGRAM, UrlUtils.detectPlatform("https://instagram.com/reel/x"))
        assertEquals(Platform.X, UrlUtils.detectPlatform("https://x.com/user/status/1"))
        assertEquals(Platform.X, UrlUtils.detectPlatform("https://twitter.com/user/status/1"))
        assertEquals(Platform.YOUTUBE, UrlUtils.detectPlatform("https://youtu.be/abc"))
        assertEquals(Platform.YOUTUBE, UrlUtils.detectPlatform("https://www.youtube.com/watch?v=abc"))
        assertEquals(Platform.OTHER, UrlUtils.detectPlatform("https://example.com/video"))
        assertEquals(Platform.OTHER, UrlUtils.detectPlatform(null))
    }
}

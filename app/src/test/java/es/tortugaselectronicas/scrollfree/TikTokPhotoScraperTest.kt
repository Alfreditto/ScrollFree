package es.tortugaselectronicas.scrollfree

import es.tortugaselectronicas.scrollfree.data.TikTokPhotoScraper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokPhotoScraperTest {

    @Test
    fun `detects tiktok photo urls`() {
        assertTrue(TikTokPhotoScraper.isTikTokPhotoUrl("https://www.tiktok.com/@user/photo/123456"))
        assertTrue(TikTokPhotoScraper.isTikTokPhotoUrl("https://www.tiktok.com/@richoff10s/photo/7692206886724160798"))
    }

    @Test
    fun `does not flag tiktok video urls as photos`() {
        assertFalse(TikTokPhotoScraper.isTikTokPhotoUrl("https://www.tiktok.com/@user/video/123456"))
    }

    @Test
    fun `does not flag non-tiktok urls`() {
        assertFalse(TikTokPhotoScraper.isTikTokPhotoUrl("https://www.instagram.com/reel/abc/"))
        assertFalse(TikTokPhotoScraper.isTikTokPhotoUrl("https://example.com/photo/1"))
    }
}

package es.tortugaselectronicas.scrollfree

import es.tortugaselectronicas.scrollfree.data.friendlyDownloadError
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadErrorTest {

    @Test
    fun `tiktok photo post is reported as a photo carousel`() {
        val error = friendlyDownloadError(
            url = "https://www.tiktok.com/@user/photo/123456",
            rawError = "ERROR: Unsupported URL: https://www.tiktok.com/@user/photo/123456"
        )
        assertTrue(error.message!!.contains("carrusel de fotos", ignoreCase = true))
    }

    @Test
    fun `unsupported url gets a generic unsupported message`() {
        val error = friendlyDownloadError(
            url = "https://example.com/page",
            rawError = "ERROR: Unsupported URL: https://example.com/page"
        )
        assertTrue(error.message!!.contains("no reconoce", ignoreCase = true))
    }

    @Test
    fun `private video is reported as private`() {
        val error = friendlyDownloadError(
            url = "https://www.instagram.com/reel/x",
            rawError = "ERROR: This video is private"
        )
        assertTrue(error.message!!.contains("privado", ignoreCase = true))
    }

    @Test
    fun `connection issues are reported as network failures`() {
        val error = friendlyDownloadError(
            url = "https://x.com/u/status/1",
            rawError = "ERROR: Unable to download webpage: connection timed out"
        )
        assertTrue(error.message!!.contains("conexión", ignoreCase = true))
    }

    @Test
    fun `unknown errors fall back to a generic message`() {
        val error = friendlyDownloadError(
            url = "https://x.com/u/status/1",
            rawError = "something totally unexpected"
        )
        assertTrue(error.message!!.contains("No se pudo descargar", ignoreCase = true))
    }
}

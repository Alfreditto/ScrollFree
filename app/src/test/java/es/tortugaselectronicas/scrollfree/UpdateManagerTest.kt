package es.tortugaselectronicas.scrollfree

import es.tortugaselectronicas.scrollfree.update.UpdateManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun `parses versionCode from release body`() {
        val body = """
            Build automática.
            - Commit: abc123
            - versionCode: `145`
        """.trimIndent()
        assertEquals(145, UpdateManager.parseVersionCode(body))
    }

    @Test
    fun `returns null when versionCode missing`() {
        assertNull(UpdateManager.parseVersionCode("sin codigo"))
        assertNull(UpdateManager.parseVersionCode(""))
    }

    @Test
    fun `parses a release json with apk asset`() {
        val json = JSONObject(
            """
            {
              "tag_name": "v1.0.42",
              "body": "- versionCode: 142",
              "assets": [
                {"name": "notes.txt", "browser_download_url": "https://x/notes.txt"},
                {"name": "ScrollFree.apk", "browser_download_url": "https://x/app.apk"}
              ]
            }
            """.trimIndent()
        )
        val info = UpdateManager.parseRelease(json)!!
        assertEquals("1.0.42", info.versionName)
        assertEquals(142, info.versionCode)
        assertEquals("https://x/app.apk", info.apkUrl)
        assertTrue(info.notes!!.contains("versionCode"))
    }

    @Test
    fun `falls back to tag for versionCode when body has none`() {
        val json = JSONObject(
            """
            {
              "tag_name": "v1.0.77",
              "body": "",
              "assets": [
                {"name": "app.apk", "browser_download_url": "https://x/app.apk"}
              ]
            }
            """.trimIndent()
        )
        assertEquals(77, UpdateManager.parseRelease(json)!!.versionCode)
    }

    @Test
    fun `returns null when no apk asset`() {
        val json = JSONObject(
            """
            {"tag_name": "v1.0.5", "body": "versionCode: 5",
             "assets": [{"name": "readme.md", "browser_download_url": "https://x/r.md"}]}
            """.trimIndent()
        )
        assertNull(UpdateManager.parseRelease(json))
    }
}

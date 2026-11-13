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
    fun `parses a release json`() {
        val json = JSONObject(
            """
            {
              "tag_name": "v1.0.42",
              "body": "- versionCode: 142",
              "html_url": "https://github.com/x/y/releases/tag/v1.0.42"
            }
            """.trimIndent()
        )
        val info = UpdateManager.parseRelease(json)!!
        assertEquals("1.0.42", info.versionName)
        assertEquals(142, info.versionCode)
        assertTrue(info.releaseUrl.contains("v1.0.42"))
    }

    @Test
    fun `falls back to tag for versionCode when body has none`() {
        val json = JSONObject(
            """
            {"tag_name": "v1.0.77", "body": "", "html_url": "https://github.com/x/y/releases/tag/v1.0.77"}
            """.trimIndent()
        )
        assertEquals(77, UpdateManager.parseRelease(json)!!.versionCode)
    }

    @Test
    fun `returns null when version cannot be determined`() {
        val json = JSONObject("""{"tag_name": "sin-numero", "body": ""}""")
        assertNull(UpdateManager.parseRelease(json))
    }
}

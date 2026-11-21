// Modified 2026 by Treslibre Project (Treslibre).
package ch.threema.domain.protocol.urls

import ch.threema.common.emptyByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BlobMirrorUrlTest {
    @Test
    fun `placeholders are replaced`() {
        val blobMirrorUrl = BlobMirrorUrl("https://blob-mirror-{deviceGroupIdPrefix4}.test.treslibre.invalid/{deviceGroupIdPrefix8}")

        val url = blobMirrorUrl.get(deviceGroupId = byteArrayOf(0xDC.toByte()))

        assertEquals("https://blob-mirror-d.test.treslibre.invalid/dc", url)
    }

    @Test
    fun `empty device group id is considered invalid`() {
        val blobMirrorUrl = BlobMirrorUrl("https://blob-mirror-{deviceGroupIdPrefix4}.test.treslibre.invalid/{deviceGroupIdPrefix8}")

        assertFailsWith<IllegalArgumentException> {
            blobMirrorUrl.get(deviceGroupId = emptyByteArray())
        }
    }
}

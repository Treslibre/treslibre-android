// Modified 2026 by Treslibre Project (Treslibre).
package ch.threema.domain.protocol.urls

import kotlin.test.Test
import kotlin.test.assertEquals

class AppRatingUrlTest {
    @Test
    fun `placeholders are replaced`() {
        val appRatingUrl = AppRatingUrl("https://test.treslibre.invalid/app-rating/android-test/{rating}")

        val url = appRatingUrl.get(rating = 5)

        assertEquals("https://test.treslibre.invalid/app-rating/android-test/5", url)
    }
}

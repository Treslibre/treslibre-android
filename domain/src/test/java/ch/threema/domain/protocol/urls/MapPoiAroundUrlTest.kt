// Modified 2026 by Treslibre Project (Treslibre).
package ch.threema.domain.protocol.urls

import ch.threema.common.models.Coordinates
import kotlin.test.Test
import kotlin.test.assertEquals

class MapPoiAroundUrlTest {
    @Test
    fun `placeholders are replaced`() {
        val mapPoiAroundUrl = MapPoiAroundUrl("https://poi.treslibre.invalid/around/{latitude}/{longitude}/{radius}/")

        val url = mapPoiAroundUrl.get(coordinates = Coordinates(47.220087, 8.808609), radius = 100)

        assertEquals("https://poi.treslibre.invalid/around/47.220087/8.808609/100/", url)
    }
}

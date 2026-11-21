// Modified 2026 by Treslibre Project (Treslibre).
package ch.threema.data.datatypes

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PredefinedContactTest {
    @Test
    fun testOfficialPredefinedContactsPurged() {
        assertTrue(PredefinedContact.getAllForCurrentEnvironment().isEmpty())
        assertNull(PredefinedContact.getPredefinedContact("*THREEMA"))
        assertNull(PredefinedContact.getPredefinedContact("*SUPPORT"))
        assertNull(PredefinedContact.getPredefinedContact("*MY3DATA"))
        assertNull(PredefinedContact.getPredefinedContact("*3MAPUSH"))
    }
}

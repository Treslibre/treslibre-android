// Modified 2026 by Treslibre Project (Treslibre).
package ch.threema.app.voip.groupcall.sfu

import ch.threema.common.plus
import ch.threema.domain.protocol.api.SfuToken
import java.time.Instant
import java.util.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

private const val SFU_BASE_URL = "https://server-1-sfu.treslibre.invalid/"
private val SFU_BASE_URL_ALLOWED_SUFFIXES: Set<String> = setOf(
    "sfu.treslibre.invalid",
    "sfu.threema-work.ch",
)

internal class SfuTokenTest {
    @Test
    fun isAllowedBaseUrl_emptyBaseUrlIsInvalid() {
        assertFalse(isAllowedBaseUrl(""))
    }

    @Test
    fun isAllowedBaseUrl_baseUrlWithoutProtocolIsInvalid() {
        assertFalse(isAllowedBaseUrl("sfu.treslibre.invalid"))
        assertFalse(isAllowedBaseUrl("sfu.treslibre.invalid/"))
    }

    @Test
    fun isAllowedBaseUrl_baseUrlWithHttpProtocolIsInvalid() {
        assertFalse(isAllowedBaseUrl("http://sfu.treslibre.invalid"))
        assertFalse(isAllowedBaseUrl("http://sfu.treslibre.invalid/"))
    }

    @Test
    fun isAllowedBaseUrl_baseUrlWithInvalidSuffixIsInvalid() {
        assertFalse(isAllowedBaseUrl("http://some-bad.url.ch"))
        assertFalse(isAllowedBaseUrl("http://server.treslibre.invalid"))
        assertFalse(isAllowedBaseUrl("http://fu.treslibre.invalid"))
        assertFalse(isAllowedBaseUrl("http://some-bad.url.ch/"))
        assertFalse(isAllowedBaseUrl("http://server.treslibre.invalid/"))
        assertFalse(isAllowedBaseUrl("http://fu.treslibre.invalid/"))
    }

    @Test
    fun isAllowedBaseUrl_baseUrlWithHttpsProtocolAndValidHostSuffixIsValid() {
        assertTrue(isAllowedBaseUrl("https://sfu.treslibre.invalid"))
        assertTrue(isAllowedBaseUrl("https://blasfu.treslibre.invalid"))
        assertTrue(isAllowedBaseUrl("https://bla-sfu.treslibre.invalid"))
        assertTrue(isAllowedBaseUrl("https://bla-1-sfu.treslibre.invalid"))
        assertTrue(isAllowedBaseUrl("https://sfu.threema-work.ch"))
        assertTrue(isAllowedBaseUrl("https://blasfu.threema-work.ch"))
        assertTrue(isAllowedBaseUrl("https://bla-sfu.threema-work.ch"))
        assertTrue(isAllowedBaseUrl("https://bla-1-sfu.threema-work.ch"))
    }

    @Test
    fun isAllowedBaseUrl_baseUrlWithAdditionalPathInfoIsValid() {
        assertTrue(isAllowedBaseUrl("https://sfu.treslibre.invalid/"))
        assertTrue(isAllowedBaseUrl("https://sfu.treslibre.invalid/path"))
        assertTrue(isAllowedBaseUrl("https://sfu.treslibre.invalid/path/to"))
        assertTrue(isAllowedBaseUrl("https://sfu.treslibre.invalid/path/to/sfu"))
        assertTrue(isAllowedBaseUrl("https://sfu.treslibre.invalid/path/to/sfu/"))
    }

    /**
     * If the base url contains a port number, the port number must also be present in the allowed suffix and vice versa.
     */
    @Test
    fun isAllowedBaseUrl_baseUrlWithNonDefaultPort() {
        // port not provided in allowed suffixes must fail
        assertFalse(isAllowedBaseUrl("https://sfu.treslibre.invalid:4711"))
        assertFalse(isAllowedBaseUrl("https://sfu.treslibre.invalid:4711/"))
        assertFalse(isAllowedBaseUrl("https://sfu.threema-work.ch:4711"))
        assertFalse(isAllowedBaseUrl("https://sfu.threema-work.ch:4711/"))

        // port provided in allowed suffixes must work
        val token = getToken(setOf("treslibre.invalid:4711", "threema-work.ch:4711"))
        assertTrue(token.isAllowedBaseUrl("https://sfu.treslibre.invalid:4711"))
        assertTrue(token.isAllowedBaseUrl("https://sfu.treslibre.invalid:4711/"))
        assertTrue(token.isAllowedBaseUrl("https://sfu.threema-work.ch:4711"))
        assertTrue(token.isAllowedBaseUrl("https://sfu.threema-work.ch:4711/"))

        // port provided in alllowed suffixes but not in checked base url must fail
        assertFalse(token.isAllowedBaseUrl("https://sfu.treslibre.invalid"))
        assertFalse(token.isAllowedBaseUrl("https://sfu.treslibre.invalid/"))
        assertFalse(token.isAllowedBaseUrl("https://sfu.threema-work.ch"))
        assertFalse(token.isAllowedBaseUrl("https://sfu.threema-work.ch/"))
    }

    private fun isAllowedBaseUrl(baseUrl: String): Boolean {
        return getToken().isAllowedBaseUrl(baseUrl)
    }

    private fun getToken(allowedSuffixes: Set<String> = SFU_BASE_URL_ALLOWED_SUFFIXES): SfuToken {
        // Some date in the future. As long as it is not relevant for the tests any date can be used.
        val expirationDate = Instant.now() + 1.days
        return SfuToken(
            SFU_BASE_URL,
            allowedSuffixes,
            "${expirationDate.epochSecond}:ABCD1234:YvKFP429AWBkIsJ4upEm9b6OJ3tayu",
            expirationDate,
        )
    }
}

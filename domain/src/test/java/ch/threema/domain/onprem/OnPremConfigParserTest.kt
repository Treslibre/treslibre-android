// Modified 2026 by Treslibre Project (Treslibre).
package ch.threema.domain.onprem

import ch.threema.common.Base64
import ch.threema.common.plus
import ch.threema.common.withoutLastLine
import ch.threema.domain.protocol.urls.BlobUrl
import ch.threema.domain.protocol.urls.MapPoiAroundUrl
import ch.threema.domain.protocol.urls.MapPoiNamesUrl
import ch.threema.domain.protocol.urls.MediatorUrl
import ch.threema.testhelpers.TestTimeProvider
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import org.json.JSONObject

class OnPremConfigParserTest {

    private val timeProvider = TestTimeProvider(initialTimestamp = 123_456_789_000L)
    private lateinit var config: OnPremConfig

    @BeforeTest
    fun setUp() {
        config = OnPremConfigParser().parse(
            obj = JSONObject(OnPremConfigTestData.goodOppf.withoutLastLine()),
            createdAt = timeProvider.get(),
        )
    }

    @Test
    fun testInvalidConfig() {
        val parser = OnPremConfigParser()

        assertFailsWith<OnPremConfigParseException> {
            parser.parse(
                obj = JSONObject("{}"),
                createdAt = timeProvider.get(),
            )
        }
    }

    @Test
    fun testRefresh() {
        assertEquals(timeProvider.get() + 24.hours, config.validUntil)
    }

    @Test
    fun testChatConfig() {
        val chatConfig = config.chat
        assertEquals("chat.onprem.example.treslibre.invalid", chatConfig.hostname)
        assertContentEquals(intArrayOf(5222, 443), chatConfig.ports)
        assertContentEquals(Base64.decode("r9utIHN9ngo21q9OlZcotsQu1f2HwAW2Wi+u6Psp4Wc="), chatConfig.publicKey)
    }

    @Test
    fun testDirectoryConfig() {
        val directoryConfig = config.directory
        assertEquals("https://onprem.example.treslibre.invalid/directory", directoryConfig.url)
    }

    @Test
    fun testBlobConfig() {
        val blobConfig = config.blob
        assertEquals("https://blob.onprem.example.treslibre.invalid/blob/upload", blobConfig.uploadUrl)
        assertEquals(BlobUrl("https://blob-{blobIdPrefix}.onprem.example.treslibre.invalid/blob/{blobId}"), blobConfig.downloadUrl)
        assertEquals(BlobUrl("https://blob-{blobIdPrefix}.onprem.example.treslibre.invalid/blob/{blobId}/done"), blobConfig.doneUrl)
    }

    @Test
    fun testWorkConfig() {
        val workConfig = config.work
        assertEquals("https://work.onprem.example.treslibre.invalid/", workConfig.url)
    }

    @Test
    fun testAvatarConfig() {
        val avatarConfig = config.avatar
        assertEquals("https://avatar.onprem.example.treslibre.invalid/", avatarConfig.url)
    }

    @Test
    fun testSafeConfig() {
        val safeConfig = config.safe
        assertEquals("https://safe.onprem.example.treslibre.invalid/", safeConfig.url)
    }

    @Test
    fun testWebConfig() {
        val webConfig = config.web!!
        assertEquals("https://web.onprem.example.treslibre.invalid/", webConfig.url)
    }

    @Test
    fun testMediatorConfig() {
        val mediatorConfig = config.mediator!!
        assertEquals(MediatorUrl("wss://mediator.onprem.example.treslibre.invalid/"), mediatorConfig.url)
        assertEquals("https://blob-mirror.onprem.example.treslibre.invalid/blob/upload", mediatorConfig.blob.uploadUrl)
        assertEquals(BlobUrl("https://blob-mirror.onprem.example.treslibre.invalid/blob/{blobId}"), mediatorConfig.blob.downloadUrl)
        assertEquals(BlobUrl("https://blob-mirror.onprem.example.treslibre.invalid/blob/{blobId}/done"), mediatorConfig.blob.doneUrl)
    }

    @Test
    fun testDomainRulesConfig() {
        assertEquals(
            OnPremConfigDomains(
                rules = listOf(
                    OnPremConfigDomainRule(
                        fqdn = "onprem.example.treslibre.invalid",
                        matchMode = OnPremConfigDomainRuleMatchMode.INCLUDE_SUBDOMAINS,
                        spkis = listOf(
                            OnPremConfigDomainRuleSpki(
                                value = "DTJU4+0HObYPrx9lF4Kz8hhjcJL3WBL4k829L++UlSk=",
                                algorithm = OnPremConfigDomainRuleSpkiAlgorithm.SHA256,
                            ),
                            OnPremConfigDomainRuleSpki(
                                value = "C19RmQgZXzwovKRRJ2st7bsokiRchKcYjBo3m63fvn8=",
                                algorithm = OnPremConfigDomainRuleSpkiAlgorithm.SHA256,
                            ),
                        ),
                    ),
                    OnPremConfigDomainRule(
                        fqdn = "another-host.example.treslibre.invalid",
                        matchMode = OnPremConfigDomainRuleMatchMode.EXACT,
                        spkis = listOf(
                            OnPremConfigDomainRuleSpki(
                                value = "XIglSWPJ6aJ7LeIz6KsOrr0fNgNZ0PzGgDCDEZq5/U4=",
                                algorithm = OnPremConfigDomainRuleSpkiAlgorithm.SHA256,
                            ),
                        ),
                    ),
                ),
            ),
            config.domains,
        )
    }

    @Test
    fun testMapsConfig() {
        assertEquals(
            OnPremConfigMaps(
                styleUrl = "https://map.onprem.example.treslibre.invalid/styles/threema/style.json",
                poiNamesUrl = MapPoiNamesUrl("https://poi.onprem.example.treslibre.invalid/names/{latitude}/{longitude}/{query}"),
                poiAroundUrl = MapPoiAroundUrl("https://poi.onprem.example.treslibre.invalid/around/{latitude}/{longitude}/{radius}"),
            ),
            config.maps,
        )
    }

    @Test
    fun `test minimal oppf`() {
        val config = OnPremConfigParser().parse(
            obj = JSONObject(OnPremConfigTestData.minimalOppf.withoutLastLine()),
            createdAt = timeProvider.get(),
        )

        assertEquals(timeProvider.get() + 24.hours, config.validUntil)
        assertNull(config.maps)
        assertNull(config.mediator)
        assertNull(config.domains)
    }
}

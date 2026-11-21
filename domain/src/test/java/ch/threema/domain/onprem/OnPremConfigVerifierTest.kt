// Modified 2026 by Treslibre Project (Treslibre).
package ch.threema.domain.onprem

import ch.threema.base.ThreemaException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OnPremConfigVerifierTest {

    @Test
    fun `verify valid config`() {
        val verifier = OnPremConfigVerifier(arrayOf(OnPremConfigTestData.PUBLIC_KEY))

        val obj = verifier.verify(OnPremConfigTestData.goodOppf)

        assertEquals("1.0", obj.getString("version"))
    }

    @Test
    fun `self-verify valid config when trustedPublicKeys is empty (TOFU unlocked ingestion)`() {
        val verifier = OnPremConfigVerifier(emptyArray())

        val obj = verifier.verify(OnPremConfigTestData.goodOppf)

        assertEquals("1.0", obj.getString("version"))
    }

    @Test
    fun `parse cleanroom JSON provisioning payload when trustedPublicKeys is empty`() {
        val verifier = OnPremConfigVerifier(emptyArray())
        val cleanroomJson = """
            {
              "server_id": "chat.thr.2sx.me",
              "chat_host": "chat.thr.2sx.me",
              "chat_port": 5222,
              "chat_pubkey_curve25519": "6ofyP2NLh0QZWpAJiKMweHuL2M0ZgeLvP4WIxAZZkk8=",
              "directory_url": "https://thr.2sx.me/dir",
              "blob_url": "https://thr.2sx.me/blob",
              "safe_url": "https://thr.2sx.me/safe",
              "push_relay_url": "https://thr.2sx.me/push",
              "admin_signing_pubkey_ed25519": "gFYqhjw0fCWEVtTHXfgcmIz3ez4zISlRYvblgz/svds="
            }
        """.trimIndent()

        val obj = verifier.verify(cleanroomJson)

        assertEquals("1.0", obj.getString("version"))
        assertEquals("chat.thr.2sx.me", obj.getJSONObject("chat").getString("hostname"))
        assertEquals("https://thr.2sx.me/dir", obj.getJSONObject("directory").getString("url"))
    }

    @Test
    fun `invalid signature`() {
        val verifier = OnPremConfigVerifier(arrayOf(OnPremConfigTestData.PUBLIC_KEY))

        assertFailsWith<ThreemaException> {
            verifier.verify(OnPremConfigTestData.badOppf)
        }
    }

    @Test
    fun `wrong public key`() {
        val verifier = OnPremConfigVerifier(arrayOf(OnPremConfigTestData.WRONG_PUBLIC_KEY))

        assertFailsWith<ThreemaException> {
            verifier.verify(OnPremConfigTestData.goodOppf)
        }
    }

    @Test
    fun `empty input is invalid`() {
        val verifier = OnPremConfigVerifier(arrayOf(OnPremConfigTestData.PUBLIC_KEY))

        assertFailsWith<ThreemaException> {
            verifier.verify("")
        }
    }
}

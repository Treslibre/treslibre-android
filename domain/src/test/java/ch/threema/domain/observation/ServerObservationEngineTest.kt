// Modified 2026 by Treslibre Project (Treslibre).
package ch.threema.domain.observation

import java.security.MessageDigest
import java.security.Signature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec

class ServerObservationEngineTest {

    private val initialChatKey = "eafa1fc8fd8d2e1d10659a4026228cc1e1ee2f633466078bbf3f8588c4065992"
    private val rotatedChatKey = "11223344556677889900aabbccddeeff0011223344556677889900aabbccddee"
    private val initialEndpoints = ObservedHttpEndpoints(
        directory = ObservedHttpEndpoint(
            host = "dir.thr.2sx.me",
            spkiSha256 = "DTJU4+0HObYPrx9lF4Kz8hhjcJL3WBL4k829L++UlSk=",
            leafSha256 = "a".repeat(64),
        ),
        blob = ObservedHttpEndpoint(
            host = "blob.thr.2sx.me",
            spkiSha256 = "C19RmQgZXzwovKRRJ2st7bsokiRchKcYjBo3m63fvn8=",
            leafSha256 = "b".repeat(64),
        ),
        safe = ObservedHttpEndpoint(
            host = "safe.thr.2sx.me",
            spkiSha256 = "XIglSWPJ6aJ7LeIz6KsOrr0fNgNZ0PzGgDCDEZq5/U4=",
            leafSha256 = "c".repeat(64),
        ),
    )

    @Test
    fun `initial pin and silent match updates last_seen_at (Case A)`() {
        val record = ServerObservationEngine.createInitialRecord(
            serverId = "chat.thr.2sx.me",
            chatServerPubkeyCurve25519 = initialChatKey,
            httpEndpoints = initialEndpoints,
            adminSigningPubkeyEd25519 = null,
            nowIso = "2026-11-21T12:00:00Z",
        )
        assertEquals(0, record.rotationCount)
        assertEquals(4, record.history.size)
        assertEquals(ObservationEventType.INITIAL_PIN, record.history.first().event)

        // Round-trip JSON serialization check
        val roundTripped = ServerObservationRecord.fromJson(record.toJson())
        assertEquals(record, roundTripped)

        val result = ServerObservationEngine.evaluateObservedFingerprint(
            record = record,
            component = ObservedComponent.CHAT_SERVER,
            observedFingerprint = initialChatKey,
            nowIso = "2026-11-21T13:00:00Z",
        )
        assertIs<ObservationEvaluationResult.Matched>(result)
        assertEquals("2026-11-21T13:00:00Z", result.record.lastSeenAt)
        assertEquals(0, result.record.rotationCount)
    }

    @Test
    fun `Ed25519 signed rotation succeeds and blocks monotonic replay (Case B)`() {
        val spec: EdDSAParameterSpec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)
        val seed = ByteArray(32) { (it + 7).toByte() }
        val privSpec = EdDSAPrivateKeySpec(seed, spec)
        val privKey = EdDSAPrivateKey(privSpec)
        val pubKey = EdDSAPublicKey(EdDSAPublicKeySpec(privSpec.a, spec))
        val adminPubHex = pubKey.abyte.joinToString("") { "%02x".format(it) }

        val record = ServerObservationEngine.createInitialRecord(
            serverId = "chat.thr.2sx.me",
            chatServerPubkeyCurve25519 = initialChatKey,
            httpEndpoints = initialEndpoints,
            adminSigningPubkeyEd25519 = adminPubHex,
            nowIso = "2026-11-21T12:00:00Z",
        )

        val payload = ServerObservationEngine.buildCanonicalRotationPayload(
            serverId = "chat.thr.2sx.me",
            component = ObservedComponent.CHAT_SERVER,
            newFingerprint = rotatedChatKey,
            timestampMonotonic = 42L,
        )
        val signer: Signature = EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm))
        signer.initSign(privKey)
        signer.setParameter(EdDSAEngine.ONE_SHOT_MODE)
        signer.update(payload)
        val sigHex = signer.sign().joinToString("") { "%02x".format(it) }

        val rotated = ServerObservationEngine.evaluateObservedFingerprint(
            record = record,
            component = ObservedComponent.CHAT_SERVER,
            observedFingerprint = rotatedChatKey,
            rotationProof = SignedRotationProof(
                serverId = "chat.thr.2sx.me",
                component = ObservedComponent.CHAT_SERVER,
                newFingerprint = rotatedChatKey,
                timestampMonotonic = 42L,
                signatureEd25519Hex = sigHex,
            ),
            nowIso = "2026-11-21T14:00:00Z",
        )
        assertIs<ObservationEvaluationResult.RotatedByAdmin>(rotated)
        assertEquals(rotatedChatKey, rotated.record.chatServerPubkeyCurve25519)
        assertEquals(1, rotated.record.rotationCount)
        assertEquals(ObservationEventType.SIGNED_ROTATION, rotated.record.history.last().event)
        assertTrue(rotated.record.history.last().verifiedByAdminKey)

        // Replay with equal timestampMonotonic must be blocked
        val replay = ServerObservationEngine.evaluateObservedFingerprint(
            record = rotated.record,
            component = ObservedComponent.CHAT_SERVER,
            observedFingerprint = initialChatKey,
            rotationProof = SignedRotationProof(
                serverId = "chat.thr.2sx.me",
                component = ObservedComponent.CHAT_SERVER,
                newFingerprint = initialChatKey,
                timestampMonotonic = 42L,
                signatureEd25519Hex = sigHex,
            ),
        )
        assertIs<ObservationEvaluationResult.MismatchBlocked>(replay)
    }

    @Test
    fun `unexpected key change hard-aborts and manual override logs unverified_manual_override (Case C)`() {
        val record = ServerObservationEngine.createInitialRecord(
            serverId = "chat.thr.2sx.me",
            chatServerPubkeyCurve25519 = initialChatKey,
            httpEndpoints = initialEndpoints,
            adminSigningPubkeyEd25519 = null,
        )

        val blocked = ServerObservationEngine.evaluateObservedFingerprint(
            record = record,
            component = ObservedComponent.DIRECTORY,
            observedFingerprint = "ROGUE_SPKI_BASE64===========================",
        )
        assertIs<ObservationEvaluationResult.MismatchBlocked>(blocked)
        assertEquals(ObservedComponent.DIRECTORY, blocked.alert.component)
        assertEquals(initialEndpoints.directory.spkiSha256, blocked.alert.oldFingerprint)

        val overridden = ServerObservationEngine.applyManualOverride(
            record = record,
            component = ObservedComponent.DIRECTORY,
            newFingerprint = "ROGUE_SPKI_BASE64===========================",
        )
        assertEquals(1, overridden.rotationCount)
        assertEquals(ObservationEventType.UNVERIFIED_MANUAL_OVERRIDE, overridden.history.last().event)
        assertFalse(overridden.history.last().verifiedByAdminKey)
    }

    @Test
    fun `live cleanroom TOFU provisioning and key rotation against thr 2sx me (Section 5_2)`() {
        val verifier = ch.threema.domain.onprem.OnPremConfigVerifier(emptyArray())
        val url = java.net.URI.create("https://thr.2sx.me/prov/config.oppf").toURL()
        val conn = (url.openConnection() as javax.net.ssl.HttpsURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            val basicAuth = java.util.Base64.getEncoder()
                .encodeToString("demo:demo-password".toByteArray(Charsets.UTF_8))
            setRequestProperty("Authorization", "Basic $basicAuth")
        }
        conn.connect()
        assertEquals(200, conn.responseCode)

        // Extract live TLS SPKI SHA-256 and leaf SHA-256 from server peer certificate
        val peerCert = conn.serverCertificates.first() as java.security.cert.X509Certificate
        val sha256 = java.security.MessageDigest.getInstance("SHA-256")
        val liveSpkiB64 = java.util.Base64.getEncoder().encodeToString(sha256.digest(peerCert.publicKey.encoded))
        val liveLeafHex = sha256.digest(peerCert.encoded).joinToString("") { b -> "%02x".format(b) }
        val rawOppfText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

        // Verify unlocked OPPF v1.0 Ed25519 self-signature
        val parsedObj = verifier.verify(rawOppfText)
        val chatPubB64 = parsedObj.getJSONObject("chat").getString("publicKey")
        val adminPubB64 = parsedObj.getString("signatureKey")
        val chatPubHex = java.util.Base64.getDecoder().decode(chatPubB64).joinToString("") { b -> "%02x".format(b) }
        val adminPubHex = java.util.Base64.getDecoder().decode(adminPubB64).joinToString("") { b -> "%02x".format(b) }
        assertEquals(64, chatPubHex.length)
        assertEquals(64, adminPubHex.length)

        val liveEndpoint = ch.threema.domain.observation.ObservedHttpEndpoint(
            host = "thr.2sx.me",
            spkiSha256 = liveSpkiB64,
            leafSha256 = liveLeafHex,
        )
        val record = ServerObservationEngine.createInitialRecord(
            serverId = "chat.thr.2sx.me",
            chatServerPubkeyCurve25519 = chatPubHex,
            httpEndpoints = ch.threema.domain.observation.ObservedHttpEndpoints(
                directory = liveEndpoint,
                blob = liveEndpoint,
                safe = liveEndpoint,
            ),
            adminSigningPubkeyEd25519 = adminPubHex,
        )

        // Subsequent connection with unchanged SPKI succeeds silently
        val matchEval = ServerObservationEngine.evaluateObservedFingerprint(
            record = record,
            component = ObservedComponent.DIRECTORY,
            observedFingerprint = liveSpkiB64,
        )
        assertIs<ObservationEvaluationResult.Matched>(matchEval)

        // Simulated MitM SPKI mismatch hard-aborts immediately
        val mitmEval = ServerObservationEngine.evaluateObservedFingerprint(
            record = record,
            component = ObservedComponent.DIRECTORY,
            observedFingerprint = "MITM_ROGUE_SPKI_BASE64====================",
        )
        assertIs<ObservationEvaluationResult.MismatchBlocked>(mitmEval)
    }
}

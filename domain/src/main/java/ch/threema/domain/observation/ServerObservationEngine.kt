// Modified 2026 by Treslibre Project (Treslibre).
package ch.threema.domain.observation

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.Signature
import java.time.Instant
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec

/**
 * Pure domain evaluator for the TOFU Local Observation System (§4.2–§4.3).
 */
object ServerObservationEngine {

    fun createInitialRecord(
        serverId: String,
        chatServerPubkeyCurve25519: String,
        httpEndpoints: ObservedHttpEndpoints,
        adminSigningPubkeyEd25519: String?,
        nowIso: String = Instant.now().toString(),
    ): ServerObservationRecord {
        val chatHex = chatServerPubkeyCurve25519.lowercase()
        val normalizedEndpoints = ObservedHttpEndpoints(
            directory = httpEndpoints.directory.copy(leafSha256 = httpEndpoints.directory.leafSha256.lowercase()),
            blob = httpEndpoints.blob.copy(leafSha256 = httpEndpoints.blob.leafSha256.lowercase()),
            safe = httpEndpoints.safe.copy(leafSha256 = httpEndpoints.safe.leafSha256.lowercase()),
        )
        return ServerObservationRecord(
            serverId = serverId,
            chatServerPubkeyCurve25519 = chatHex,
            httpEndpoints = normalizedEndpoints,
            adminSigningPubkeyEd25519 = adminSigningPubkeyEd25519?.lowercase(),
            firstSeenAt = nowIso,
            lastSeenAt = nowIso,
            rotationCount = 0,
            history = listOf(
                ObservationHistoryEntry(
                    timestamp = nowIso,
                    event = ObservationEventType.INITIAL_PIN,
                    component = ObservedComponent.CHAT_SERVER,
                    oldFingerprint = null,
                    newFingerprint = chatHex,
                    verifiedByAdminKey = false,
                ),
                ObservationHistoryEntry(
                    timestamp = nowIso,
                    event = ObservationEventType.INITIAL_PIN,
                    component = ObservedComponent.DIRECTORY,
                    oldFingerprint = null,
                    newFingerprint = normalizedEndpoints.directory.spkiSha256,
                    verifiedByAdminKey = false,
                ),
                ObservationHistoryEntry(
                    timestamp = nowIso,
                    event = ObservationEventType.INITIAL_PIN,
                    component = ObservedComponent.BLOB,
                    oldFingerprint = null,
                    newFingerprint = normalizedEndpoints.blob.spkiSha256,
                    verifiedByAdminKey = false,
                ),
                ObservationHistoryEntry(
                    timestamp = nowIso,
                    event = ObservationEventType.INITIAL_PIN,
                    component = ObservedComponent.SAFE,
                    oldFingerprint = null,
                    newFingerprint = normalizedEndpoints.safe.spkiSha256,
                    verifiedByAdminKey = false,
                ),
            ),
        )
    }

    /**
     * Build the canonical message bytes covered by an admin Ed25519 rotation signature (§4.3):
     * `"${server_id}||${component}||${new_fingerprint}||${timestamp_monotonic}"`
     */
    fun buildCanonicalRotationPayload(
        serverId: String,
        component: ObservedComponent,
        newFingerprint: String,
        timestampMonotonic: Long,
    ): ByteArray =
        "$serverId||${component.wireName}||$newFingerprint||$timestampMonotonic"
            .toByteArray(StandardCharsets.UTF_8)

    fun getPinnedFingerprintAndHost(
        record: ServerObservationRecord,
        component: ObservedComponent,
    ): Pair<String, String> = when (component) {
        ObservedComponent.CHAT_SERVER -> record.chatServerPubkeyCurve25519 to record.serverId
        ObservedComponent.DIRECTORY -> record.httpEndpoints.directory.spkiSha256 to record.httpEndpoints.directory.host
        ObservedComponent.BLOB -> record.httpEndpoints.blob.spkiSha256 to record.httpEndpoints.blob.host
        ObservedComponent.SAFE -> record.httpEndpoints.safe.spkiSha256 to record.httpEndpoints.safe.host
    }

    fun resolveHttpComponentForHost(
        record: ServerObservationRecord,
        hostname: String,
    ): ObservedComponent? = when {
        record.httpEndpoints.directory.host.equals(hostname, ignoreCase = true) -> ObservedComponent.DIRECTORY
        record.httpEndpoints.blob.host.equals(hostname, ignoreCase = true) -> ObservedComponent.BLOB
        record.httpEndpoints.safe.host.equals(hostname, ignoreCase = true) -> ObservedComponent.SAFE
        else -> null
    }

    /**
     * Evaluate an observed server key or TLS SPKI fingerprint against the stored TOFU record (§4.3).
     */
    fun evaluateObservedFingerprint(
        record: ServerObservationRecord,
        component: ObservedComponent,
        observedFingerprint: String,
        observedLeafSha256: String? = null,
        rotationProof: SignedRotationProof? = null,
        nowIso: String = Instant.now().toString(),
    ): ObservationEvaluationResult {
        val (pinnedFp, host) = getPinnedFingerprintAndHost(record, component)
        val normalizedObserved = if (component == ObservedComponent.CHAT_SERVER) {
            observedFingerprint.lowercase()
        } else {
            observedFingerprint
        }
        val normalizedPinned = if (component == ObservedComponent.CHAT_SERVER) {
            pinnedFp.lowercase()
        } else {
            pinnedFp
        }

        // Case A: Matches stored record -> proceed silently, update last_seen_at
        if (normalizedObserved == normalizedPinned) {
            return ObservationEvaluationResult.Matched(
                record.copy(lastSeenAt = nowIso),
            )
        }

        // Case B: Signed key/cert rotation (admin key present)
        val adminKeyHex = record.adminSigningPubkeyEd25519
        if (rotationProof != null && !adminKeyHex.isNullOrEmpty()) {
            val normalizedProofFp = if (component == ObservedComponent.CHAT_SERVER) {
                rotationProof.newFingerprint.lowercase()
            } else {
                rotationProof.newFingerprint
            }
            if (
                rotationProof.serverId == record.serverId &&
                rotationProof.component == component &&
                normalizedProofFp == normalizedObserved &&
                rotationProof.timestampMonotonic > record.lastRotationMonotonic
            ) {
                val payload = buildCanonicalRotationPayload(
                    serverId = rotationProof.serverId,
                    component = component,
                    newFingerprint = rotationProof.newFingerprint,
                    timestampMonotonic = rotationProof.timestampMonotonic,
                )
                if (verifyEd25519Hex(adminKeyHex, payload, rotationProof.signatureEd25519Hex)) {
                    val updated = applyPinUpdate(
                        record = record,
                        component = component,
                        newFingerprint = normalizedObserved,
                        newLeafSha256 = observedLeafSha256 ?: rotationProof.newLeafSha256,
                        event = ObservationEventType.SIGNED_ROTATION,
                        verifiedByAdminKey = true,
                        nowIso = nowIso,
                        monotonicTimestamp = rotationProof.timestampMonotonic,
                    )
                    return ObservationEvaluationResult.RotatedByAdmin(updated)
                }
            }
        }

        // Case C: Unexpected key/cert change -> Hard-abort
        return ObservationEvaluationResult.MismatchBlocked(
            ServerKeyChangeAlert(
                serverId = record.serverId,
                component = component,
                host = host,
                oldFingerprint = normalizedPinned,
                newFingerprint = normalizedObserved,
                newLeafSha256 = observedLeafSha256,
                detectedAt = nowIso,
            ),
        )
    }

    fun applyManualOverride(
        record: ServerObservationRecord,
        component: ObservedComponent,
        newFingerprint: String,
        newLeafSha256: String? = null,
        nowIso: String = Instant.now().toString(),
    ): ServerObservationRecord = applyPinUpdate(
        record = record,
        component = component,
        newFingerprint = if (component == ObservedComponent.CHAT_SERVER) newFingerprint.lowercase() else newFingerprint,
        newLeafSha256 = newLeafSha256,
        event = ObservationEventType.UNVERIFIED_MANUAL_OVERRIDE,
        verifiedByAdminKey = false,
        nowIso = nowIso,
        monotonicTimestamp = record.lastRotationMonotonic,
    )

    private fun applyPinUpdate(
        record: ServerObservationRecord,
        component: ObservedComponent,
        newFingerprint: String,
        newLeafSha256: String?,
        event: ObservationEventType,
        verifiedByAdminKey: Boolean,
        nowIso: String,
        monotonicTimestamp: Long,
    ): ServerObservationRecord {
        val (oldFp, _) = getPinnedFingerprintAndHost(record, component)
        val updatedEndpoints = record.httpEndpoints.copy(
            directory = if (component == ObservedComponent.DIRECTORY) {
                record.httpEndpoints.directory.copy(
                    spkiSha256 = newFingerprint,
                    leafSha256 = newLeafSha256?.lowercase() ?: record.httpEndpoints.directory.leafSha256,
                )
            } else {
                record.httpEndpoints.directory
            },
            blob = if (component == ObservedComponent.BLOB) {
                record.httpEndpoints.blob.copy(
                    spkiSha256 = newFingerprint,
                    leafSha256 = newLeafSha256?.lowercase() ?: record.httpEndpoints.blob.leafSha256,
                )
            } else {
                record.httpEndpoints.blob
            },
            safe = if (component == ObservedComponent.SAFE) {
                record.httpEndpoints.safe.copy(
                    spkiSha256 = newFingerprint,
                    leafSha256 = newLeafSha256?.lowercase() ?: record.httpEndpoints.safe.leafSha256,
                )
            } else {
                record.httpEndpoints.safe
            },
        )
        return record.copy(
            chatServerPubkeyCurve25519 = if (component == ObservedComponent.CHAT_SERVER) {
                newFingerprint.lowercase()
            } else {
                record.chatServerPubkeyCurve25519
            },
            httpEndpoints = updatedEndpoints,
            lastSeenAt = nowIso,
            rotationCount = record.rotationCount + 1,
            lastRotationMonotonic = monotonicTimestamp,
            history = record.history + ObservationHistoryEntry(
                timestamp = nowIso,
                event = event,
                component = component,
                oldFingerprint = oldFp,
                newFingerprint = newFingerprint,
                verifiedByAdminKey = verifiedByAdminKey,
            ),
        )
    }

    fun verifyEd25519Hex(publicKeyHex: String, message: ByteArray, signatureHex: String): Boolean =
        try {
            val pubBytes = hexToBytes(publicKeyHex)
            val sigBytes = hexToBytes(signatureHex)
            if (pubBytes.size != 32 || sigBytes.size != 64) {
                false
            } else {
                val spec: EdDSAParameterSpec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)
                val verifier: Signature = EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm))
                val edPub = EdDSAPublicKey(EdDSAPublicKeySpec(pubBytes, spec))
                verifier.initVerify(edPub)
                verifier.setParameter(EdDSAEngine.ONE_SHOT_MODE)
                verifier.update(message)
                verifier.verify(sigBytes)
            }
        } catch (_: Exception) {
            false
        }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}

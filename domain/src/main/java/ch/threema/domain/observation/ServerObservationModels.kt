// Modified 2026 by Treslibre Project (Treslibre).
package ch.threema.domain.observation

import org.json.JSONArray
import org.json.JSONObject

/**
 * Observed component type in the TOFU Local Observation System (§4.2).
 */
enum class ObservedComponent(val wireName: String) {
    CHAT_SERVER("chat_server"),
    DIRECTORY("directory"),
    BLOB("blob"),
    SAFE("safe"),
    ;

    companion object {
        fun fromWireName(wireName: String): ObservedComponent =
            entries.firstOrNull { it.wireName == wireName }
                ?: throw IllegalArgumentException("Unknown ObservedComponent: $wireName")
    }
}

/**
 * History event type for key/certificate pinning and rotations (§4.2).
 */
enum class ObservationEventType(val wireName: String) {
    INITIAL_PIN("initial_pin"),
    SIGNED_ROTATION("signed_rotation"),
    UNVERIFIED_MANUAL_OVERRIDE("unverified_manual_override"),
    ;

    companion object {
        fun fromWireName(wireName: String): ObservationEventType =
            entries.firstOrNull { it.wireName == wireName }
                ?: throw IllegalArgumentException("Unknown ObservationEventType: $wireName")
    }
}

data class ObservedHttpEndpoint(
    val host: String,
    val spkiSha256: String,
    val leafSha256: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("host", host)
        put("spki_sha256", spkiSha256)
        put("leaf_sha256", leafSha256)
    }

    companion object {
        fun fromJson(obj: JSONObject): ObservedHttpEndpoint = ObservedHttpEndpoint(
            host = obj.getString("host"),
            spkiSha256 = obj.getString("spki_sha256"),
            leafSha256 = obj.getString("leaf_sha256"),
        )
    }
}

data class ObservedHttpEndpoints(
    val directory: ObservedHttpEndpoint,
    val blob: ObservedHttpEndpoint,
    val safe: ObservedHttpEndpoint,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("directory", directory.toJson())
        put("blob", blob.toJson())
        put("safe", safe.toJson())
    }

    companion object {
        fun fromJson(obj: JSONObject): ObservedHttpEndpoints = ObservedHttpEndpoints(
            directory = ObservedHttpEndpoint.fromJson(obj.getJSONObject("directory")),
            blob = ObservedHttpEndpoint.fromJson(obj.getJSONObject("blob")),
            safe = ObservedHttpEndpoint.fromJson(obj.getJSONObject("safe")),
        )
    }
}

data class ObservationHistoryEntry(
    val timestamp: String,
    val event: ObservationEventType,
    val component: ObservedComponent,
    val oldFingerprint: String?,
    val newFingerprint: String,
    val verifiedByAdminKey: Boolean,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("timestamp", timestamp)
        put("event", event.wireName)
        put("component", component.wireName)
        put("old_fingerprint", oldFingerprint ?: JSONObject.NULL)
        put("new_fingerprint", newFingerprint)
        put("verified_by_admin_key", verifiedByAdminKey)
    }

    companion object {
        fun fromJson(obj: JSONObject): ObservationHistoryEntry = ObservationHistoryEntry(
            timestamp = obj.getString("timestamp"),
            event = ObservationEventType.fromWireName(obj.getString("event")),
            component = ObservedComponent.fromWireName(obj.getString("component")),
            oldFingerprint = if (obj.isNull("old_fingerprint")) null else obj.getString("old_fingerprint"),
            newFingerprint = obj.getString("new_fingerprint"),
            verifiedByAdminKey = obj.getBoolean("verified_by_admin_key"),
        )
    }
}

/**
 * Canonical schema for each observed server record (§4.2).
 */
data class ServerObservationRecord(
    val serverId: String,
    val chatServerPubkeyCurve25519: String,
    val httpEndpoints: ObservedHttpEndpoints,
    val adminSigningPubkeyEd25519: String?,
    val firstSeenAt: String,
    val lastSeenAt: String,
    val rotationCount: Int,
    val history: List<ObservationHistoryEntry>,
    val lastRotationMonotonic: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("server_id", serverId)
        put("chat_server_pubkey_curve25519", chatServerPubkeyCurve25519)
        put("http_endpoints", httpEndpoints.toJson())
        put("admin_signing_pubkey_ed25519", adminSigningPubkeyEd25519 ?: JSONObject.NULL)
        put("first_seen_at", firstSeenAt)
        put("last_seen_at", lastSeenAt)
        put("rotation_count", rotationCount)
        val histArr = JSONArray()
        history.forEach { histArr.put(it.toJson()) }
        put("history", histArr)
        if (lastRotationMonotonic > 0L) {
            put("last_rotation_monotonic", lastRotationMonotonic)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): ServerObservationRecord {
            val histArr = obj.getJSONArray("history")
            val historyList = mutableListOf<ObservationHistoryEntry>()
            for (i in 0 until histArr.length()) {
                historyList.add(ObservationHistoryEntry.fromJson(histArr.getJSONObject(i)))
            }
            return ServerObservationRecord(
                serverId = obj.getString("server_id"),
                chatServerPubkeyCurve25519 = obj.getString("chat_server_pubkey_curve25519"),
                httpEndpoints = ObservedHttpEndpoints.fromJson(obj.getJSONObject("http_endpoints")),
                adminSigningPubkeyEd25519 = if (obj.isNull("admin_signing_pubkey_ed25519")) {
                    null
                } else {
                    obj.optString("admin_signing_pubkey_ed25519").takeIf { it.isNotEmpty() }
                },
                firstSeenAt = obj.getString("first_seen_at"),
                lastSeenAt = obj.getString("last_seen_at"),
                rotationCount = obj.getInt("rotation_count"),
                history = historyList,
                lastRotationMonotonic = obj.optLong("last_rotation_monotonic", 0L),
            )
        }
    }
}

data class SignedRotationProof(
    val serverId: String,
    val component: ObservedComponent,
    val newFingerprint: String,
    val newLeafSha256: String? = null,
    val timestampMonotonic: Long,
    val signatureEd25519Hex: String,
)

data class ServerKeyChangeAlert(
    val serverId: String,
    val component: ObservedComponent,
    val host: String,
    val oldFingerprint: String,
    val newFingerprint: String,
    val newLeafSha256: String? = null,
    val detectedAt: String,
)

sealed class ObservationEvaluationResult {
    data class Matched(val record: ServerObservationRecord) : ObservationEvaluationResult()
    data class RotatedByAdmin(val record: ServerObservationRecord) : ObservationEvaluationResult()
    data class MismatchBlocked(val alert: ServerKeyChangeAlert) : ObservationEvaluationResult()
}

interface ServerObservationStore {
    fun load(): ServerObservationRecord?
    fun save(record: ServerObservationRecord): ServerObservationRecord
    fun clear()
    fun onKeyMismatchDetected(alert: ServerKeyChangeAlert) {}
}

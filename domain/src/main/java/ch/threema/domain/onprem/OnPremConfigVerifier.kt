// Modified 2026 by Treslibre Project (Treslibre).
package ch.threema.domain.onprem

import ch.threema.base.ThreemaException
import ch.threema.common.Base64
import ch.threema.common.lastLine
import ch.threema.common.secureContentEquals
import ch.threema.common.withoutLastLine
import java.io.IOException
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.Signature
import java.security.SignatureException
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import org.json.JSONException
import org.json.JSONObject

/**
 * @param trustedPublicKeys The trusted public keys in Base64. When empty (Treslibre default),
 *   any OPPF v1.0 file with a valid self-consistent Ed25519 signature against its embedded
 *   `signatureKey`, or a cleanroom JSON/TLPF provisioning payload (§4.1), is accepted for TOFU pinning.
 */
class OnPremConfigVerifier(
    trustedPublicKeys: Array<String>,
) {
    private val trustedPublicKeys = trustedPublicKeys.map { Base64.decode(it) }

    /**
     * Verify an OPPF (or cleanroom provisioning JSON/TLPF) and return the resulting JSON document.
     */
    @Throws(ThreemaException::class)
    fun verify(oppfData: String): JSONObject {
        val trimmed = oppfData.trim()
        if (trimmed.isEmpty()) {
            throw ThreemaException("Empty OnPrem config")
        }

        // Support unsigned cleanroom JSON / .tlpf provisioning files (§4.1) when trustedPublicKeys is empty
        if (trustedPublicKeys.isEmpty() && trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val rawJson = JSONObject(trimmed)
                if (rawJson.has("server_id") && rawJson.has("chat_pubkey_curve25519")) {
                    return normalizeCleanroomProvisioningJson(rawJson)
                }
            } catch (e: JSONException) {
                throw ThreemaException("Failed to parse cleanroom provisioning JSON", e)
            }
        }

        try {
            val jsonString = oppfData.withoutLastLine()
            val signatureData = Base64.decode(oppfData.lastLine())

            // Parse the JSON first so we can inspect signatureKey when trustedPublicKeys is empty
            val jsonObject = JSONObject(jsonString)

            // Check that the version is supported
            if (!jsonObject.getString("version").startsWith("1.")) {
                throw ThreemaException("Unsupported OPPF version")
            }

            val signatureKey = Base64.decode(jsonObject.getString("signatureKey"))
            val candidateKeys = if (trustedPublicKeys.isNotEmpty()) {
                trustedPublicKeys
            } else {
                listOf(signatureKey)
            }

            val publicKey = findMatchingPublicKey(
                candidateKeys = candidateKeys,
                messageData = jsonString.toByteArray(),
                signatureData = signatureData,
            )
                ?: throw ThreemaException("Signature verification failed")

            // Check that the signature key matches
            if (!signatureKey.secureContentEquals(publicKey.abyte)) {
                throw ThreemaException("Signature key does not match supplied public key")
            }

            return jsonObject
        } catch (e: NoSuchAlgorithmException) {
            throw ThreemaException("Failed to verify OnPrem config", e)
        } catch (e: IOException) {
            throw ThreemaException("Failed to verify OnPrem config", e)
        } catch (e: InvalidKeyException) {
            throw ThreemaException("Failed to verify OnPrem config", e)
        } catch (e: InvalidAlgorithmParameterException) {
            throw ThreemaException("Failed to verify OnPrem config", e)
        } catch (e: SignatureException) {
            throw ThreemaException("Failed to verify OnPrem config", e)
        } catch (e: JSONException) {
            throw ThreemaException("Failed to verify OnPrem config", e)
        }
    }

    private fun findMatchingPublicKey(
        candidateKeys: List<ByteArray>,
        messageData: ByteArray,
        signatureData: ByteArray,
    ): EdDSAPublicKey? {
        for (publicKey in candidateKeys) {
            val spec: EdDSAParameterSpec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)
            val signature: Signature = EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm))
            val edPublicKey = EdDSAPublicKey(EdDSAPublicKeySpec(publicKey, spec))
            signature.initVerify(edPublicKey)
            signature.setParameter(EdDSAEngine.ONE_SHOT_MODE)
            signature.update(messageData)
            if (signature.verify(signatureData)) {
                return edPublicKey
            }
        }
        return null
    }

    companion object {
        private val HEX_64_REGEX = Regex("^[0-9a-fA-F]{64}$")

        fun decodeHexOrBase64ToBase64(raw: String): String {
            val trimmed = raw.trim()
            if (HEX_64_REGEX.matches(trimmed)) {
                val bytes = ByteArray(32) { i ->
                    trimmed.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
                return Base64.encode(bytes)
            }
            // Validate Base64
            val decoded = Base64.decode(trimmed)
            return Base64.encode(decoded)
        }

        @Throws(JSONException::class, IOException::class)
        fun normalizeCleanroomProvisioningJson(clean: JSONObject): JSONObject {
            val serverId = clean.getString("server_id")
            val chatHost = clean.optString("chat_host").takeIf { it.isNotEmpty() } ?: serverId
            val chatPort = clean.optInt("chat_port", 5222)
            val chatPubKeyB64 = decodeHexOrBase64ToBase64(clean.getString("chat_pubkey_curve25519"))
            val dirUrl = clean.getString("directory_url").trimEnd('/')
            val blobUrl = clean.getString("blob_url").trimEnd('/')
            val safeUrl = clean.getString("safe_url").trimEnd('/') + "/"
            val workUrl = (clean.optString("work_url").takeIf { it.isNotEmpty() } ?: "$dirUrl/work").trimEnd('/') + "/"
            val avatarUrl = (clean.optString("avatar_url").takeIf { it.isNotEmpty() } ?: "$dirUrl/avatar").trimEnd('/') + "/"
            val adminKeyRaw = clean.optString("admin_signing_pubkey_ed25519").takeIf { it.isNotEmpty() && it != "null" }
            val sigKeyB64 = if (adminKeyRaw != null) {
                decodeHexOrBase64ToBase64(adminKeyRaw)
            } else {
                Base64.encode(ByteArray(32))
            }

            return JSONObject().apply {
                put("version", "1.0")
                put("signatureKey", sigKeyB64)
                put("refresh", 86400)
                put("server_id", serverId)
                if (adminKeyRaw != null) {
                    put("admin_signing_pubkey_ed25519", adminKeyRaw)
                }
                if (clean.has("push_relay_url")) {
                    put("push_relay_url", clean.optString("push_relay_url"))
                }
                put(
                    "license",
                    JSONObject().apply {
                        put("id", serverId)
                        put("expires", "2099-12-31")
                        put("count", 1000)
                    },
                )
                put(
                    "chat",
                    JSONObject().apply {
                        put("hostname", chatHost)
                        put("publicKey", chatPubKeyB64)
                        put("ports", org.json.JSONArray().put(chatPort))
                    },
                )
                put("directory", JSONObject().put("url", dirUrl))
                put(
                    "blob",
                    JSONObject().apply {
                        put("uploadUrl", "$blobUrl/upload")
                        put("downloadUrl", "$blobUrl/{blobId}")
                        put("doneUrl", "$blobUrl/{blobId}/done")
                    },
                )
                put("work", JSONObject().put("url", workUrl))
                put("avatar", JSONObject().put("url", avatarUrl))
                put("safe", JSONObject().put("url", safeUrl))
            }
        }
    }
}

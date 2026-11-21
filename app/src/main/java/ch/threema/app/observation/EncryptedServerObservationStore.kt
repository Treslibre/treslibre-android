// Modified 2026 by Treslibre Project (Treslibre).
package ch.threema.app.observation

import android.content.Context
import android.content.Intent
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.observation.ObservedComponent
import ch.threema.domain.observation.ObservedHttpEndpoint
import ch.threema.domain.observation.ObservedHttpEndpoints
import ch.threema.domain.observation.ServerKeyChangeAlert
import ch.threema.domain.observation.ServerObservationEngine
import ch.threema.domain.observation.ServerObservationRecord
import ch.threema.domain.observation.ServerObservationStore
import java.io.File
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import org.json.JSONObject

private val logger = getThreemaLogger("EncryptedServerObservationStore")

/**
 * Persistent, encrypted local store for the TOFU Server Observation Record (§4.2) on Android,
 * backed by Android Keystore AES-256-GCM (with deterministic AES-256-GCM fallback in JVM unit tests).
 */
class EncryptedServerObservationStore(
    private val context: Context,
    private val file: File = File(context.filesDir, FILE_NAME),
) : ServerObservationStore {

    @Volatile
    private var cachedRecord: ServerObservationRecord? = null

    @Synchronized
    override fun load(): ServerObservationRecord? {
        cachedRecord?.let { return it }
        if (!file.exists()) {
            return null
        }
        return try {
            val encryptedBytes = file.readBytes()
            val plainJson = decryptPayload(encryptedBytes)
            val parsed = ServerObservationRecord.fromJson(JSONObject(plainJson))
            cachedRecord = parsed
            parsed
        } catch (e: Exception) {
            logger.error("Failed to load encrypted server observation record", e)
            null
        }
    }

    @Synchronized
    override fun save(record: ServerObservationRecord): ServerObservationRecord {
        val jsonStr = record.toJson().toString(2)
        val encrypted = encryptPayload(jsonStr)
        file.parentFile?.mkdirs()
        file.writeBytes(encrypted)
        cachedRecord = record
        return record
    }

    @Synchronized
    override fun clear() {
        cachedRecord = null
        if (file.exists()) {
            file.delete()
        }
    }

    override fun onKeyMismatchDetected(alert: ServerKeyChangeAlert) {
        try {
            val intent = ServerKeyChangedActivity.createIntent(context, alert).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            logger.error("Unable to launch ServerKeyChangedActivity", e)
        }
    }

    private fun encryptPayload(plainUtf8: String): ByteArray {
        val plainBytes = plainUtf8.toByteArray(StandardCharsets.UTF_8)
        return try {
            val key = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plainBytes)
            byteArrayOf(MODE_KEYSTORE, iv.size.toByte()) + iv + ciphertext
        } catch (_: Exception) {
            val key = deriveFallbackKey()
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val ciphertext = cipher.doFinal(plainBytes)
            byteArrayOf(MODE_FALLBACK, iv.size.toByte()) + iv + ciphertext
        }
    }

    private fun decryptPayload(payload: ByteArray): String {
        require(payload.size > 14) { "Invalid encrypted observation payload" }
        val mode = payload[0]
        val ivLen = payload[1].toInt() and 0xFF
        val iv = payload.copyOfRange(2, 2 + ivLen)
        val ciphertext = payload.copyOfRange(2 + ivLen, payload.size)
        val key: SecretKey = if (mode == MODE_KEYSTORE) {
            getOrCreateKeystoreKey()
        } else {
            deriveFallbackKey()
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) {
            return existing.secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun deriveFallbackKey(): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(("treslibre-android-observation-v1:" + file.absolutePath).toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    companion object {
        const val FILE_NAME = "treslibre_server_observation.enc"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "treslibre_server_observation_aes256"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val MODE_KEYSTORE: Byte = 1
        private const val MODE_FALLBACK: Byte = 2

        @Volatile
        private var globalInstance: EncryptedServerObservationStore? = null

        @JvmStatic
        fun getInstance(context: Context): EncryptedServerObservationStore =
            globalInstance ?: synchronized(this) {
                globalInstance ?: EncryptedServerObservationStore(context.applicationContext).also {
                    globalInstance = it
                }
            }

        @JvmStatic
        fun bytesToHex(bytes: ByteArray): String =
            bytes.joinToString("") { "%02x".format(it) }

        /**
         * Probe a live HTTPS endpoint to extract its leaf certificate SPKI SHA-256 (Base64)
         * and leaf SHA-256 (64-char hex) for the First-Time Server Trust Prompt (§4.1).
         */
        @JvmStatic
        fun probeHttpsEndpoint(urlString: String): ObservedHttpEndpoint {
            val normalizedUrl = if (urlString.startsWith("http://") || urlString.startsWith("https://")) {
                urlString
            } else {
                "https://$urlString"
            }
            val parsed = URL(normalizedUrl)
            val host = parsed.host
            val port = if (parsed.port != -1) parsed.port else 443

            return try {
                var capturedCert: X509Certificate? = null
                val trustAll = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                        capturedCert = chain?.firstOrNull()
                    }
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf(trustAll), SecureRandom())
                (sslContext.socketFactory.createSocket(host, port) as SSLSocket).use { socket ->
                    socket.soTimeout = 7000
                    socket.startHandshake()
                    val cert = capturedCert
                        ?: (socket.session.peerCertificates.firstOrNull() as? X509Certificate)
                        ?: throw IllegalStateException("No certificate presented by $host:$port")
                    val spkiSha256 = ch.threema.common.Base64.encode(
                        MessageDigest.getInstance("SHA-256").digest(cert.publicKey.encoded),
                    )
                    val leafSha256 = bytesToHex(
                        MessageDigest.getInstance("SHA-256").digest(cert.encoded),
                    )
                    ObservedHttpEndpoint(
                        host = host,
                        spkiSha256 = spkiSha256,
                        leafSha256 = leafSha256,
                    )
                }
            } catch (e: Exception) {
                logger.warn("Live TLS probe for $host failed, using placeholder pending first connection", e)
                ObservedHttpEndpoint(
                    host = host,
                    spkiSha256 = "UNPROBED",
                    leafSha256 = "0".repeat(64),
                )
            }
        }
    }
}

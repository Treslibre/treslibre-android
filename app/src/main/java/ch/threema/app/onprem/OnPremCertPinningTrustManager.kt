// Modified 2026 by Treslibre Project (Treslibre).
package ch.threema.app.onprem

import android.annotation.SuppressLint
import ch.threema.common.Base64
import ch.threema.common.secureContentEquals
import ch.threema.common.sha256
import ch.threema.common.toHexString
import ch.threema.domain.libthreema.LibthreemaAware
import ch.threema.domain.observation.ObservationEvaluationResult
import ch.threema.domain.observation.ServerObservationEngine
import ch.threema.domain.observation.ServerObservationStore
import ch.threema.domain.onprem.OnPremConfigDomainRuleMatchMode
import ch.threema.domain.onprem.OnPremConfigDomainRuleSpkiAlgorithm
import ch.threema.domain.onprem.OnPremConfigDomains
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

@SuppressLint("CustomX509TrustManager")
@LibthreemaAware
class OnPremCertPinningTrustManager(
    private val getOnPremConfigDomains: () -> OnPremConfigDomains?,
    private val hostnameProvider: HostnameProvider,
    private val delegate: TrustManagerDelegate = TrustManagerDelegate(),
    private val observationStore: ServerObservationStore? = null,
) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        delegate.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {
        val hostname = try {
            hostnameProvider.getHostname()
        } catch (e: IllegalStateException) {
            throw CertificateException(e)
        }
        delegate.checkServerTrusted(chain, authType, hostname)

        val certificate = chain?.firstOrNull()
            ?: throw CertificateException("No certificate found in trust chain")

        // Evaluate against the TOFU Local Observation Store (§4.3)
        val record = observationStore?.load()
        if (record != null) {
            val component = ServerObservationEngine.resolveHttpComponentForHost(record, hostname)
            if (component != null) {
                val spkiBase64 = Base64.encode(sha256(certificate.publicKey.encoded))
                val leafHex = sha256(certificate.encoded).toHexString().lowercase()
                when (
                    val eval = ServerObservationEngine.evaluateObservedFingerprint(
                        record = record,
                        component = component,
                        observedFingerprint = spkiBase64,
                        observedLeafSha256 = leafHex,
                    )
                ) {
                    is ObservationEvaluationResult.Matched -> {
                        observationStore.save(eval.record)
                    }
                    is ObservationEvaluationResult.RotatedByAdmin -> {
                        observationStore.save(eval.record)
                    }
                    is ObservationEvaluationResult.MismatchBlocked -> {
                        observationStore.onKeyMismatchDetected(eval.alert)
                        throw OnPremCertificateException(hostname)
                    }
                }
            }
        }

        val rules = getOnPremConfigDomains()?.rules ?: return

        rules.forEach { rule ->
            val matchesHostname = when (rule.matchMode) {
                OnPremConfigDomainRuleMatchMode.EXACT -> hostname.equals(rule.fqdn, ignoreCase = true)
                OnPremConfigDomainRuleMatchMode.INCLUDE_SUBDOMAINS -> {
                    hostname.equals(rule.fqdn, ignoreCase = true) || hostname.endsWith(".${rule.fqdn}", ignoreCase = true)
                }
            }
            if (matchesHostname) {
                val matchesCertificate = rule.spkis?.any { spki ->
                    certificate.getPublicKeyFingerprintFor(spki.algorithm).secureContentEquals(Base64.decode(spki.value))
                }
                if (matchesCertificate == false) {
                    throw OnPremCertificateException(hostname)
                }
            }
        }
    }

    private fun X509Certificate.getPublicKeyFingerprintFor(algorithm: OnPremConfigDomainRuleSpkiAlgorithm): ByteArray =
        when (algorithm) {
            OnPremConfigDomainRuleSpkiAlgorithm.SHA256 -> sha256(publicKey.encoded)
        }

    override fun getAcceptedIssuers(): Array<X509Certificate> =
        delegate.getAcceptedIssuers()
}

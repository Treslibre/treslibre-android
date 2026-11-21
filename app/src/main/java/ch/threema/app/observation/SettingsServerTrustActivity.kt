// Modified 2026 by Treslibre Project (Treslibre).
package ch.threema.app.observation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import ch.threema.app.activities.EnterSerialActivity

/**
 * Settings > Server Trust & Keys inspection screen (§4.4).
 * Displays:
 * - Active server hostname / ID and endpoint URLs
 * - Pinned Chat Server Curve25519 Public Key fingerprint
 * - Pinned HTTP endpoint TLS SPKI SHA-256 & Leaf Certificate SHA-256 fingerprints
 * - Admin Signing Ed25519 Public Key (if present)
 * - First Seen timestamp, Last Verified timestamp, and full key rotation history log
 * - Actions: Copy / Share QR for out-of-band verification, and Reset Server Configuration
 */
class SettingsServerTrustActivity : AppCompatActivity() {

    private lateinit var detailsTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Server Trust & Keys (TOFU)"

        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val headerView = TextView(this).apply {
            text = "Server Trust & Keys (TOFU Local Observation Store)"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        }
        container.addView(headerView)

        detailsTextView = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, 24, 0, 24)
        }
        container.addView(detailsTextView)

        val copyButton = Button(this).apply {
            text = "Copy Fingerprints & History (JSON)"
            setOnClickListener {
                val record = EncryptedServerObservationStore.getInstance(this@SettingsServerTrustActivity).load()
                if (record == null) {
                    Toast.makeText(this@SettingsServerTrustActivity, "No pinned server record", Toast.LENGTH_SHORT).show()
                } else {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("Treslibre Server Trust", record.toJson().toString(2)),
                    )
                    Toast.makeText(this@SettingsServerTrustActivity, "Copied server trust JSON to clipboard", Toast.LENGTH_SHORT).show()
                }
            }
        }
        container.addView(copyButton)

        val qrShareButton = Button(this).apply {
            text = "Show Out-of-Band Verification URI"
            setOnClickListener {
                val record = EncryptedServerObservationStore.getInstance(this@SettingsServerTrustActivity).load()
                if (record != null) {
                    val uri = "treslibre://verify-server?id=${record.serverId}" +
                        "&chat_pk=${record.chatServerPubkeyCurve25519}" +
                        "&dir_spki=${record.httpEndpoints.directory.spkiSha256}"
                    AlertDialog.Builder(this@SettingsServerTrustActivity)
                        .setTitle("Out-of-Band Server Verification URI")
                        .setMessage(uri)
                        .setPositiveButton("Copy URI") { _, _ ->
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Treslibre Verify URI", uri))
                        }
                        .setNegativeButton("Close", null)
                        .show()
                }
            }
        }
        container.addView(qrShareButton)

        val resetButton = Button(this).apply {
            text = "Reset Server Configuration…"
            setOnClickListener {
                AlertDialog.Builder(this@SettingsServerTrustActivity)
                    .setTitle("Reset Pinned Server Configuration?")
                    .setMessage(
                        "This will clear the pinned TOFU Server Observation Record and return to the initial server setup screen.",
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Reset & Clear Pins") { _, _ ->
                        EncryptedServerObservationStore.getInstance(this@SettingsServerTrustActivity).clear()
                        refreshDisplay()
                        startActivity(EnterSerialActivity.createIntent(this@SettingsServerTrustActivity))
                    }
                    .show()
            }
        }
        container.addView(resetButton)

        scroll.addView(container)
        setContentView(scroll)
        refreshDisplay()
    }

    private fun refreshDisplay() {
        val record = EncryptedServerObservationStore.getInstance(this).load()
        if (record == null) {
            detailsTextView.text = "No TOFU server observation record is currently pinned."
            return
        }
        val sb = StringBuilder()
        sb.appendLine("Server ID: ${record.serverId}")
        sb.appendLine("Chat Curve25519 Public Key:\n  ${record.chatServerPubkeyCurve25519}")
        sb.appendLine("Directory Endpoint (${record.httpEndpoints.directory.host}):")
        sb.appendLine("  SPKI SHA-256: ${record.httpEndpoints.directory.spkiSha256}")
        sb.appendLine("  Leaf SHA-256: ${record.httpEndpoints.directory.leafSha256}")
        sb.appendLine("Blob Endpoint (${record.httpEndpoints.blob.host}):")
        sb.appendLine("  SPKI SHA-256: ${record.httpEndpoints.blob.spkiSha256}")
        sb.appendLine("  Leaf SHA-256: ${record.httpEndpoints.blob.leafSha256}")
        sb.appendLine("Safe Endpoint (${record.httpEndpoints.safe.host}):")
        sb.appendLine("  SPKI SHA-256: ${record.httpEndpoints.safe.spkiSha256}")
        sb.appendLine("  Leaf SHA-256: ${record.httpEndpoints.safe.leafSha256}")
        sb.appendLine("Admin Signing Ed25519 Key:\n  ${record.adminSigningPubkeyEd25519 ?: "None"}")
        sb.appendLine("First Seen: ${record.firstSeenAt}")
        sb.appendLine("Last Verified: ${record.lastSeenAt}")
        sb.appendLine("Rotation Count: ${record.rotationCount}")
        sb.appendLine("\nHistory (${record.history.size} entries):")
        record.history.forEachIndexed { idx, entry ->
            sb.appendLine(
                "  #${idx + 1} [${entry.timestamp}] ${entry.event.wireName} (${entry.component.wireName}): " +
                    "${entry.oldFingerprint ?: "null"} -> ${entry.newFingerprint} (admin=${entry.verifiedByAdminKey})",
            )
        }
        detailsTextView.text = sb.toString()
    }

    companion object {
        @JvmStatic
        fun createIntent(context: Context): Intent =
            Intent(context, SettingsServerTrustActivity::class.java)
    }
}

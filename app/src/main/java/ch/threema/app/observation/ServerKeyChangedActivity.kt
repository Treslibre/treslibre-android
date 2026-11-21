// Modified 2026 by Treslibre Project (Treslibre).
package ch.threema.app.observation

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import ch.threema.domain.observation.ObservedComponent
import ch.threema.domain.observation.ServerKeyChangeAlert
import ch.threema.domain.observation.ServerObservationEngine

/**
 * Blocking modal displayed when an unexpected server key or TLS SPKI change is detected (§4.3 Case C).
 * Displays Old vs New Fingerprint side-by-side (hex + visual identicon) with:
 * - Default action: "Abort Connection"
 * - Secondary action: "Accept New Server Key" (requires secondary confirmation dialog and records
 *   `unverified_manual_override`).
 */
class ServerKeyChangedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serverId = intent.getStringExtra(EXTRA_SERVER_ID) ?: "unknown"
        val componentWire = intent.getStringExtra(EXTRA_COMPONENT) ?: ObservedComponent.CHAT_SERVER.wireName
        val component = runCatching { ObservedComponent.fromWireName(componentWire) }
            .getOrDefault(ObservedComponent.CHAT_SERVER)
        val host = intent.getStringExtra(EXTRA_HOST) ?: serverId
        val oldFp = intent.getStringExtra(EXTRA_OLD_FP) ?: ""
        val newFp = intent.getStringExtra(EXTRA_NEW_FP) ?: ""
        val newLeaf = intent.getStringExtra(EXTRA_NEW_LEAF)

        val rootScroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val titleView = TextView(this).apply {
            text = "Warning: Server Key / Certificate Changed"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(211, 47, 47))
        }
        container.addView(titleView)

        val messageView = TextView(this).apply {
            text = "\nThe cryptographic identity for ${component.wireName} on $host (Server ID: $serverId) " +
                "does not match the pinned TOFU record and was not signed by the server administrator's Ed25519 key.\n\n" +
                "All network traffic to this endpoint has been halted to prevent a potential Man-in-the-Middle (MitM) attack.\n"
            textSize = 14f
        }
        container.addView(messageView)

        val comparisonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        comparisonRow.addView(
            buildFingerprintCard("Pinned (Old) Fingerprint", oldFp),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 16
            },
        )
        comparisonRow.addView(
            buildFingerprintCard("Observed (New) Fingerprint", newFp),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        container.addView(comparisonRow)

        val abortButton = Button(this).apply {
            text = "Abort Connection (Default)"
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener {
                finish()
            }
        }
        container.addView(
            abortButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 36 },
        )
        abortButton.requestFocus()

        val acceptButton = Button(this).apply {
            text = "Accept New Server Key…"
            setOnClickListener {
                AlertDialog.Builder(this@ServerKeyChangedActivity)
                    .setTitle("Confirm Unverified Server Key Override")
                    .setMessage(
                        "Accepting an unverified server key change will record an 'unverified_manual_override' " +
                            "in your local audit log. Only proceed if you have verified the new fingerprint:\n\n$newFp\n\n" +
                            "out-of-band with your server administrator.",
                    )
                    .setNegativeButton("Abort Connection") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setPositiveButton("Confirm Manual Override") { _, _ ->
                        val store = EncryptedServerObservationStore.getInstance(this@ServerKeyChangedActivity)
                        val record = store.load()
                        if (record != null) {
                            val updated = ServerObservationEngine.applyManualOverride(
                                record = record,
                                component = component,
                                newFingerprint = newFp,
                                newLeafSha256 = newLeaf,
                            )
                            store.save(updated)
                        }
                        finish()
                    }
                    .show()
            }
        }
        container.addView(
            acceptButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 16 },
        )

        rootScroll.addView(container)
        setContentView(rootScroll)
    }

    private fun buildFingerprintCard(label: String, fingerprint: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.argb(24, 128, 128, 128))

            addView(
                TextView(this@ServerKeyChangedActivity).apply {
                    text = label
                    textSize = 13f
                    setTypeface(typeface, Typeface.BOLD)
                },
            )

            addView(
                buildIdenticonGrid(fingerprint),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = 16
                    bottomMargin = 16
                },
            )

            addView(
                TextView(this@ServerKeyChangedActivity).apply {
                    text = fingerprint
                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                },
            )
        }

    private fun buildIdenticonGrid(fingerprint: String): GridLayout {
        val bytes = fingerprint.toByteArray()
        var hash = 0x811c9dc5.toInt()
        for (b in bytes) {
            hash = hash xor (b.toInt() and 0xFF)
            hash *= 0x01000193
        }
        val activeColor = Color.rgb(
            64 + (hash and 0x7F),
            64 + ((hash ushr 8) and 0x7F),
            64 + ((hash ushr 16) and 0x7F),
        )
        return GridLayout(this).apply {
            rowCount = 5
            columnCount = 5
            for (r in 0 until 5) {
                val rowBits = hash ushr (r * 3)
                val cols = booleanArrayOf(
                    (rowBits and 1) != 0,
                    (rowBits and 2) != 0,
                    (rowBits and 4) != 0,
                    (rowBits and 2) != 0,
                    (rowBits and 1) != 0,
                )
                for (c in 0 until 5) {
                    val cell = TextView(this@ServerKeyChangedActivity).apply {
                        width = 20
                        height = 20
                        setBackgroundColor(if (cols[c]) activeColor else Color.TRANSPARENT)
                    }
                    addView(cell)
                }
            }
        }
    }

    companion object {
        private const val EXTRA_SERVER_ID = "extra_server_id"
        private const val EXTRA_COMPONENT = "extra_component"
        private const val EXTRA_HOST = "extra_host"
        private const val EXTRA_OLD_FP = "extra_old_fp"
        private const val EXTRA_NEW_FP = "extra_new_fp"
        private const val EXTRA_NEW_LEAF = "extra_new_leaf"

        @JvmStatic
        fun createIntent(context: Context, alert: ServerKeyChangeAlert): Intent =
            Intent(context, ServerKeyChangedActivity::class.java).apply {
                putExtra(EXTRA_SERVER_ID, alert.serverId)
                putExtra(EXTRA_COMPONENT, alert.component.wireName)
                putExtra(EXTRA_HOST, alert.host)
                putExtra(EXTRA_OLD_FP, alert.oldFingerprint)
                putExtra(EXTRA_NEW_FP, alert.newFingerprint)
                putExtra(EXTRA_NEW_LEAF, alert.newLeafSha256)
            }
    }
}

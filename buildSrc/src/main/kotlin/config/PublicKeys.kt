// Modified 2026 by Treslibre Project (Treslibre).
package config

@Suppress("ktlint:standard:max-line-length", "ktlint:standard:property-wrapping")
object PublicKeys {
    val threemaPush = ByteArray(32)
    val prodServer = ByteArray(32)
    val prodServerAlt = ByteArray(32)
    val sandboxServer = ByteArray(32)

    val onPremTrusted = emptyArray<String>()
}

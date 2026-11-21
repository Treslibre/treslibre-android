// Modified 2026 by Treslibre Project (Treslibre).
package ch.threema.app

import ch.threema.app.multidevice.DesktopClientFlavor

object BuildFlavorConstants {
    const val DISPLAY_NAME = "Treslibre"
    const val IS_SANDBOX = false
    val licenseType = BuildFlavor.LicenseType.WORK
    val desktopClientFlavor = DesktopClientFlavor.OnPrem
}

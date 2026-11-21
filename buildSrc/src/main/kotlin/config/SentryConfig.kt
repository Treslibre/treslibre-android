// Modified 2026 by Treslibre Project (Treslibre).
package config

import com.android.build.api.dsl.VariantDimension
import utils.booleanBuildConfigField
import utils.intBuildConfigField
import utils.stringBuildConfigField

enum class SentryConfig(
    val projectId: Int,
    val publicApiKey: String,
    val host: String = "",
) {
    SANDBOX(
        projectId = 0,
        publicApiKey = "",
    ),
    PRODUCTION(
        projectId = 0,
        publicApiKey = "",
    ),
}

fun VariantDimension.setSentryConfig(config: SentryConfig?) {
    setSentryConfig(
        projectId = 0,
        publicApikey = "",
        host = "",
    )
}

private fun VariantDimension.setSentryConfig(
    projectId: Int,
    publicApikey: String,
    host: String,
) {
    intBuildConfigField("SENTRY_PROJECT_ID", 0)
    stringBuildConfigField("SENTRY_PUBLIC_API_KEY", "")
    stringBuildConfigField("SENTRY_HOST", "")
    booleanBuildConfigField("ERROR_REPORTING_SUPPORTED", false)
}

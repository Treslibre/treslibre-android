// Modified 2026 by Treslibre Project (Treslibre).
package ch.threema.app.license

class UsernameParameter private constructor(
    override val value: String,
    override val source: ParameterSource,
) : Parameter {
    companion object {
        @JvmStatic
        fun create(username: String, parameterSource: ParameterSource): UsernameParameter? {
            if (username.isEmpty()) {
                return null
            }

            return UsernameParameter(
                value = username,
                source = parameterSource,
            )
        }

        @JvmStatic
        fun createOrEmpty(username: String?, parameterSource: ParameterSource): UsernameParameter =
            UsernameParameter(
                value = username ?: "",
                source = parameterSource,
            )
    }
}

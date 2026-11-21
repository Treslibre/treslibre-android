// Modified 2026 by Treslibre Project (Treslibre).
package ch.threema.data.datatypes

import ch.threema.app.BuildFlavor
import ch.threema.domain.models.BasicContact
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.types.IdentityString

/**
 * A predefined contact can be added to the contact list and is automatically
 * initialized with its identity, nickname, hard-coded public key and the
 * verification level fully verified. Once a predefined contact is in the
 * contact list, it is treated like any other normal contact (with editable
 * properties like first and last name, etc.).
 *
 * A predefined contact may be marked special meaning it follows special logic.
 * These are also known as _Special Contacts_. Even though special contacts
 * should not normally appear in the contact list, there's nothing stopping a
 * user from adding a special contact to their contact list. While they are
 * treated like normal contacts in the contact list, depending on the special
 * handling logic, it may not be possible to send or receive normal messages
 * from them.
 */
@OptIn(ExperimentalStdlibApi::class) // For hexToByteArray
data class PredefinedContact(
    /** Threema ID of the predefined contact. */
    val identity: IdentityString,

    /** Whether the predefined contact is marked as a _special contact_. */
    val isSpecial: Boolean,

    /** Public key of the predefined contact. */
    val publicKey: ByteArray,

    /** Nickname of the contact (without `~` prefix). */
    val nickname: String,
) {

    /**
     * Convert the predefined contact to a basic contact. Note that the verification level is set to "full" and the feature mask to 0. With this
     * conversion, the nickname is lost.
     */
    fun toBasicContact(): BasicContact = BasicContact(
        identity = identity,
        publicKey = publicKey,
        featureMask = 0u,
        identityState = IdentityState.ACTIVE,
        identityType = IdentityType.REGULAR,
        verificationLevel = VerificationLevel.FULLY_VERIFIED,
        workVerificationLevel = WorkVerificationLevel.NONE,
    )

    companion object {
        const val THREEMA_CHANNEL_IDENTITY: IdentityString = "*THREEMA"
        const val THREEMA_SUPPORT_IDENTITY: IdentityString = "*SUPPORT"
        const val SPECIAL_CONTACT_3MAPUSH_IDENTITY: IdentityString = "*3MAPUSH"
        const val SPECIAL_CONTACT_3MAW0RK_IDENTITY: IdentityString = "*3MAW0RK"

        /** Map containing all predefined contacts for current build environment. */
        private val all: Map<IdentityString, PredefinedContact> by lazy { getAllForCurrentEnvironment() }

        /**
         * The support contact with identity [THREEMA_SUPPORT_IDENTITY]. Note that on onprem builds this contact is null.
         */
        val supportContact: PredefinedContact? by lazy { all[THREEMA_SUPPORT_IDENTITY] }

        /**
         * The threema channel contact with identity [THREEMA_CHANNEL_IDENTITY]. Note that on onprem build this contact is null.
         */
        val threemaChannelContact: PredefinedContact? by lazy { all[THREEMA_CHANNEL_IDENTITY] }

        /**
         * Return whether the specified identity is a predefined contact.
         */
        @JvmStatic
        fun isPredefinedContact(identity: IdentityString): Boolean = identity in all

        /**
         * Return the predefined contact with [identity] if it is a predefined contact.
         */
        @JvmStatic
        fun getPredefinedContact(identity: IdentityString): PredefinedContact? =
            all[identity]

        /**
         * Return whether the specified identity is a special contact.
         */
        @JvmStatic
        fun isSpecialContact(identity: IdentityString): Boolean =
            all[identity]?.isSpecial == true

        /**
         * Return the special contact with [identity] if it is a special contact.
         */
        @JvmStatic
        fun getSpecialContact(identity: IdentityString): PredefinedContact? =
            all[identity]
                .takeIf { predefinedContact -> predefinedContact?.isSpecial == true }

        @JvmStatic
        fun getAllPredefinedContacts(): Collection<PredefinedContact> =
            all.values

        /**
         * Return all predefined contacts for current build environment.
         */
        private fun getAllForCurrentEnvironment(): Map<IdentityString, PredefinedContact> =
            emptyMap()

        private fun buildProduction(): Map<IdentityString, PredefinedContact> = emptyMap()

        private fun buildSandbox(): Map<IdentityString, PredefinedContact> = emptyMap()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PredefinedContact

        if (isSpecial != other.isSpecial) return false
        if (identity != other.identity) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (nickname != other.nickname) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isSpecial.hashCode()
        result = 31 * result + identity.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + nickname.hashCode()
        return result
    }
}

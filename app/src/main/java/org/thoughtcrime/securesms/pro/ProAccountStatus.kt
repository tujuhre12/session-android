package org.thoughtcrime.securesms.pro

sealed interface ProAccountStatus{
    data object None: ProAccountStatus

    sealed interface Pro: ProAccountStatus{
        val showProBadge: Boolean
        val infoLabel: CharSequence

        data class AutoRenewing(
            override val showProBadge: Boolean,
            override val infoLabel: CharSequence
        ): Pro

        data class Expiring(
            override val showProBadge: Boolean,
            override val infoLabel: CharSequence
        ): Pro
    }

    data object Expired: ProAccountStatus
}
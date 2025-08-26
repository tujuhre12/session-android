package org.thoughtcrime.securesms.pro

import org.thoughtcrime.securesms.pro.subscription.ProSubscriptionDuration
import java.time.Instant

sealed interface ProAccountStatus{
    data object None: ProAccountStatus

    sealed interface Pro: ProAccountStatus{
        val showProBadge: Boolean
        val type: ProSubscriptionDuration
        /**
         * The validity of the Pro status, if null, it means the Pro status is permanent.
         */
        val validUntil: Instant?

        //todo PRO we need a way to know which store the subscription is from

        data class AutoRenewing(
            override val showProBadge: Boolean,
            override val validUntil: Instant?,
            override val type: ProSubscriptionDuration
        ): Pro

        data class Expiring(
            override val showProBadge: Boolean,
            override val validUntil: Instant?,
            override val type: ProSubscriptionDuration
        ): Pro
    }

    data object Expired: ProAccountStatus
}
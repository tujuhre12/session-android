package org.thoughtcrime.securesms.notifications

import kotlinx.coroutines.Job

interface TokenFetcher {
    fun fetch(): Job
}

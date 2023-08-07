package org.thoughtcrime.securesms.notifications

import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoOpTokenFetcher @Inject constructor() : TokenFetcher {
    override fun fetch(): Job = MainScope().launch { }
}

package org.thoughtcrime.securesms.home.startconversation.community

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.thoughtcrime.securesms.groups.EnterCommunityUrlDelegate
import org.thoughtcrime.securesms.groups.EnterCommunityUrlFragment

class JoinCommunityFragmentAdapter(
    private val parentFragment: Fragment,
    private val enterCommunityUrlDelegate: EnterCommunityUrlDelegate,
) : FragmentStateAdapter(parentFragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> EnterCommunityUrlFragment().apply { delegate = enterCommunityUrlDelegate }
            1 -> EnterCommunityUrlFragment().apply { delegate = enterCommunityUrlDelegate }
            else -> throw IllegalStateException()
        }
    }
}
package org.thoughtcrime.securesms.conversation.start

interface NewConversationDelegate {
    fun onNewMessageSelected()
    fun onCreateGroupSelected()
    fun onJoinCommunitySelected()
    fun onContactSelected(address: String)
    fun onDialogBackPressed()
    fun onDialogClosePressed()
    fun onInviteFriend()
}

object NullNewConversationDelegate: NewConversationDelegate {
    override fun onNewMessageSelected() {}
    override fun onCreateGroupSelected() {}
    override fun onJoinCommunitySelected() {}
    override fun onContactSelected(address: String) {}
    override fun onDialogBackPressed() {}
    override fun onDialogClosePressed() {}
    override fun onInviteFriend() {}
}
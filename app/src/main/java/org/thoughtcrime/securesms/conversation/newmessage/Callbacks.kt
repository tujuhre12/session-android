package org.thoughtcrime.securesms.conversation.newmessage

interface Callbacks {
    fun onChange(value: String) {}
    fun onContinue() {}
    fun onScanQrCode(value: String) {}
}

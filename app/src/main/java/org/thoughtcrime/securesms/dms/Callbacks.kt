package org.thoughtcrime.securesms.dms

interface Callbacks {
    fun onChange(value: String) {}
    fun onContinue() {}
    fun onScan(value: String) {}
}

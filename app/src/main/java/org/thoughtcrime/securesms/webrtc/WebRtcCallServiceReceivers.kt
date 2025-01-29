package org.thoughtcrime.securesms.webrtc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.service.WebRtcCallBridge
import org.thoughtcrime.securesms.webrtc.locks.LockManager
import javax.inject.Inject


class PowerButtonReceiver(val sendCommand: (Intent)->Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_SCREEN_OFF == intent.action) {
            val serviceIntent = Intent(context,WebRtcCallBridge::class.java)
                    .setAction(WebRtcCallBridge.ACTION_SCREEN_OFF)
            sendCommand(serviceIntent)
        }
    }
}

class ProximityLockRelease(private val lockManager: LockManager): Thread.UncaughtExceptionHandler {
    companion object {
        private val TAG = Log.tag(ProximityLockRelease::class.java)
    }
    override fun uncaughtException(t: Thread, e: Throwable) {
        Log.e(TAG,"Uncaught exception - releasing proximity lock", e)
        lockManager.updatePhoneState(LockManager.PhoneState.IDLE)
    }
}

class WiredHeadsetStateReceiver(val sendCommand: (Intent)->Unit): BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getIntExtra("state", -1)
        val serviceIntent = Intent(context, WebRtcCallBridge::class.java)
                .setAction(WebRtcCallBridge.ACTION_WIRED_HEADSET_CHANGE)
                .putExtra(WebRtcCallBridge.EXTRA_AVAILABLE, state != 0)

        sendCommand(serviceIntent)
    }
}


@AndroidEntryPoint
class EndCallReceiver(): BroadcastReceiver() {
    @Inject
    lateinit var webRtcCallBridge: WebRtcCallBridge

    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = when(intent.action) {
            WebRtcCallBridge.ACTION_LOCAL_HANGUP -> {
                WebRtcCallBridge.hangupIntent(context)
            }

            else -> WebRtcCallBridge.denyCallIntent(context)
        }

        webRtcCallBridge.sendCommand(serviceIntent)
    }
}
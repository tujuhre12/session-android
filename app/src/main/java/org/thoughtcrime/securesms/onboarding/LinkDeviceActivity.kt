package org.thoughtcrime.securesms.onboarding

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityLinkDeviceBinding
import network.loki.messenger.databinding.FragmentRecoveryPhraseBinding
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.crypto.MnemonicCodec
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.crypto.MnemonicUtilities
import org.thoughtcrime.securesms.util.ScanQRCodeWrapperFragment
import org.thoughtcrime.securesms.util.ScanQRCodeWrapperFragmentDelegate
import org.thoughtcrime.securesms.util.setUpActionBarSessionLogo

@AndroidEntryPoint
class LinkDeviceActivity : BaseActionBarActivity(), ScanQRCodeWrapperFragmentDelegate {

    private lateinit var binding: ActivityLinkDeviceBinding

    private val adapter = LinkDeviceActivityAdapter(this)

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpActionBarSessionLogo()
        TextSecurePreferences.apply {
            setHasViewedSeed(this@LinkDeviceActivity, true)
            setConfigurationMessageSynced(this@LinkDeviceActivity, false)
            setRestorationTime(this@LinkDeviceActivity, System.currentTimeMillis())
            setLastProfileUpdateTime(this@LinkDeviceActivity, 0)
        }
        binding = ActivityLinkDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.viewPager.adapter = adapter
        binding.tabLayout.setupWithViewPager(binding.viewPager)
    }
    // endregion

    // region Interaction
    override fun handleQRCodeScanned(mnemonic: String) {
        try {
            val seed = Hex.fromStringCondensed(mnemonic)
            continueWithSeed(seed)
        } catch (e: Exception) {
            Log.e("Loki","Error getting seed from QR code", e)
            Toast.makeText(this, "An error occurred.", Toast.LENGTH_LONG).show()
        }
    }

    fun continueWithMnemonic(mnemonic: String) {
        val loadFileContents: (String) -> String = { fileName ->
            MnemonicUtilities.loadFileContents(this, fileName)
        }
        try {
            val hexEncodedSeed = MnemonicCodec(loadFileContents).decode(mnemonic)
            val seed = Hex.fromStringCondensed(hexEncodedSeed)
            continueWithSeed(seed)
        } catch (error: Exception) {
            val message = if (error is MnemonicCodec.DecodingError) {
                error.description
            } else {
                "An error occurred."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun continueWithSeed(seed: ByteArray) {
        startLoadingActivity(seed)
    }
    // endregion
}

// region Adapter
private class LinkDeviceActivityAdapter(private val activity: LinkDeviceActivity) : FragmentPagerAdapter(activity.supportFragmentManager) {
    val recoveryPhraseFragment = RecoveryPhraseFragment()

    override fun getCount(): Int {
        return 2
    }

    override fun getItem(index: Int): Fragment {
        return when (index) {
            0 -> recoveryPhraseFragment
            1 -> {
                val result = ScanQRCodeWrapperFragment()
                result.delegate = activity
                result.message = activity.getString(R.string.activity_link_device_qr_message)
                result
            }
            else -> throw IllegalStateException()
        }
    }

    override fun getPageTitle(index: Int): CharSequence {
        return when (index) {
            0 -> activity.getString(R.string.activity_link_device_recovery_phrase)
            1 -> activity.getString(R.string.activity_link_device_scan_qr_code)
            else -> throw IllegalStateException()
        }
    }
}
// endregion

// region Recovery Phrase Fragment
class RecoveryPhraseFragment : Fragment() {
    private lateinit var binding: FragmentRecoveryPhraseBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentRecoveryPhraseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            mnemonicEditText.imeOptions = EditorInfo.IME_ACTION_DONE or 16777216 // Always use incognito keyboard
            mnemonicEditText.setRawInputType(InputType.TYPE_CLASS_TEXT)
            mnemonicEditText.setOnEditorActionListener { v, actionID, _ ->
                if (actionID == EditorInfo.IME_ACTION_DONE) {
                    val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                    handleContinueButtonTapped()
                    true
                } else {
                    false
                }
            }
            continueButton.setOnClickListener { handleContinueButtonTapped() }
        }
    }

    private fun handleContinueButtonTapped() {
        val mnemonic = binding.mnemonicEditText.text?.trim().toString()
        (requireActivity() as LinkDeviceActivity).continueWithMnemonic(mnemonic)
    }
}
// endregion

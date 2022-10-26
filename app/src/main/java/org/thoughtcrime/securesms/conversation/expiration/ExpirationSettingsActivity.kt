package org.thoughtcrime.securesms.conversation.expiration

import android.os.Bundle
import android.os.Parcelable
import android.util.SparseArray
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityExpirationSettingsBinding
import org.session.libsignal.protos.SignalServiceProtos.Content.ExpirationType
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.preferences.RadioOption
import org.thoughtcrime.securesms.preferences.RadioOptionAdapter

@AndroidEntryPoint
class ExpirationSettingsActivity: PassphraseRequiredActionBarActivity() {

    private lateinit var binding : ActivityExpirationSettingsBinding

    private val expirationType: ExpirationType? by lazy {
        ExpirationType.valueOf(intent.getIntExtra(EXTRA_EXPIRATION_TYPE, -1))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val scrollParcelArray = SparseArray<Parcelable>()
        binding.scrollView.saveHierarchyState(scrollParcelArray)
        outState.putSparseParcelableArray(SCROLL_PARCEL, scrollParcelArray)
    }

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)
        binding = ActivityExpirationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setUpToolbar()

        savedInstanceState?.let { bundle ->
            val scrollStateParcel = bundle.getSparseParcelableArray<Parcelable>(SCROLL_PARCEL)
            if (scrollStateParcel != null) {
                binding.scrollView.restoreHierarchyState(scrollStateParcel)
            }
        }

        val options = if (expirationType == ExpirationType.DELETE_AFTER_SEND) {
            val values = resources.getIntArray(R.array.send_expiration_time_values).map(Int::toString)
            val names = resources.getStringArray(R.array.send_expiration_time_names)
            values.zip(names) { value, name -> RadioOption(value, name)}
        } else {
            listOf(
                RadioOption("off", getString(R.string.expiration_off)),
                RadioOption("read", getString(R.string.expiration_type_disappear_after_read)),
                RadioOption("send", getString(R.string.expiration_type_disappear_after_send))
            )
        }
        val optionAdapter = RadioOptionAdapter {

        }
        binding.textViewDeleteType.isVisible = expirationType == null
        binding.textViewTimer.isVisible = expirationType == null
        binding.layoutTimer.isVisible = expirationType == null
        binding.recyclerView.apply {
            adapter = optionAdapter
            addItemDecoration(ContextCompat.getDrawable(this@ExpirationSettingsActivity, R.drawable.conversation_menu_divider)!!.let {
                DividerItemDecoration(this@ExpirationSettingsActivity, RecyclerView.VERTICAL).apply {
                    setDrawable(it)
                }
            })
            setHasFixedSize(true)
        }
        optionAdapter.submitList(options)

    }

    private fun setUpToolbar() {
        setSupportActionBar(binding.toolbar)
        val actionBar = supportActionBar ?: return
        actionBar.title = getString(R.string.activity_expiration_settings_title)
        actionBar.subtitle = if (expirationType == ExpirationType.DELETE_AFTER_SEND) {
            getString(R.string.activity_expiration_settings_subtitle_sent)
        } else {
            getString(R.string.activity_expiration_settings_subtitle)
        }
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setHomeButtonEnabled(true)
    }
    companion object {
        private const val SCROLL_PARCEL = "scroll_parcel"
        const val EXTRA_EXPIRATION_TYPE = "expiration_type"
        const val EXTRA_READ_ONLY = "read_only"
    }

}
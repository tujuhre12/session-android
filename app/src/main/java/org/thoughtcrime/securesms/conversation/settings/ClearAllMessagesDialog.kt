package org.thoughtcrime.securesms.conversation.settings

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import network.loki.messenger.databinding.DialogClearAllMessagesBinding
import org.thoughtcrime.securesms.conversation.v2.utilities.BaseDialog

class ClearAllMessagesDialog(private val isUserAdmin: Boolean, private val callback: (Option) -> Unit): BaseDialog(), View.OnClickListener {

    enum class Option {
        FOR_ME,
        FOR_EVERYONE
    }

    private lateinit var binding: DialogClearAllMessagesBinding

    override fun setContentView(builder: AlertDialog.Builder) {
        super.setContentView(builder)
        binding = DialogClearAllMessagesBinding.inflate(LayoutInflater.from(requireContext()))
        with (binding) {
            forEveryone.isVisible = isUserAdmin
            forEveryone.setOnClickListener(this@ClearAllMessagesDialog)
            forMe.isVisible = isUserAdmin
            forMe.setOnClickListener(this@ClearAllMessagesDialog)
            close.isVisible = isUserAdmin
            close.setOnClickListener(this@ClearAllMessagesDialog)

            cancel.isVisible = !isUserAdmin
            cancel.setOnClickListener(this@ClearAllMessagesDialog)
            clear.isVisible = !isUserAdmin
            clear.setOnClickListener(this@ClearAllMessagesDialog)
        }
        builder.setView(binding.root)
    }

    override fun onClick(v: View) {
        when {
            v === binding.cancel ||
            v === binding.close -> dismiss()
            v === binding.forMe ||
            v === binding.clear -> {
                callback(Option.FOR_ME)
                dismiss()
            }
            v === binding.forEveryone -> {
                callback(Option.FOR_EVERYONE)
                dismiss()
            }
        }
    }
}
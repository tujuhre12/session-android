package org.thoughtcrime.securesms.conversation.settings

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import network.loki.messenger.databinding.DialogClearAllMediaBinding
import org.thoughtcrime.securesms.conversation.v2.utilities.BaseDialog

class ClearAllMediaDialog(private val callback: ()->Unit): BaseDialog(), View.OnClickListener {

    private lateinit var binding: DialogClearAllMediaBinding

    override fun setContentView(builder: AlertDialog.Builder) {
        super.setContentView(builder)
        binding = DialogClearAllMediaBinding.inflate(LayoutInflater.from(requireContext()))
        with (binding) {
            clear.setOnClickListener(this@ClearAllMediaDialog)
            cancel.setOnClickListener(this@ClearAllMediaDialog)
        }
        builder.setView(binding.root)
    }

    override fun onClick(v: View) {
        when {
            v === binding.cancel -> dismiss()
            v === binding.clear -> {
                callback()
                dismiss()
            }
        }
    }

}
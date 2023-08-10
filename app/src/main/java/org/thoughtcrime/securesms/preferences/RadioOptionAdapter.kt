package org.thoughtcrime.securesms.preferences

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import network.loki.messenger.R
import network.loki.messenger.databinding.ItemSelectableBinding
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.thoughtcrime.securesms.mms.GlideApp
import java.util.Objects

class RadioOptionAdapter<T>(
    private var selectedOptionPosition: Int = 0,
    private val onClickListener: (RadioOption<T>) -> Unit
) : ListAdapter<RadioOption<T>, RadioOptionAdapter.ViewHolder<T>>(RadioOptionDiffer()) {

    class RadioOptionDiffer<T>: DiffUtil.ItemCallback<RadioOption<T>>() {
        override fun areItemsTheSame(oldItem: RadioOption<T>, newItem: RadioOption<T>) = oldItem.title == newItem.title
        override fun areContentsTheSame(oldItem: RadioOption<T>, newItem: RadioOption<T>) = Objects.equals(oldItem.value,newItem.value)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<T> {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_selectable, parent, false)
        return ViewHolder<T>(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder<T>, position: Int) {
        val option = getItem(position)
        val isSelected = position == selectedOptionPosition
        holder.bind(option, isSelected) {
            onClickListener(it)
            selectedOptionPosition = position
            notifyItemRangeChanged(0, itemCount)
        }
    }

    fun setSelectedPosition(selectedPosition: Int) {
        selectedOptionPosition = selectedPosition
        notifyDataSetChanged()
    }

    class ViewHolder<T>(itemView: View): RecyclerView.ViewHolder(itemView) {

        val glide = GlideApp.with(itemView)
        val binding = ItemSelectableBinding.bind(itemView)

        fun bind(option: RadioOption<T>, isSelected: Boolean, toggleSelection: (RadioOption<T>) -> Unit) {
            val alpha = if (option.enabled) 1f else 0.5f
            binding.root.isEnabled = option.enabled
            binding.root.contentDescription = option.contentDescription
            binding.titleTextView.alpha = alpha
            binding.subtitleTextView.alpha = alpha
            binding.selectButton.alpha = alpha

            binding.titleTextView.text = option.title
            binding.subtitleTextView.text = option.subtitle
            binding.subtitleTextView.isVisible = !option.subtitle.isNullOrEmpty()
            binding.selectButton.isSelected = isSelected
            if (option.enabled) {
                binding.root.setOnClickListener { toggleSelection(option) }
            }
        }
    }

}

sealed class RadioOption<T>(
    val value: T,
    val title: String,
    val subtitle: String? = null,
    val enabled: Boolean = true,
    val contentDescription: String = ""
)

class StringRadioOption(value: String,
                        title: String,
                        subtitle: String? = null,
                        enabled: Boolean = true,
                        contentDescription: String = ""): RadioOption<String>(
    value,
    title,
    subtitle,
    enabled,
    contentDescription
)

class ExpirationRadioOption(
    value: ExpiryMode,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    contentDescription: String = ""
): RadioOption<ExpiryMode>(
    value,
    title,
    subtitle,
    enabled,
    contentDescription
) {
    fun copy(value: ExpiryMode = this.value,
             title: String = this.title,
             subtitle: String? = this.subtitle,
             enabled: Boolean = this.enabled,
             contentDescription: String = this.contentDescription) =
        ExpirationRadioOption(value, title, subtitle, enabled, contentDescription)
}

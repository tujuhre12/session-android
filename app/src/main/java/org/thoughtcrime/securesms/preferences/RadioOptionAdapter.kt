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
import org.thoughtcrime.securesms.mms.GlideApp

class RadioOptionAdapter(
    private var selectedOptionPosition: Int = 0,
    private val onClickListener: (RadioOption) -> Unit
) : ListAdapter<RadioOption, RadioOptionAdapter.ViewHolder>(RadioOptionDiffer()) {

    class RadioOptionDiffer: DiffUtil.ItemCallback<RadioOption>() {
        override fun areItemsTheSame(oldItem: RadioOption, newItem: RadioOption) = oldItem === newItem
        override fun areContentsTheSame(oldItem: RadioOption, newItem: RadioOption) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_selectable, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val option = getItem(position)
        val isSelected = position == selectedOptionPosition
        holder.bind(option, isSelected) {
            onClickListener(it)
            selectedOptionPosition = position
            notifyDataSetChanged()
        }
    }

    fun setSelectedPosition(selectedPosition: Int) {
        selectedOptionPosition = selectedPosition
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {

        val glide = GlideApp.with(itemView)
        val binding = ItemSelectableBinding.bind(itemView)

        fun bind(option: RadioOption, isSelected: Boolean, toggleSelection: (RadioOption) -> Unit) {
            val alpha = if (option.enabled) 1f else 0.5f
            binding.root.isEnabled = option.enabled
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

data class RadioOption(
    val value: String,
    val title: String,
    val subtitle: String? = null,
    val enabled: Boolean = true
)

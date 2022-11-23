package org.thoughtcrime.securesms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import network.loki.messenger.R
import network.loki.messenger.databinding.MediaOverviewGalleryItemBinding
import org.thoughtcrime.securesms.database.MediaDatabase.MediaRecord

class MediaGalleryAdapter(private val itemClickListener: ItemClickListener): RecyclerView.Adapter<MediaGalleryAdapter.ViewHolder>() {

    private val items: MutableList<MediaRecord> = mutableListOf()
    private val selectedItems: MutableList<MediaRecord> = mutableListOf()

    fun setItems(newItems: List<MediaRecord>) {
        items.clear()
        items += newItems
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.media_overview_gallery_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, selectedItems.contains(item), itemClickListener)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {

        private val binding = MediaOverviewGalleryItemBinding.bind(itemView)

        fun bind(item: MediaRecord, isSelected: Boolean, itemClickListener: ItemClickListener) {
            binding.image.setOnClickListener { itemClickListener.onMediaClicked(item) }
            binding.image.setOnLongClickListener {
                itemClickListener.onMediaLongClicked(item)
                true
            }
            binding.selectedIndicator.isVisible = isSelected
        }
    }

    interface ItemClickListener {
        fun onMediaClicked(mediaRecord: MediaRecord)
        fun onMediaLongClicked(mediaRecord: MediaRecord?)
    }

}
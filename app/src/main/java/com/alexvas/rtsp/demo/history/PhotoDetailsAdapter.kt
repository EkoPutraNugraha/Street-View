package com.alexvas.rtsp.demo.history

import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.alexvas.rtsp.demo.data.SnapshotEntity
import com.alexvas.rtsp.demo.databinding.ItemPhotoDetailsBinding
import java.io.File

class PhotoDetailsAdapter : ListAdapter<SnapshotEntity, PhotoDetailsAdapter.PhotoDetailsViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoDetailsViewHolder {
        val binding = ItemPhotoDetailsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoDetailsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoDetailsViewHolder, position: Int) {
        val snapshot = getItem(position)
        Log.d("PhotoDetailsAdapter", "Binding photo at position $position: ${snapshot.path}")
        holder.bind(snapshot)
    }

    inner class PhotoDetailsViewHolder(private val binding: ItemPhotoDetailsBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(snapshot: SnapshotEntity) {
            binding.imageView.setImageURI(Uri.fromFile(File(snapshot.path)))
            binding.dateTime.text = snapshot.dateTime
            binding.coordinates.text = snapshot.coordinates
            binding.address.text = snapshot.address
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<SnapshotEntity>() {
            override fun areItemsTheSame(oldItem: SnapshotEntity, newItem: SnapshotEntity) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: SnapshotEntity, newItem: SnapshotEntity) = oldItem == newItem
        }
    }
}

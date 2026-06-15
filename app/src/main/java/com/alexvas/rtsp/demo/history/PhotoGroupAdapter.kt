package com.alexvas.rtsp.demo.history

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.alexvas.rtsp.demo.data.AppDatabase
import com.alexvas.rtsp.demo.data.PhotoGroup
import com.alexvas.rtsp.demo.databinding.ItemPhotoGroupBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoGroupAdapter(private val onClick: (PhotoGroup) -> Unit) : ListAdapter<PhotoGroup, PhotoGroupAdapter.PhotoGroupViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoGroupViewHolder {
        val binding = ItemPhotoGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoGroupViewHolder(binding, onClick, this)
    }

    override fun onBindViewHolder(holder: PhotoGroupViewHolder, position: Int) {
        val group = getItem(position)
        holder.bind(group)
    }

    class PhotoGroupViewHolder(
        private val binding: ItemPhotoGroupBinding,
        private val onClick: (PhotoGroup) -> Unit,
        private val adapter: PhotoGroupAdapter
    ) : RecyclerView.ViewHolder(binding.root) {
        private var currentGroup: PhotoGroup? = null

        init {
            itemView.setOnClickListener {
                currentGroup?.let {
                    onClick(it)
                }
            }

            binding.btnDeleteGroup.setOnClickListener {
                currentGroup?.let { group ->
                    deleteGroup(group)
                }
            }
        }

        fun bind(group: PhotoGroup) {
            currentGroup = group
            binding.groupName.text = group.groupName
            binding.dateTime.text = group.dateTime
        }

        private fun deleteGroup(group: PhotoGroup) {
            CoroutineScope(Dispatchers.IO).launch {
                val database = AppDatabase.getDatabase(binding.root.context)
                database.snapshotDao().deleteGroup(group)
                withContext(Dispatchers.Main) {
                    Toast.makeText(binding.root.context, "Group deleted", Toast.LENGTH_SHORT).show()
                    val currentList = adapter.currentList.toMutableList()
                    currentList.remove(group)
                    adapter.submitList(currentList)
                }
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<PhotoGroup>() {
            override fun areItemsTheSame(oldItem: PhotoGroup, newItem: PhotoGroup) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: PhotoGroup, newItem: PhotoGroup) = oldItem == newItem
        }
    }
}

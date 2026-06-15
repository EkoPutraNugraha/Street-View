package com.alexvas.rtsp.demo.history

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.alexvas.rtsp.demo.R
import com.alexvas.rtsp.demo.data.AppDatabase
import com.alexvas.rtsp.demo.data.PhotoGroup
import com.alexvas.rtsp.demo.databinding.FragmentHistoryBinding

class HistoryFragment : Fragment() {

    private lateinit var binding: FragmentHistoryBinding
    private lateinit var adapter: PhotoGroupAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHistoryBinding.inflate(inflater, container, false)

        setupRecyclerView()
        loadPhotoGroups()

        return binding.root
    }

    private fun setupRecyclerView() {
        adapter = PhotoGroupAdapter { group -> onGroupClicked(group) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun loadPhotoGroups() {
        val database = AppDatabase.getDatabase(requireContext())
        val snapshotDao = database.snapshotDao()

        snapshotDao.getAllGroups().observe(viewLifecycleOwner, Observer { groups ->
            if (groups != null && groups.isNotEmpty()) {
                Log.d("HistoryFragment", "Groups loaded: ${groups.size}")
                adapter.submitList(groups)
            } else {
                Log.d("HistoryFragment", "No groups found")
            }
        })
    }

    private fun onGroupClicked(group: PhotoGroup) {
        val fragment = PhotoGroupDetailsFragment.newInstance(group.id, group.groupName)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }
}

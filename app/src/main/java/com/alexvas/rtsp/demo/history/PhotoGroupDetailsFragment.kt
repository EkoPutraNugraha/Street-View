package com.alexvas.rtsp.demo.history

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.alexvas.rtsp.demo.data.AppDatabase
import com.alexvas.rtsp.demo.data.SnapshotEntity
import com.alexvas.rtsp.demo.databinding.FragmentPhotoGroupDetailsBinding
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions

class PhotoGroupDetailsFragment : Fragment() {

    private lateinit var binding: FragmentPhotoGroupDetailsBinding
    private lateinit var adapter: PhotoDetailsAdapter
    private var groupId: Long = 0
    private var groupName: String = ""
    private lateinit var mapView: MapView
    private lateinit var pointAnnotationManager: PointAnnotationManager

    companion object {
        private const val ARG_GROUP_ID = "group_id"
        private const val ARG_GROUP_NAME = "group_name"
        private const val TAG = "PhotoGroupDetailsFragment"

        fun newInstance(groupId: Long, groupName: String) = PhotoGroupDetailsFragment().apply {
            arguments = Bundle().apply {
                putLong(ARG_GROUP_ID, groupId)
                putString(ARG_GROUP_NAME, groupName)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPhotoGroupDetailsBinding.inflate(inflater, container, false)
        groupId = arguments?.getLong(ARG_GROUP_ID) ?: 0
        groupName = arguments?.getString(ARG_GROUP_NAME) ?: ""

        setupRecyclerView()
        mapView = binding.mapView
        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) { style -> // Use MAPBOX_STREETS for a white map background
            Log.d(TAG, "Map style loaded")
            pointAnnotationManager = mapView.annotations.createPointAnnotationManager()
            loadPhotoDetails()
        }

        return binding.root
    }


    private fun setupRecyclerView() {
        adapter = PhotoDetailsAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun loadPhotoDetails() {
        val database = AppDatabase.getDatabase(requireContext())
        val snapshotDao = database.snapshotDao()

        Log.d(TAG, "Loading photo details for groupId: $groupId, groupName: $groupName")
        snapshotDao.getSnapshotsByGroup(groupId).observe(viewLifecycleOwner, Observer { photos ->
            if (photos != null && photos.isNotEmpty()) {
                Log.d(TAG, "Photos found: ${photos.size}")
                adapter.submitList(photos)
                updateMap(photos)
            } else {
                Log.d(TAG, "No photos found for groupId: $groupId")
            }
        })
    }

    private fun updateMap(photos: List<SnapshotEntity>) {
        if (::pointAnnotationManager.isInitialized) {
            pointAnnotationManager.deleteAll()
            val points = mutableListOf<Point>()
            photos.forEach { photo ->
                val coordinates = photo.coordinates.split(",")
                Log.d(TAG, "Raw coordinates: ${photo.coordinates}")
                if (coordinates.size == 2) {
                    try {
                        val latitude = coordinates[0].replace("Latitude: ", "").trim().toDouble()
                        val longitude = coordinates[1].replace("Longitude: ", "").trim().toDouble()
                        val point = Point.fromLngLat(longitude, latitude)
                        points.add(point)
                        Log.d(TAG, "Point added: ${point.latitude()}, ${point.longitude()}")
                        val pointAnnotationOptions = PointAnnotationOptions()
                            .withPoint(point)
                        pointAnnotationManager.create(pointAnnotationOptions)
                    } catch (e: NumberFormatException) {
                        Log.e(TAG, "Invalid coordinate format: ${photo.coordinates}", e)
                    }
                }
            }
            if (points.isNotEmpty()) {
                val lineString = LineString.fromLngLats(points)
                Log.d(TAG, "LineString created: ${lineString.coordinates()}")
                mapView.getMapboxMap().getStyle { style ->
                    style.removeStyleLayer("line-layer")
                    style.removeStyleSource("line-source")
                    Log.d(TAG, "Removed existing line-layer and line-source")

                    val geoJsonSource = geoJsonSource("line-source") {
                        geometry(lineString)
                    }
                    style.addSource(geoJsonSource)
                    Log.d(TAG, "Added geoJsonSource with lineString")

                    val lineLayer = lineLayer("line-layer", "line-source") {
                        lineColor("#FF0000")
                        lineWidth(5.0)
                    }
                    style.addLayer(lineLayer)
                    Log.d(TAG, "Added lineLayer with line-source")
                }
                val firstPoint = points.first()
                mapView.getMapboxMap().setCamera(com.mapbox.maps.CameraOptions.Builder().center(firstPoint).zoom(14.0).build())
                Log.d(TAG, "Camera set to first point: ${firstPoint.latitude()}, ${firstPoint.longitude()}")
            } else {
                Log.d(TAG, "No valid points found to create LineString")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}

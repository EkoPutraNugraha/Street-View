package com.alexvas.rtsp.demo.local

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Camera
import android.location.Geocoder
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.alexvas.rtsp.demo.camera.CameraMenuActivity
import com.alexvas.rtsp.demo.data.AppDatabase
import com.alexvas.rtsp.demo.data.PhotoGroup
import com.alexvas.rtsp.demo.data.SnapshotEntity
import com.alexvas.rtsp.demo.databinding.FragmentLocalCameraBinding
import com.azhar.captureimage.utils.GPSTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class LocalCameraFragment : Fragment(), SurfaceHolder.Callback {

    private lateinit var binding: FragmentLocalCameraBinding
    private lateinit var gpsTracker: GPSTracker
    private lateinit var database: AppDatabase
    private var currentPhotoPath: String? = null
    private var camera: Camera? = null
    private lateinit var surfaceHolder: SurfaceHolder
    private var isCapturing = false
    private var imageCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private var currentGroup: PhotoGroup? = null

    private var lastLatitude = 0.0
    private var lastLongitude = 0.0
    private val captureDistance = 20.0

    companion object {
        private const val TAG = "LocalCameraFragment"
        private const val REQUEST_IMAGE_CAPTURE = 1
        private const val REQUEST_CODE_STORAGE_PERMISSION = 2001
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentLocalCameraBinding.inflate(inflater, container, false)
        database = AppDatabase.getDatabase(requireContext())
        gpsTracker = GPSTracker(requireContext())

        surfaceHolder = binding.cameraPreview.holder
        surfaceHolder.addCallback(this)

        binding.btnCapturePhoto.setOnClickListener {
            startCapturing()
        }

        binding.btnStopPhoto.setOnClickListener {
            stopCapturing()
        }

        binding.btnBack.setOnClickListener {
            val intent = Intent(activity, CameraMenuActivity::class.java)
            startActivity(intent)
        }

        return binding.root
    }

    private fun startCapturing() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ), REQUEST_CODE_STORAGE_PERMISSION)
            return
        }

        isCapturing = true
        imageCount = 0
        gpsTracker.getLocation()
        val groupName = "Group_${System.currentTimeMillis()}"
        val dateTime = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale.getDefault()).format(Date())
        currentGroup = PhotoGroup(groupName = groupName, dateTime = dateTime)

        lastLatitude = gpsTracker.latitude
        lastLongitude = gpsTracker.longitude

        Log.d(TAG, "Initial coordinates: Latitude: $lastLatitude, Longitude: $lastLongitude")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val groupId = saveGroupToDatabase(currentGroup!!)
                currentGroup?.id = groupId
                withContext(Dispatchers.Main) {
                    capturePhoto()
                    handler.post(captureRunnable)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save group to database: ${e.message}")
                stopCapturing()
            }
        }
    }

    private fun stopCapturing() {
        isCapturing = false
        handler.removeCallbacks(captureRunnable)
        releaseCamera()
        currentGroup = null
        Log.d(TAG, "Capturing stopped")
    }

    private val captureRunnable = object : Runnable {
        override fun run() {
            if (isCapturing) {
                try {
                    gpsTracker.getLocation()
                    val currentLatitude = gpsTracker.latitude
                    val currentLongitude = gpsTracker.longitude

                    Log.d(TAG, "Current coordinates: Latitude: $currentLatitude, Longitude: $currentLongitude")

                    val distance = calculateDistance(lastLatitude, lastLongitude, currentLatitude, currentLongitude)
                    Log.d(TAG, "Distance calculated: $distance meters")

                    if (distance >= captureDistance) {
                        lastLatitude = currentLatitude
                        lastLongitude = currentLongitude
                        capturePhoto()
                    }

                    CoroutineScope(Dispatchers.Main).launch {
                        binding.tvDistance.text = "Distance: ${distance.toInt()} m"
                    }

                    handler.postDelayed(this, 1000)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in captureRunnable: ${e.message}")
                    stopCapturing()
                }
            }
        }
    }

    private fun capturePhoto() {
        camera?.takePicture(null, null, Camera.PictureCallback { data, _ ->
            try {
                val pictureFile = createImageFile(currentGroup!!.groupName)
                val fos = FileOutputStream(pictureFile)
                fos.write(data)
                fos.close()
                savePhotoWithMetadata(pictureFile.absolutePath)
                imageCount++
                CoroutineScope(Dispatchers.Main).launch {
                    updateUI()
                }
                restartPreview()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving picture: ${e.message}")
                restartPreview()
            }
        })
    }

    private fun restartPreview() {
        try {
            camera?.stopPreview()
            camera?.setPreviewDisplay(surfaceHolder)
            camera?.startPreview()
        } catch (e: IOException) {
            Log.e(TAG, "Error restarting camera preview: ${e.message}")
        }
    }

    private fun createImageFile(groupName: String): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), groupName)
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun savePhotoWithMetadata(filePath: String) {
        CoroutineScope(Dispatchers.IO).launch {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                withContext(Dispatchers.Main) {
                    requestStoragePermissions()
                    Toast.makeText(requireContext(), "Permission needed to save snapshot", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            try {
                val latitude = gpsTracker.latitude
                val longitude = gpsTracker.longitude

                if (latitude == 0.0 && longitude == 0.0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to get location", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val geocoder = Geocoder(requireContext(), Locale.getDefault())
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)

                val address = addresses?.firstOrNull()?.getAddressLine(0) ?: "Address not found"

                val coordinatesText = "Latitude: $latitude, Longitude: $longitude"
                val dateTime = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale.getDefault()).format(Date())

                withContext(Dispatchers.Main) {
                    binding.tvLocation.text = coordinatesText
                    binding.tvDateTime.text = dateTime
                    binding.tvAddress.text = address
                }

                val snapshot = SnapshotEntity(
                    path = filePath,
                    coordinates = coordinatesText,
                    address = address,
                    dateTime = dateTime,
                    latitude = latitude,
                    longitude = longitude,
                    groupId = currentGroup!!.id
                )

                savePhotoDetailsToDatabase(snapshot)

            } catch (e: IOException) {
                Log.e(TAG, "Failed to save photo", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to save photo", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun savePhotoDetailsToDatabase(snapshot: SnapshotEntity) {
        CoroutineScope(Dispatchers.IO).launch {
            database.snapshotDao().insert(snapshot)
            Log.d(TAG, "Photo details saved to database with groupId: ${snapshot.groupId}")
        }
    }

    private suspend fun saveGroupToDatabase(group: PhotoGroup): Long {
        return withContext(Dispatchers.IO) {
            database.snapshotDao().insertGroup(group)
        }
    }

    private suspend fun updateUI() {
        withContext(Dispatchers.Main) {
            binding.tvDistance.text = "Distance: 0 m"
            binding.tvImageCount.text = "Images: $imageCount"
        }
    }

    private fun requestStoragePermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CODE_STORAGE_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
                Toast.makeText(requireContext(), "Permission needed to save photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceHolder = holder
        openCamera()
    }

    private fun openCamera() {
        try {
            camera = Camera.open()
            camera?.setDisplayOrientation(90)
            camera?.setPreviewDisplay(surfaceHolder)
            camera?.startPreview()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting camera preview: ${e.message}")
            Toast.makeText(requireContext(), "Failed to open camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun releaseCamera() {
        camera?.stopPreview()
        camera?.release()
        camera = null
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (holder.surface == null) {
            return
        }
        restartPreview()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        releaseCamera()
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }
}

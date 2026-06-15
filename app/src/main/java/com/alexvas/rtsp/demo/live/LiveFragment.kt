package com.alexvas.rtsp.demo.live

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.alexvas.rtsp.demo.data.AppDatabase
import com.alexvas.rtsp.demo.data.PhotoGroup
import com.alexvas.rtsp.demo.data.SnapshotEntity
import com.alexvas.rtsp.demo.databinding.FragmentLiveBinding
import com.azhar.captureimage.utils.GPSTracker
import com.alexvas.rtsp.widget.RtspSurfaceView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("LogNotTimber")
class LiveFragment : Fragment() {

    private lateinit var binding: FragmentLiveBinding
    private lateinit var liveViewModel: LiveViewModel
    private lateinit var gpsTracker: GPSTracker
    private lateinit var database: AppDatabase
    private var currentGroup: PhotoGroup? = null

    private var isCapturing = false
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "LiveFragment"
        private const val REQUEST_CODE_STORAGE_PERMISSION = 2001
    }

    private val rtspStatusListener = object : RtspSurfaceView.RtspStatusListener {
        override fun onRtspStatusConnecting() {
            binding.apply {
                tvStatus.text = "Connecting"
                pbLoading.visibility = View.VISIBLE
                vShutter.visibility = View.VISIBLE
                etRtspRequest.isEnabled = false
                etRtspUsername.isEnabled = false
                etRtspPassword.isEnabled = false
                cbVideo.isEnabled = false
                cbAudio.isEnabled = false
                cbDebug.isEnabled = false
                tgRotation.isEnabled = false
            }
            Log.d(TAG, "Connecting")
        }

        override fun onRtspStatusConnected() {
            binding.apply {
                tvStatus.text = "Connected"
                bnStartStop.text = "Stop Stream"
                pbLoading.visibility = View.GONE
                bnSnapshot.isEnabled = true
            }
            Log.d(TAG, "Connected")
        }

        override fun onRtspStatusDisconnecting() {
            binding.apply {
                tvStatus.text = "Disconnecting"
            }
            Log.d(TAG, "Disconnecting")
        }

        override fun onRtspStatusDisconnected() {
            binding.apply {
                tvStatus.text = "Disconnected"
                bnStartStop.text = "Start Stream"
                pbLoading.visibility = View.GONE
                vShutter.visibility = View.VISIBLE
                bnSnapshot.isEnabled = false
                cbVideo.isEnabled = true
                cbAudio.isEnabled = true
                cbDebug.isEnabled = true
                etRtspRequest.isEnabled = true
                etRtspUsername.isEnabled = true
                etRtspPassword.isEnabled = true
                tgRotation.isEnabled = true
            }
            Log.d(TAG, "Disconnected")
        }

        override fun onRtspStatusFailedUnauthorized() {
            if (context == null) return
            binding.apply {
                tvStatus.text = "Username or password invalid"
                pbLoading.visibility = View.GONE
            }
            Toast.makeText(context, binding.tvStatus.text, Toast.LENGTH_LONG).show()
            Log.e(TAG, "Failed: Unauthorized")
        }

        override fun onRtspStatusFailed(message: String?) {
            if (context == null) return
            binding.apply {
                tvStatus.text = "Error: $message"
                Toast.makeText(context, tvStatus.text, Toast.LENGTH_LONG).show()
                pbLoading.visibility = View.GONE
            }
            Log.e(TAG, "Failed: $message")
        }

        override fun onRtspFirstFrameRendered() {
            binding.apply {
                vShutter.visibility = View.GONE
                bnSnapshot.isEnabled = true
                startCapturing()
            }
            Log.d(TAG, "First frame rendered")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        liveViewModel = ViewModelProvider(this).get(LiveViewModel::class.java)
        binding = FragmentLiveBinding.inflate(inflater, container, false)
        database = AppDatabase.getDatabase(requireContext())

        binding.svVideo.setStatusListener(rtspStatusListener)
        binding.etRtspRequest.addTextChangedListener(createTextWatcher { liveViewModel.rtspRequest.value = it })
        binding.etRtspUsername.addTextChangedListener(createTextWatcher { liveViewModel.rtspUsername.value = it })
        binding.etRtspPassword.addTextChangedListener(createTextWatcher { liveViewModel.rtspPassword.value = it })

        liveViewModel.rtspRequest.observe(viewLifecycleOwner) {
            if (binding.etRtspRequest.text.toString() != it)
                binding.etRtspRequest.setText(it)
        }
        liveViewModel.rtspUsername.observe(viewLifecycleOwner) {
            if (binding.etRtspUsername.text.toString() != it)
                binding.etRtspUsername.setText(it)
        }
        liveViewModel.rtspPassword.observe(viewLifecycleOwner) {
            if (binding.etRtspPassword.text.toString() != it)
                binding.etRtspPassword.setText(it)
        }

        binding.bnRotate0.setOnClickListener { binding.svVideo.videoRotation = 0 }
        binding.bnRotate90.setOnClickListener { binding.svVideo.videoRotation = 90 }
        binding.bnRotate180.setOnClickListener { binding.svVideo.videoRotation = 180 }
        binding.bnRotate270.setOnClickListener { binding.svVideo.videoRotation = 270 }

        binding.bnRotate0.performClick()

        binding.bnStartStop.setOnClickListener {
            if (binding.svVideo.isStarted()) {
                binding.svVideo.stop()
                stopCapturing()
                Log.d(TAG, "Stream stopped")
            } else {
                val uri = Uri.parse(liveViewModel.rtspRequest.value)
                if (uri != null && liveViewModel.rtspRequest.value!!.isNotEmpty()) {
                    val scheme = uri.scheme
                    if (scheme.equals("rtsp", ignoreCase = true)) {
                        binding.svVideo.visibility = View.VISIBLE
                        startRtspStream(uri)
                    } else {
                        Toast.makeText(requireContext(), "Unsupported URL scheme", Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Unsupported URL scheme")
                    }
                } else {
                    Toast.makeText(requireContext(), "URL is empty", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "URL is empty")
                }
            }
        }

        binding.bnSnapshot.setOnClickListener {
            val bitmap = getSnapshot()
            if (bitmap != null) {
                saveSnapshotToDCIM(bitmap)
            } else {
                Toast.makeText(requireContext(), "Snapshot failed", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Snapshot failed")
            }
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        liveViewModel.loadParams(requireContext())
    }

    override fun onPause() {
        super.onPause()
        liveViewModel.saveParams(requireContext())
        if (binding.svVideo.isStarted()) {
            binding.svVideo.stop()
        }
    }

    private fun createTextWatcher(onTextChanged: (String) -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onTextChanged(s.toString())
            }
        }
    }

    private fun getSnapshot(): Bitmap? {
        val surfaceBitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val lock = Object()
        val success = AtomicBoolean(false)
        val thread = HandlerThread("PixelCopyHelper")
        thread.start()
        val sHandler = Handler(thread.looper)
        val listener = PixelCopy.OnPixelCopyFinishedListener { copyResult ->
            success.set(copyResult == PixelCopy.SUCCESS)
            synchronized(lock) {
                lock.notify()
            }
        }
        synchronized(lock) {
            PixelCopy.request(binding.svVideo.holder.surface, surfaceBitmap, listener, sHandler)
            lock.wait()
        }
        thread.quitSafely()
        return if (success.get()) surfaceBitmap else null
    }

    private fun saveSnapshotToDCIM(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                withContext(Dispatchers.Main) {
                    requestStoragePermissions()
                    Toast.makeText(requireContext(), "Permission needed to save snapshot", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val groupName = currentGroup?.groupName ?: "Snapshots"
            val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            val snapshotDir = File(dcimDir, groupName)
            if (!snapshotDir.exists()) {
                snapshotDir.mkdirs()
            }
            val snapshotFile = File(snapshotDir, "snapshot_${System.currentTimeMillis()}.jpg")
            try {
                FileOutputStream(snapshotFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    out.flush()
                }

                withContext(Dispatchers.Main) {
                    gpsTracker = GPSTracker(requireContext())
                    gpsTracker.getLocation()
                }

                val latitude = gpsTracker.latitude
                val longitude = gpsTracker.longitude

                val geocoder = Geocoder(requireContext(), Locale.getDefault())
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)

                val address = addresses?.let {
                    if (it.isNotEmpty()) it[0].getAddressLine(0) else "Address not found"
                } ?: "Address not found"

                val coordinatesText = "Latitude: $latitude, Longitude: $longitude"
                val dateTime = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale.getDefault()).format(Date())

                withContext(Dispatchers.Main) {
                    binding.tvLocation.text = coordinatesText
                    binding.tvDateTime.text = dateTime
                    binding.tvAddress.text = address
                }

                saveSnapshotDetailsToDatabase(snapshotFile.absolutePath, coordinatesText, address, dateTime, latitude, longitude, currentGroup!!.id)

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Snapshot saved: $address", Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to save snapshot", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to save snapshot", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveSnapshotDetailsToDatabase(path: String, coordinates: String, address: String, dateTime: String, latitude: Double, longitude: Double, groupId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            val snapshotEntity = SnapshotEntity(
                path = path,
                coordinates = coordinates,
                address = address,
                dateTime = dateTime,
                latitude = latitude,
                longitude = longitude,
                groupId = groupId
            )
            database.snapshotDao().insert(snapshotEntity)
            Log.d(TAG, "Snapshot details saved to database")
        }
    }

    private suspend fun saveGroupToDatabase(group: PhotoGroup): Long {
        return withContext(Dispatchers.IO) {
            database.snapshotDao().insertGroup(group)
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
                Toast.makeText(requireContext(), "Permission needed to save snapshot", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startRtspStream(uri: Uri) {
        binding.svVideo.init(uri, liveViewModel.rtspUsername.value, liveViewModel.rtspPassword.value, "rtsp-client-android")
        binding.svVideo.debug = binding.cbDebug.isChecked
        binding.svVideo.start(binding.cbVideo.isChecked, binding.cbAudio.isChecked)
        Log.d(TAG, "RTSP started with URI: $uri")
    }

    private fun startCapturing() {
        gpsTracker = GPSTracker(requireContext())
        isCapturing = true
        gpsTracker.getLocation()

        val groupName = "Group_${System.currentTimeMillis()}"
        val dateTime = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale.getDefault()).format(Date())
        currentGroup = PhotoGroup(groupName = groupName, dateTime = dateTime)

        CoroutineScope(Dispatchers.IO).launch {
            val groupId = saveGroupToDatabase(currentGroup!!)
            currentGroup?.id = groupId
            withContext(Dispatchers.Main) {
                handler.post(captureRunnable)
            }
        }

        Log.d(TAG, "Started capturing snapshots every 5 seconds")
    }

    private fun stopCapturing() {
        isCapturing = false
        handler.removeCallbacks(captureRunnable)
        Log.d(TAG, "Stopped capturing snapshots")
    }

    private val captureRunnable = object : Runnable {
        override fun run() {
            if (isCapturing) {
                val bitmap = getSnapshot()
                if (bitmap != null) {
                    saveSnapshotToDCIM(bitmap)
                } else {
                    Log.e(TAG, "Snapshot failed")
                }
                handler.postDelayed(this, 5000)
            }
        }
    }
}

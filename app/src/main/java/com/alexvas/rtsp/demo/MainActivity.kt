package com.alexvas.rtsp.demo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.alexvas.rtsp.demo.live.LiveFragment
import com.alexvas.rtsp.demo.local.LocalCameraFragment
import com.alexvas.rtsp.demo.camera.CameraMenuActivity
import com.alexvas.rtsp.demo.history.HistoryFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.location.LocationEngineRequest
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin
import com.mapbox.maps.plugin.locationcomponent.location

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var locationEngine: LocationEngine
    private lateinit var locationComponentPlugin: LocationComponentPlugin
    private var currentLocation: Point? = null

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1001
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!allPermissionsGranted()) {
            requestPermissions()
        } else {
            initializeApp()
        }

        when (intent.getStringExtra("open_fragment")) {
            "LiveFragment" -> openFragment(LiveFragment())
            "LocalCameraFragment" -> openFragment(LocalCameraFragment());
        }
    }

    private fun initializeApp() {
        mapView = findViewById(R.id.mapView)
        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) {
            initializeLocationComponent()
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener(navListener)

        val myLocationButton = findViewById<ImageButton>(R.id.my_location_button)
        myLocationButton.setOnClickListener {
            currentLocation?.let {
                mapView.getMapboxMap().setCamera(
                    com.mapbox.maps.CameraOptions.Builder()
                        .center(it)
                        .zoom(14.0)
                        .build()
                )
            } ?: run {
                Toast.makeText(this, "Lokasi belum tersedia", Toast.LENGTH_SHORT).show()
            }
        }

        showRekamanLayout()
    }

    private fun initializeLocationComponent() {
        locationComponentPlugin = mapView.location
        locationComponentPlugin.updateSettings {
            enabled = true
            pulsingEnabled = true
        }

        locationEngine = LocationEngineProvider.getBestLocationEngine(this)
        val request = LocationEngineRequest.Builder(1000L)
            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .setMaxWaitTime(5000L)
            .build()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationEngine.requestLocationUpdates(
                request,
                locationCallback,
                mainLooper
            )
        } else {
            requestPermissions()
        }
    }

    private val locationCallback = object : LocationEngineCallback<LocationEngineResult> {
        override fun onSuccess(result: LocationEngineResult?) {
            result?.lastLocation?.let { location ->
                currentLocation = Point.fromLngLat(location.longitude, location.latitude)
            }
        }

        override fun onFailure(exception: Exception) {
            Toast.makeText(this@MainActivity, "Gagal mendapatkan lokasi: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private val navListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.nav_back -> {
                showRekamanLayout()
                true
            }
            R.id.navigation_live -> {
                val intent = Intent(this, CameraMenuActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.nav_history -> {
                openFragment(HistoryFragment())
                true
            }
            else -> false
        }
    }

    private fun showRekamanLayout() {
        findViewById<View>(R.id.layout_rekaman).visibility = View.VISIBLE
        findViewById<View>(R.id.fragment_container).visibility = View.GONE
        findViewById<View>(R.id.layout_logs).visibility = View.GONE
    }

    private fun showLogsLayout() {
        findViewById<View>(R.id.layout_rekaman).visibility = View.GONE
        findViewById<View>(R.id.fragment_container).visibility = View.GONE
        findViewById<View>(R.id.layout_logs).visibility = View.VISIBLE
    }

    private fun openFragment(fragment: Fragment) {
        findViewById<View>(R.id.layout_rekaman).visibility = View.GONE
        findViewById<View>(R.id.layout_logs).visibility = View.GONE
        findViewById<View>(R.id.fragment_container).visibility = View.VISIBLE
        val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            REQUIRED_PERMISSIONS,
            REQUEST_CODE_PERMISSIONS
        )
    }

    fun getUserLocation(): Point? {
        return currentLocation
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                initializeApp()
            } else {
                Toast.makeText(this, "Izin diperlukan untuk menjalankan aplikasi ini", Toast.LENGTH_SHORT).show()
                finish()
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

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }
}

package com.alexvas.rtsp.demo.camera

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.alexvas.rtsp.demo.MainActivity
import com.alexvas.rtsp.demo.R

class CameraMenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_menu)

        val cameraButton: Button = findViewById(R.id.button_camera)
        val rtspCameraButton: Button = findViewById(R.id.button_rtsp_camera)

        cameraButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("open_fragment", "LocalCameraFragment")
            startActivity(intent)
        }

        rtspCameraButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("open_fragment", "LiveFragment")
            startActivity(intent)
        }
    }
}

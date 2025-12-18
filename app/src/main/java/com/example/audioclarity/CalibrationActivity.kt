package com.example.audioclarity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class CalibrationActivity : AppCompatActivity() {

    private lateinit var latencyTester: LatencyTester
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var btnStartCalibration: Button
    private lateinit var tvStatus: TextView

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            runCalibration()
        } else {
            tvStatus.text = "Calibration failed: microphone permission denied."
            Toast.makeText(
                this,
                "Microphone permission is required for calibration.",
                Toast.LENGTH_LONG
            ).show()
            // Offer user a button to open App Settings so they can enable microphone permission
            btnStartCalibration.text = "Open App Settings"
            btnStartCalibration.setOnClickListener {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)

        latencyTester = LatencyTester()
        settingsRepo = SettingsRepository(this)
        btnStartCalibration = findViewById(R.id.btnStartCalibration)
        tvStatus = findViewById(R.id.tvCalibrationStatus)

        btnStartCalibration.setOnClickListener {
            checkPermissionAndRunCalibration()
        }
    }

    private fun checkPermissionAndRunCalibration() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                runCalibration()
            }

            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun runCalibration() {
        btnStartCalibration.isEnabled = false
        tvStatus.text = "Calibrating... Please wait."

        latencyTester.measureLatency { result ->
            runOnUiThread {
                result.onSuccess { latencyMs ->
                    tvStatus.text = "Calibration complete!\nMeasured Latency: $latencyMs ms"
                    settingsRepo.saveLatency(latencyMs)
                    btnStartCalibration.text = "Start Calibration"
                    btnStartCalibration.setOnClickListener { checkPermissionAndRunCalibration() }
                }.onFailure { error ->
                    // Provide actionable guidance for permission errors or timeouts
                    val message = when (error) {
                        is SecurityException -> "Calibration failed: microphone permission denied. Open app settings to enable it."
                        else -> "Calibration failed: ${error.message}"
                    }
                    tvStatus.text = message
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    btnStartCalibration.text = "Open App Settings"
                    btnStartCalibration.isEnabled = true
                    btnStartCalibration.setOnClickListener {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    }
                }
            }
        }
    }
}

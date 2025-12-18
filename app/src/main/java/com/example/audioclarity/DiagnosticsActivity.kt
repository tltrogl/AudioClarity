package com.example.audioclarity

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Bundle
import android.os.IBinder
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer

class DiagnosticsActivity : AppCompatActivity() {

    private var audioService: AudioService? = null
    private var isBound = false
    private lateinit var audioManager: AudioManager
    private lateinit var settingsRepo: SettingsRepository

    private lateinit var tvOutputDevice: TextView
    private lateinit var tvSampleRate: TextView
    private lateinit var tvBufferSize: TextView
    private lateinit var tvAgc: TextView
    private lateinit var tvNs: TextView
    private lateinit var tvAec: TextView
    private lateinit var tvPitch: TextView
    private lateinit var tvLatency: TextView

    private val diagnosticsObserver = Observer<DiagnosticsData> { data ->
        tvSampleRate.text = "Sample Rate: ${data.sampleRate} Hz"
        tvBufferSize.text = "Buffer Size: ${data.bufferSize} samples"
        tvPitch.text = if (data.detectedPitch > 0) "Detected Pitch: ${
            String.format(
                "%.1f",
                data.detectedPitch
            )
        } Hz" else "Detected Pitch: N/A"
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as AudioService.LocalBinder
            audioService = binder.getService()
            isBound = true
            audioService?.diagnosticsData?.observe(this@DiagnosticsActivity, diagnosticsObserver)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            audioService?.diagnosticsData?.removeObserver(diagnosticsObserver)
            audioService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        settingsRepo = SettingsRepository(this)

        tvOutputDevice = findViewById(R.id.tvDiagOutputDevice)
        tvSampleRate = findViewById(R.id.tvDiagSampleRate)
        tvBufferSize = findViewById(R.id.tvDiagBufferSize)
        tvAgc = findViewById(R.id.tvDiagAgc)
        tvNs = findViewById(R.id.tvDiagNs)
        tvAec = findViewById(R.id.tvDiagAec)
        tvPitch = findViewById(R.id.tvDiagPitch)
        tvLatency = findViewById(R.id.tvDiagLatency)

        updateStaticInfo()
    }

    override fun onStart() {
        super.onStart()
        Intent(this, AudioService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        // Also refresh static info in case settings changed
        updateStaticInfo()
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
            audioService?.diagnosticsData?.removeObserver(diagnosticsObserver)
        }
    }

    private fun updateStaticInfo() {
        // --- Output Device ---
        val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val deviceName = outputDevices.firstOrNull()?.productName ?: "Unknown"
        tvOutputDevice.text = "Output Device: $deviceName"

        // --- System Effects ---
        tvAgc.text = "AGC Available: ${AutomaticGainControl.isAvailable()}"
        tvNs.text = "NS Available: ${NoiseSuppressor.isAvailable()}"
        tvAec.text = "AEC Available: ${AcousticEchoCanceler.isAvailable()}"

        // --- Calibrated Latency ---
        val latency = settingsRepo.getSettings().calibratedLatencyMs
        tvLatency.text =
            if (latency > 0) "Calibrated Latency: $latency ms" else "Calibrated Latency: Not run"
    }
}

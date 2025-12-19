package com.example.audioclarity

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var audioService: AudioService? = null
    private var isBound = false

    private lateinit var settingsRepo: SettingsRepository

    private lateinit var btnToggle: Button
    private lateinit var btnReplay: Button
    private lateinit var btnDiagnostics: Button
    private lateinit var btnCalibrate: Button
    private lateinit var btnEqualizer: Button
    private lateinit var tvStatus: TextView
    private lateinit var sliderGain: SeekBar
    private lateinit var tvGainValue: TextView

    private lateinit var switchHpf: SwitchCompat
    private lateinit var switchAutoClarity: SwitchCompat
    private lateinit var switchNoiseGate: SwitchCompat

    private val serviceStateObserver = Observer<ServiceState> { state ->
        updateUIState(state)
    }

    private val dspStateObserver = Observer<DspState> { state ->
        if (isBound && audioService?.isAutoClarityEnabled == true) {
            // Update the visual state of the toggles even when they are disabled
            switchHpf.isChecked = state.isHpfEnabled
            switchNoiseGate.isChecked = state.isNoiseGateEnabled
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as AudioService.LocalBinder
            audioService = binder.getService()
            isBound = true
            audioService?.state?.observe(this@MainActivity, serviceStateObserver)
            audioService?.dspState?.observe(this@MainActivity, dspStateObserver)
            updateUIState(audioService?.state?.value)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            audioService?.state?.removeObserver(serviceStateObserver)
            audioService?.dspState?.removeObserver(dspStateObserver)
            audioService = null
            updateUIState(ServiceState.IDLE)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val postNotificationsGranted = if (Build.VERSION.SDK_INT >= 33) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true
        }

        if (recordAudioGranted && postNotificationsGranted) {
            toggleAudioService(start = true)
        } else {
            Toast.makeText(this, getString(R.string.mic_permission_required), Toast.LENGTH_SHORT)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settingsRepo = SettingsRepository(this)

        tvStatus = findViewById(R.id.tvStatus)
        btnToggle = findViewById(R.id.btnToggle)
        btnReplay = findViewById(R.id.btnReplay)
        btnDiagnostics = findViewById(R.id.btnDiagnostics)
        btnCalibrate = findViewById(R.id.btnCalibrate)
        btnEqualizer = findViewById(R.id.btnEqualizer)
        sliderGain = findViewById(R.id.sliderGain)
        tvGainValue = findViewById(R.id.tvGainValue)

        switchHpf = findViewById(R.id.switchHpf)
        switchAutoClarity = findViewById(R.id.switchAutoClarity)
        switchNoiseGate = findViewById(R.id.switchNoiseGate)

        btnToggle.setOnClickListener {
            checkPermissionsAndToggle()
        }

        btnReplay.setOnClickListener {
            audioService?.replayBoosted()
        }

        btnDiagnostics.setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }

        btnCalibrate.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }

        btnEqualizer.setOnClickListener {
            startActivity(Intent(this, EqualizerActivity::class.java))
        }

        sliderGain.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val gain = 1.0f + (progress * 0.08f)
                tvGainValue.text = String.format(Locale.US, "%.1fx", gain)
                if (fromUser) {
                    saveSettingsAndUpdateService()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        setupToggles()
        loadSettings()
    }

    private fun setupToggles() {
        switchHpf.setOnCheckedChangeListener { _, _ -> saveSettingsAndUpdateService() }
        switchNoiseGate.setOnCheckedChangeListener { _, _ -> saveSettingsAndUpdateService() }
        switchAutoClarity.setOnCheckedChangeListener { _, isChecked ->
            saveSettingsAndUpdateService()
            updateToggleStates(isChecked)
        }
    }

    private fun loadSettings() {
        val settings = settingsRepo.getSettings()
        switchHpf.isChecked = settings.hpfEnabled
        switchAutoClarity.isChecked = settings.autoClarityEnabled
        switchNoiseGate.isChecked = settings.noiseGateEnabled

        // Clamp computed progress to the SeekBar range to avoid out-of-bounds values
        val rawProgress = ((settings.gain - 1.0f) / 0.08f).toInt()
        val progress = rawProgress.coerceIn(0, sliderGain.max)
        sliderGain.progress = progress

        updateToggleStates(settings.autoClarityEnabled)
    }

    private fun saveSettings() {
        val gain = 1.0f + (sliderGain.progress * 0.08f)
        val existing = settingsRepo.getSettings()
        val settings = existing.copy(
            gain = gain,
            hpfEnabled = switchHpf.isChecked,
            nsEnabled = false, // Obsolete
            aecEnabled = false, // Obsolete
            agcEnabled = false, // Obsolete
            autoClarityEnabled = switchAutoClarity.isChecked,
            noiseGateEnabled = switchNoiseGate.isChecked
        )
        settingsRepo.saveSettings(settings)
    }

    private fun saveSettingsAndUpdateService() {
        saveSettings()
        if (isBound && audioService?.state?.value == ServiceState.RUNNING) {
            syncServiceSettings()
        }
    }

    private fun syncServiceSettings() {
        val service = audioService ?: return
        val settings = settingsRepo.getSettings()

        service.setHpfEnabled(settings.hpfEnabled)
        service.isAutoClarityEnabled = settings.autoClarityEnabled
        service.setNoiseGateEnabled(settings.noiseGateEnabled)
        service.setVolumeGain(settings.gain)
        service.setGraphicEqEnabled(settings.graphicEqEnabled)
        service.setGraphicEqGains(settings.graphicEqGainsDb.toFloatArray())
    }

    override fun onStart() {
        super.onStart()
        Intent(this, AudioService::class.java).also { intent ->
            bindService(intent, connection, BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        // Ensure observers are removed and we don't hold on to the service reference
        if (isBound) {
            try {
                unbindService(connection)
            } catch (_: IllegalArgumentException) {
                // ignore if already unbound
            }
        }
        // Remove observers if present and clear reference
        audioService?.state?.removeObserver(serviceStateObserver)
        audioService?.dspState?.removeObserver(dspStateObserver)
        audioService = null
        isBound = false
    }

    private fun checkPermissionsAndToggle() {
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)

        if (Build.VERSION.SDK_INT >= 33) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            toggleAudioService()
        } else {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun toggleAudioService(start: Boolean = false) {
        val currentState = audioService?.state?.value
        if (currentState == ServiceState.STARTING || currentState == ServiceState.STOPPING) {
            return // Ignore clicks while in transition
        }

        // If caller explicitly requested start OR the service reports IDLE OR we are not bound yet (null),
        // attempt to start the service. This makes the UI action tolerant to the bind happening async
        // (clicking quickly after app start) and when we're not yet observing the service state.
        if (start || currentState == ServiceState.IDLE || currentState == null) {
            val settings = settingsRepo.getSettings()
            val startIntent = Intent(this, AudioService::class.java).apply {
                action = AudioService.ACTION_START
                putExtra("gain", settings.gain)
                putExtra("hpf", settings.hpfEnabled)
                putExtra("auto_clarity", settings.autoClarityEnabled)
                putExtra("noise_gate", settings.noiseGateEnabled)
                putExtra("latency", settings.calibratedLatencyMs)
                putExtra("graphic_eq_enabled", settings.graphicEqEnabled)
                putExtra("graphic_eq_gains", settings.graphicEqGainsDb.toFloatArray())
            }
            startService(startIntent)
        } else if (currentState == ServiceState.RUNNING) {
            stopService(Intent(this, AudioService::class.java))
        }
    }

    private fun updateUIState(state: ServiceState?) {
        val isRunning = state == ServiceState.RUNNING
        btnReplay.visibility = if (isRunning) View.VISIBLE else View.GONE

        when (state) {
            ServiceState.IDLE -> {
                tvStatus.text = getString(R.string.status_stopped)
                btnToggle.text = getString(R.string.start_audio_passthrough)
                btnToggle.isEnabled = true
            }

            ServiceState.STARTING -> {
                tvStatus.text = getString(R.string.status_starting)
                btnToggle.isEnabled = false
            }

            ServiceState.RUNNING -> {
                tvStatus.text = getString(R.string.status_running)
                btnToggle.text = getString(R.string.stop_audio_passthrough)
                btnToggle.isEnabled = true
            }

            ServiceState.STOPPING -> {
                tvStatus.text = getString(R.string.status_stopping)
                btnToggle.isEnabled = false
            }

            null -> {
                tvStatus.text = getString(R.string.status_unknown)
                btnToggle.isEnabled = false
            }
        }
    }

    private fun updateToggleStates(isAutoClarityOn: Boolean) {
        // Disable manual toggles when auto-clarity is on
        switchHpf.isEnabled = !isAutoClarityOn
        switchNoiseGate.isEnabled = !isAutoClarityOn
    }
}

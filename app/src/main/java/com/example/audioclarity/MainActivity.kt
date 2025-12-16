package com.example.audioclarity

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer

class MainActivity : AppCompatActivity() {

    private var audioService: AudioService? = null
    private var isBound = false

    private lateinit var settingsRepo: SettingsRepository
    
    private lateinit var btnToggle: Button
    private lateinit var btnReplay: Button
    private lateinit var btnDiagnostics: Button
    private lateinit var tvStatus: TextView
    private lateinit var sliderGain: SeekBar
    private lateinit var tvGainValue: TextView
    
    private lateinit var switchHpf: Switch
    private lateinit var switchNs: Switch
    private lateinit var switchAec: Switch
    private lateinit var switchAgc: Switch
    private lateinit var switchAutoClarity: Switch
    private lateinit var switchNoiseGate: Switch

    private val serviceStateObserver = Observer<ServiceState> { state ->
        updateUIState(state)
    }
    
    private val dspStateObserver = Observer<DspState> { state ->
        if (isBound && audioService?.isAutoClarityEnabled == true) {
            // Update the visual state of the toggles even when they are disabled
            switchHpf.isChecked = state.isHpfEnabled
            // Noise gate is independent, no need to update its visual state from here
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
             Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_SHORT).show()
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
        sliderGain = findViewById(R.id.sliderGain)
        tvGainValue = findViewById(R.id.tvGainValue)
        
        switchHpf = findViewById(R.id.switchHpf)
        switchNs = findViewById(R.id.switchNs)
        switchAec = findViewById(R.id.switchAec)
        switchAgc = findViewById(R.id.switchAgc)
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

        sliderGain.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val gain = 1.0f + (progress * 0.08f)
                tvGainValue.text = String.format("%.1fx", gain)
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
        switchNs.setOnCheckedChangeListener { _, _ -> saveSettingsAndUpdateService() }
        switchAec.setOnCheckedChangeListener { _, _ -> saveSettingsAndUpdateService() }
        switchAgc.setOnCheckedChangeListener { _, _ -> saveSettingsAndUpdateService() }
        switchNoiseGate.setOnCheckedChangeListener { _, _ -> saveSettingsAndUpdateService() }
        switchAutoClarity.setOnCheckedChangeListener { _, isChecked -> 
            saveSettingsAndUpdateService()
            updateToggleStates(isChecked)
        }
    }

    private fun loadSettings() {
        val settings = settingsRepo.getSettings()
        switchHpf.isChecked = settings.hpfEnabled
        switchNs.isChecked = settings.nsEnabled
        switchAec.isChecked = settings.aecEnabled
        switchAgc.isChecked = settings.agcEnabled
        switchAutoClarity.isChecked = settings.autoClarityEnabled
        switchNoiseGate.isChecked = settings.noiseGateEnabled
        
        val progress = ((settings.gain - 1.0f) / 0.08f).toInt()
        sliderGain.progress = progress

        updateToggleStates(settings.autoClarityEnabled)
    }

    private fun saveSettings() {
        val gain = 1.0f + (sliderGain.progress * 0.08f)
        val settings = AudioSettings(
            gain = gain,
            hpfEnabled = switchHpf.isChecked,
            nsEnabled = switchNs.isChecked,
            aecEnabled = switchAec.isChecked,
            agcEnabled = switchAgc.isChecked,
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
        service.setNsEnabled(settings.nsEnabled)
        service.setAecEnabled(settings.aecEnabled)
        service.setAgcEnabled(settings.agcEnabled)
        service.isAutoClarityEnabled = settings.autoClarityEnabled
        service.setNoiseGateEnabled(settings.noiseGateEnabled)
        service.setVolumeGain(settings.gain)
    }

    override fun onStart() {
        super.onStart()
        Intent(this, AudioService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
            audioService?.state?.removeObserver(serviceStateObserver)
            audioService?.dspState?.removeObserver(dspStateObserver)
        }
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
            requestPermissionLauncher.launch(missingPermissions.toArray(emptyArray()))
        }
    }

    private fun toggleAudioService(start: Boolean = false) {
        val currentState = audioService?.state?.value
        if (currentState == ServiceState.STARTING || currentState == ServiceState.STOPPING) {
            return // Ignore clicks while in transition
        }

        if (start || currentState == ServiceState.IDLE) {
            val settings = settingsRepo.getSettings()
            val startIntent = Intent(this, AudioService::class.java).apply {
                action = AudioService.ACTION_START
                putExtra("gain", settings.gain)
                putExtra("hpf", settings.hpfEnabled)
                putExtra("ns", settings.nsEnabled)
                putExtra("aec", settings.aecEnabled)
                putExtra("agc", settings.agcEnabled)
                putExtra("auto_clarity", settings.autoClarityEnabled)
                putExtra("noise_gate", settings.noiseGateEnabled)
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
                tvStatus.text = "Status: Stopped"
                btnToggle.text = "Start Audio Passthrough"
                btnToggle.isEnabled = true
            }
            ServiceState.STARTING -> {
                tvStatus.text = "Status: Starting..."
                btnToggle.isEnabled = false
            }
            ServiceState.RUNNING -> {
                tvStatus.text = "Status: Running"
                btnToggle.text = "Stop Audio Passthrough"
                btnToggle.isEnabled = true
            }
            ServiceState.STOPPING -> {
                tvStatus.text = "Status: Stopping..."
                btnToggle.isEnabled = false
            }
            null -> {
                tvStatus.text = "Status: Unknown"
                btnToggle.isEnabled = false
            }
        }
    }

    private fun updateToggleStates(isAutoClarityOn: Boolean) {
        // Disable manual toggles when auto-clarity is on
        switchHpf.isEnabled = !isAutoClarityOn
        // The other toggles are independent and can be used with Auto-Clarity
    }
}

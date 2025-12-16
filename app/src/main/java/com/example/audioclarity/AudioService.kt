package com.example.audioclarity

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlin.concurrent.thread

enum class ServiceState {
    IDLE,
    STARTING,
    RUNNING,
    STOPPING
}

class AudioService : Service() {

    private val binder = LocalBinder()
    private var audioThread: Thread? = null

    private val _state = MutableLiveData(ServiceState.IDLE)
    val state: LiveData<ServiceState> = _state

    private val _dspState = MutableLiveData<DspState>()
    val dspState: LiveData<DspState> = _dspState

    private val _diagnosticsData = MutableLiveData<DiagnosticsData>()
    val diagnosticsData: LiveData<DiagnosticsData> = _diagnosticsData

    // Audio Config - Made public for diagnostics
    val sampleRate = 44100
    var bufferSize = 0
        private set

    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    
    // Scout Mode Ring Buffer (5 seconds)
    private val ringBuffer = RingBuffer(sampleRate * 5)

    // DSP
    private lateinit var dspChain: DspChain
    private val vad = VoiceActivityDetector()
    private var pitchDetector: PitchDetector? = null

    // Android Effects
    private var agc: AutomaticGainControl? = null
    private var ns: NoiseSuppressor? = null
    private var aec: AcousticEchoCanceler? = null
    
    // Config Flags
    private var enableAgc = false
    private var enableNs = true
    private var enableAec = true
    var isAutoClarityEnabled = false

    // Pitch tracking state
    @Volatile
    var lastDetectedPitch = 0f
        private set
    private var lastStablePitch = 0f
    private var pitchConsecutiveFrames = 0

    // Audio Output Monitoring
    private lateinit var audioManager: AudioManager
    private var audioDeviceCallback: android.media.AudioDeviceCallback? = null
    private val handler = Handler(Looper.getMainLooper())

    inner class LocalBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    override fun onBind(intent: Intent): IBinder {
        DiagLogger.log(DiagLogger.Level.INFO, "Service bound")
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        DiagLogger.log(DiagLogger.Level.INFO, "Service creating")
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        dspChain = DspChain(sampleRate)
        createNotificationChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
        DiagLogger.log(DiagLogger.Level.INFO, "Service destroying")
        stopAudioPassthrough()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DiagLogger.log(DiagLogger.Level.DEBUG, "onStartCommand action: ${intent?.action}")
        when (intent?.action) {
            ACTION_STOP -> {
                stopAudioPassthrough()
                stopSelf()
            }
            ACTION_START -> {
                intent.extras?.let {
                    setVolumeGain(it.getFloat("gain", 1.0f))
                    setHpfEnabled(it.getBoolean("hpf", true))
                    setNsEnabled(it.getBoolean("ns", true))
                    setAecEnabled(it.getBoolean("aec", true))
                    setAgcEnabled(it.getBoolean("agc", false))
                    isAutoClarityEnabled = it.getBoolean("auto_clarity", false)
                    setNoiseGateEnabled(it.getBoolean("noise_gate", false))
                }
                startAudioPassthrough()
            }
            ACTION_REPLAY -> {
                replayBoosted()
            }
        }
        return START_NOT_STICKY
    }

    fun startAudioPassthrough() {
        if (_state.value != ServiceState.IDLE) return
        _state.postValue(ServiceState.STARTING)
        DiagLogger.logSession("STARTING")
        
        if (!hasHeadphonesConnected()) {
            DiagLogger.log(DiagLogger.Level.WARN, "Headphones not connected. Aborting start.")
            Toast.makeText(this, "Headphones must be connected.", Toast.LENGTH_SHORT).show()
            _state.postValue(ServiceState.IDLE)
            DiagLogger.logSession("IDLE")
            return
        }

        startForegroundService()
        registerAudioDeviceCallback()

        audioThread = thread(start = true, name = "AudioPassThread") {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            runAudioLoop()
        }
    }

    fun stopAudioPassthrough() {
        if (_state.value == ServiceState.IDLE || _state.value == ServiceState.STOPPING) return
        _state.postValue(ServiceState.STOPPING)
        DiagLogger.logSession("STOPPING")
        
        try {
            audioThread?.join(1000)
        } catch (e: InterruptedException) {
            DiagLogger.logError("Audio thread join interrupted", e)
        }
        audioThread = null
        
        unregisterAudioDeviceCallback()
        stopForeground(STOP_FOREGROUND_REMOVE)
        
        _state.postValue(ServiceState.IDLE)
        DiagLogger.logSession("IDLE")
    }
    
    fun replayBoosted() {
        if (_state.value != ServiceState.RUNNING) return
        DiagLogger.log(DiagLogger.Level.INFO, "Replay triggered")

        val snapshot = ringBuffer.getOrderedSnapshot()
        if (snapshot.isEmpty()) {
            DiagLogger.log(DiagLogger.Level.WARN, "Replay buffer is empty, aborting.")
            return
        }

        thread(start = true, name = "ReplayThread") {
            val boostedDsp = DspChain(sampleRate).apply {
                isHpfEnabled = true // Always use HPF for replay clarity
                gainFactor = dspChain.gainFactor * 1.5f // Boost gain by 50%
            }

            val processedSnapshot = snapshot.copyOf()
            boostedDsp.process(processedSnapshot, processedSnapshot.size)
            
            val replayTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfigOut)
                        .build()
                )
                .setBufferSizeInBytes(processedSnapshot.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            if (replayTrack.state != AudioTrack.STATE_INITIALIZED) {
                DiagLogger.logError("Replay AudioTrack failed to initialize")
                return@thread
            }

            try {
                DiagLogger.log(DiagLogger.Level.INFO, "Playing back ${processedSnapshot.size} boosted samples.")
                // Mute live audio
                val originalGain = dspChain.gainFactor
                dspChain.gainFactor = 0f

                replayTrack.write(processedSnapshot, 0, processedSnapshot.size)
                replayTrack.play()
                // This will block until playback is complete because it's a static track
                Thread.sleep((processedSnapshot.size.toFloat() / sampleRate * 1000).toLong() + 100)
                
                // Restore live audio
                dspChain.gainFactor = originalGain

            } catch (e: Exception) {
                DiagLogger.logError("Replay failed", e)
            } finally {
                replayTrack.release()
                DiagLogger.log(DiagLogger.Level.INFO, "Replay finished.")
            }
        }
    }

    private fun hasHeadphonesConnected(): Boolean {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return outputs.any { dev ->
            when (dev.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_USB_HEADSET -> true
                else -> false
            }
        }
    }

    private fun registerAudioDeviceCallback() {
        if (audioDeviceCallback == null) {
            audioDeviceCallback = object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    checkAudioOutput()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    checkAudioOutput()
                }
            }
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler)
        }
    }

    private fun unregisterAudioDeviceCallback() {
        audioDeviceCallback?.let {
            audioManager.unregisterAudioDeviceCallback(it)
            audioDeviceCallback = null
        }
    }
    
    private fun checkAudioOutput() {
        if (!hasHeadphonesConnected()) {
            DiagLogger.log(DiagLogger.Level.WARN, "Headphones disconnected. Stopping.")
            stopAudioPassthrough()
            handler.post {
                Toast.makeText(applicationContext, "Headphones disconnected. Audio stopped.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun setVolumeGain(gain: Float) {
        dspChain.gainFactor = gain
    }
    
    fun setHpfEnabled(enabled: Boolean) {
        if (!isAutoClarityEnabled) {
            dspChain.isHpfEnabled = enabled
        }
    }

    fun setNoiseGateEnabled(enabled: Boolean) {
        dspChain.isNoiseGateEnabled = enabled
    }
    
    fun setAgcEnabled(enabled: Boolean) {
        enableAgc = enabled
        if (_state.value == ServiceState.RUNNING) {
            agc?.enabled = enabled
        }
    }

    fun setNsEnabled(enabled: Boolean) {
        enableNs = enabled
        if (_state.value == ServiceState.RUNNING) {
            ns?.enabled = enabled
        }
    }

    fun setAecEnabled(enabled: Boolean) {
        enableAec = enabled
        if (_state.value == ServiceState.RUNNING) {
            aec?.enabled = enabled
        }
    }

    private fun runAudioLoop() {
        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            DiagLogger.log(DiagLogger.Level.WARN, "Bad buffer size, falling back.")
             bufferSize = sampleRate / 10 // Fallback
        }
        DiagLogger.logAudioParams(sampleRate, bufferSize)
        
        pitchDetector = PitchDetector(sampleRate, bufferSize)

        val readSize = bufferSize
        val audioBuffer = ShortArray(readSize)

        val audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION

        val recorder = try {
            AudioRecord(audioSource, sampleRate, channelConfigIn, audioFormat, readSize * 2)
        } catch (e: SecurityException) {
            DiagLogger.logError("AudioRecord creation failed (Security)", e)
            _state.postValue(ServiceState.IDLE)
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            DiagLogger.logError("AudioRecord failed to initialize")
            _state.postValue(ServiceState.IDLE)
            return
        }

        setupEffects(recorder.audioSessionId)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfigOut)
                    .build()
            )
            .setBufferSizeInBytes(readSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            DiagLogger.logError("AudioTrack failed to initialize")
            recorder.release()
            releaseEffects()
            _state.postValue(ServiceState.IDLE)
            return
        }
        
        _state.postValue(ServiceState.RUNNING)
        DiagLogger.logSession("RUNNING")
        recorder.startRecording()
        track.play()

        while (_state.value == ServiceState.RUNNING) {
            val readCount = recorder.read(audioBuffer, 0, readSize)
            if (readCount > 0) {
                // Write to ring buffer BEFORE processing
                ringBuffer.write(audioBuffer, readCount)
                
                // Auto-clarity logic
                if (isAutoClarityEnabled) {
                    val isSpeech = vad.isSpeech(audioBuffer, readCount)
                    dspChain.isHpfEnabled = isSpeech
                    dspChain.isSpeechEqEnabled = isSpeech

                    if (isSpeech) {
                        val pitch = pitchDetector?.detect(audioBuffer, readCount) ?: 0.0f
                        lastDetectedPitch = pitch
                        if (pitch > 0) {
                            // Smoothing logic
                            if (kotlin.math.abs(pitch - lastStablePitch) < 25f) { // If new pitch is close to last one
                                pitchConsecutiveFrames++
                            } else {
                                pitchConsecutiveFrames = 0
                                lastStablePitch = pitch
                            }

                            // Update the EQ only after a few stable frames
                            if (pitchConsecutiveFrames > 3) {
                                DiagLogger.log(DiagLogger.Level.DEBUG, "Updating EQ for pitch: $lastStablePitch Hz")
                                dspChain.updatePitchEq(lastStablePitch)
                                pitchConsecutiveFrames = 0 // Reset after update
                            }
                        }
                    } else {
                        lastDetectedPitch = 0f
                    }
                }

                dspChain.process(audioBuffer, readCount)
                track.write(audioBuffer, 0, readCount)

                // Post state updates
                _dspState.postValue(DspState(dspChain.isHpfEnabled, dspChain.isSpeechEqEnabled, dspChain.isNoiseGateEnabled))
                _diagnosticsData.postValue(DiagnosticsData(sampleRate, bufferSize, lastDetectedPitch))

            } else {
                DiagLogger.log(DiagLogger.Level.WARN, "AudioRecord read error: $readCount")
                Thread.sleep(5)
            }
        }

        try {
            track.stop()
            track.release()
            recorder.stop()
            recorder.release()
            releaseEffects()
        } catch (e: Exception) {
            DiagLogger.logError("Resource release failed", e)
        }
    }

    private fun setupEffects(sessionId: Int) {
        val agcAvailable = AutomaticGainControl.isAvailable()
        DiagLogger.logEffect("AGC", agcAvailable)
        if (agcAvailable) {
            agc = AutomaticGainControl.create(sessionId)
            agc?.enabled = enableAgc
        }
        
        val nsAvailable = NoiseSuppressor.isAvailable()
        DiagLogger.logEffect("NS", nsAvailable)
        if (nsAvailable) {
            ns = NoiseSuppressor.create(sessionId)
            ns?.enabled = enableNs
        }

        val aecAvailable = AcousticEchoCanceler.isAvailable()
        DiagLogger.logEffect("AEC", aecAvailable)
        if (aecAvailable) {
            aec = AcousticEchoCanceler.create(sessionId)
            aec?.enabled = enableAec
        }
    }

    private fun releaseEffects() {
        agc?.release()
        ns?.release()
        aec?.release()
        agc = null
        ns = null
        aec = null
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, AudioService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        
        val replayIntent = Intent(this, AudioService::class.java).apply { 
            action = ACTION_REPLAY
        }
        val replayPendingIntent = PendingIntent.getService(this, 2, replayIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Audio Clarity Active")
            .setContentText("Microphone audio is being passed through.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .addAction(android.R.drawable.ic_menu_revert, "Replay", replayPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        if (Build.VERSION.SDK_INT >= 34) {
             startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
             startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Clarity Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "AudioClarityChannel"
        const val NOTIFICATION_ID = 101
        const val ACTION_START = "com.example.audioclarity.ACTION_START"
        const val ACTION_STOP = "com.example.audioclarity.ACTION_STOP"
        const val ACTION_REPLAY = "com.example.audioclarity.ACTION_REPLAY"
    }
}

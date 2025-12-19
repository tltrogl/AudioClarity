package com.example.audioclarity

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import kotlin.math.roundToInt

class EqualizerActivity : AppCompatActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private var audioService: AudioService? = null
    private var isBound = false

    private lateinit var switchEnableEq: SwitchCompat
    private lateinit var btnResetEq: Button
    private lateinit var bandsContainer: LinearLayout

    private val bandControls = mutableListOf<BandControl>()

    private data class BandControl(
        val seekBar: SeekBar,
        val valueLabel: TextView
    )

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as AudioService.LocalBinder
            audioService = binder.getService()
            isBound = true
            applySettingsToService(settingsRepository.getSettings())
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            audioService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_equalizer)

        settingsRepository = SettingsRepository(this)

        switchEnableEq = findViewById(R.id.switchEnableEq)
        btnResetEq = findViewById(R.id.btnResetEq)
        bandsContainer = findViewById(R.id.eqBandsContainer)

        val settings = settingsRepository.getSettings()
        buildBandControls(settings)
        switchEnableEq.isChecked = settings.graphicEqEnabled
        updateBandEnabledState(settings.graphicEqEnabled)

        switchEnableEq.setOnCheckedChangeListener { _, isChecked ->
            persistAndApply(currentBandGains(), isChecked)
            updateBandEnabledState(isChecked)
        }

        btnResetEq.setOnClickListener {
            val zeroed = FloatArray(GraphicEqConfig.BAND_FREQUENCIES.size) { 0f }
            updateBandUI(zeroed)
            persistAndApply(zeroed, switchEnableEq.isChecked)
        }
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
        }
    }

    private fun buildBandControls(settings: AudioSettings) {
        val inflater = layoutInflater
        val gains = settings.graphicEqGainsDb
        GraphicEqConfig.BAND_FREQUENCIES.forEachIndexed { index, freq ->
            val bandView = inflater.inflate(R.layout.item_eq_band, bandsContainer, false)
            val labelView = bandView.findViewById<TextView>(R.id.tvBandLabel)
            val valueView = bandView.findViewById<TextView>(R.id.tvBandValue)
            val seekBar = bandView.findViewById<SeekBar>(R.id.seekBand)

            labelView.text = formatFrequency(freq)
            seekBar.max = progressRange()
            val initialDb = gains.getOrNull(index) ?: 0f
            seekBar.progress = dbToProgress(initialDb)
            valueView.text = formatDb(initialDb)

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val gainDb = progressToDb(progress)
                    valueView.text = formatDb(gainDb)
                    if (fromUser) {
                        persistAndApply(currentBandGains(), switchEnableEq.isChecked)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            bandControls.add(BandControl(seekBar, valueView))
            bandsContainer.addView(bandView)
        }
    }

    private fun persistAndApply(gainsDb: FloatArray, enabled: Boolean) {
        val settings = settingsRepository.getSettings().copy(
            graphicEqEnabled = enabled,
            graphicEqGainsDb = gainsDb.toList()
        )
        settingsRepository.saveSettings(settings)
        applySettingsToService(settings)
    }

    private fun applySettingsToService(settings: AudioSettings) {
        if (!isBound) return
        audioService?.setGraphicEqGains(settings.graphicEqGainsDb.toFloatArray())
        audioService?.setGraphicEqEnabled(settings.graphicEqEnabled)
    }

    private fun updateBandUI(gainsDb: FloatArray) {
        bandControls.forEachIndexed { index, control ->
            val gain = gainsDb.getOrNull(index) ?: 0f
            control.seekBar.progress = dbToProgress(gain)
            control.valueLabel.text = formatDb(gain)
        }
    }

    private fun updateBandEnabledState(enabled: Boolean) {
        bandControls.forEach { control ->
            control.seekBar.isEnabled = enabled
            control.valueLabel.isEnabled = enabled
        }
    }

    private fun currentBandGains(): FloatArray {
        return bandControls.map { control ->
            progressToDb(control.seekBar.progress)
        }.toFloatArray()
    }

    private fun progressRange(): Int {
        return (GraphicEqConfig.MAX_GAIN_DB - GraphicEqConfig.MIN_GAIN_DB).roundToInt()
    }

    private fun dbToProgress(db: Float): Int {
        val clamped = db.coerceIn(GraphicEqConfig.MIN_GAIN_DB, GraphicEqConfig.MAX_GAIN_DB)
        return (clamped - GraphicEqConfig.MIN_GAIN_DB).roundToInt()
    }

    private fun progressToDb(progress: Int): Float {
        val range = GraphicEqConfig.MAX_GAIN_DB - GraphicEqConfig.MIN_GAIN_DB
        val normalized = progress.coerceIn(0, progressRange()) / progressRange().toFloat()
        return (GraphicEqConfig.MIN_GAIN_DB + (normalized * range))
    }

    private fun formatFrequency(freqHz: Float): String {
        return if (freqHz >= 1000f) {
            String.format("%.1f kHz", freqHz / 1000f)
        } else {
            String.format("%.0f Hz", freqHz)
        }
    }

    private fun formatDb(value: Float): String {
        return String.format("%.0f dB", value)
    }
}

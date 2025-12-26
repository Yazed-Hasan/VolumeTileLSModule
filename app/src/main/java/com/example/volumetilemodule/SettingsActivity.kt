package com.example.volumetilemodule

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private val PREFS_NAME = "module_prefs"

    @SuppressLint("SetWorldReadable")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Use world readable mode if possible, or fix permissions manually
        // Note: MODE_WORLD_READABLE is deprecated and throws SecurityException on N+
        // We will use standard mode and try to make the file readable
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val switchSlide = findViewById<Switch>(R.id.switch_slide)
        val switchLongPress = findViewById<Switch>(R.id.switch_long_press)
        val switchSingleTap = findViewById<Switch>(R.id.switch_single_tap)
        val seekbarSensitivity = findViewById<SeekBar>(R.id.seekbar_sensitivity)
        val textSensitivity = findViewById<TextView>(R.id.text_sensitivity_value)
        val seekbarLongPressDelay = findViewById<SeekBar>(R.id.seekbar_long_press_delay)
        val textLongPressDelay = findViewById<TextView>(R.id.text_long_press_delay_value)
        val btnSave = findViewById<Button>(R.id.btn_save)

        // Load current values
        switchSlide.isChecked = prefs.getBoolean("enable_slide", true)
        switchLongPress.isChecked = prefs.getBoolean("enable_long_press", true)
        switchSingleTap.isChecked = prefs.getBoolean("enable_single_tap", false)
        val currentSensitivity = prefs.getInt("slide_sensitivity", 20)
        seekbarSensitivity.progress = currentSensitivity
        textSensitivity.text = "${currentSensitivity}dp"
        
        val currentLongPressDelay = prefs.getInt("long_press_delay", 500)
        seekbarLongPressDelay.progress = currentLongPressDelay
        textLongPressDelay.text = "${currentLongPressDelay}ms"

        // Listeners
        seekbarSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Minimum 5dp to avoid accidental touches
                val value = if (progress < 5) 5 else progress
                textSensitivity.text = "${value}dp"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekbarLongPressDelay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Minimum 100ms
                val value = if (progress < 100) 100 else progress
                textLongPressDelay.text = "${value}ms"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnSave.setOnClickListener {
            val sensitivity = if (seekbarSensitivity.progress < 5) 5 else seekbarSensitivity.progress
            val longPressDelay = if (seekbarLongPressDelay.progress < 100) 100 else seekbarLongPressDelay.progress
            
            // Use commit() to ensure file is written synchronously before we change permissions
            val editor = prefs.edit()
            editor.putBoolean("enable_slide", switchSlide.isChecked)
            editor.putBoolean("enable_long_press", switchLongPress.isChecked)
            editor.putBoolean("enable_single_tap", switchSingleTap.isChecked)
            editor.putInt("slide_sensitivity", sensitivity)
            editor.putInt("long_press_delay", longPressDelay)
            editor.commit()

            // Make the prefs file world readable so Xposed can read it
            fixPermissions()
            
            // Broadcast change to update immediately
            sendBroadcast(android.content.Intent("com.example.volumetilemodule.SETTINGS_CHANGED"))

            Toast.makeText(this, "Settings Saved.", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SetWorldReadable")
    private fun fixPermissions() {
        try {
            val dataDir = File(applicationInfo.dataDir)
            val prefsDir = File(dataDir, "shared_prefs")
            val prefsFile = File(prefsDir, "$PREFS_NAME.xml")

            // Set permissions for data directory
            if (dataDir.exists()) {
                dataDir.setReadable(true, false)
                dataDir.setExecutable(true, false)
            }

            // Set permissions for shared_prefs directory
            if (prefsDir.exists()) {
                prefsDir.setReadable(true, false)
                prefsDir.setExecutable(true, false)
            }

            // Set permissions for the file itself
            if (prefsFile.exists()) {
                prefsFile.setReadable(true, false)
            }
            
            // try using Runtime to chmod (more reliable on some devices)
            try {
                Runtime.getRuntime().exec("chmod 755 ${dataDir.absolutePath}")
                Runtime.getRuntime().exec("chmod 755 ${prefsDir.absolutePath}")
                Runtime.getRuntime().exec("chmod 644 ${prefsFile.absolutePath}")
            } catch (e: Exception) {
                // Ignore if chmod fails
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

package com.plantopo.plantopo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class DebugSettingsActivity : AppCompatActivity() {
    private lateinit var baseUrlEditText: EditText
    private lateinit var currentUrlText: TextView
    private lateinit var saveUrlButton: Button
    private lateinit var resetUrlButton: Button
    private lateinit var useBundledSpaSwitch: SwitchCompat
    private lateinit var openDebugLogButton: Button
    private lateinit var closeButton: Button
    private lateinit var debugSettings: DebugSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_settings)

        debugSettings = DebugSettings.getInstance(this)

        baseUrlEditText = findViewById(R.id.base_url_edit_text)
        currentUrlText = findViewById(R.id.current_url_text)
        saveUrlButton = findViewById(R.id.save_url_button)
        resetUrlButton = findViewById(R.id.reset_url_button)
        useBundledSpaSwitch = findViewById(R.id.use_bundled_spa_switch)
        openDebugLogButton = findViewById(R.id.open_debug_log_button)
        closeButton = findViewById(R.id.close_button)

        updateCurrentUrlDisplay()

        // Set initial state of bundled SPA switch
        useBundledSpaSwitch.isChecked = debugSettings.getUseBundledSpa()

        useBundledSpaSwitch.setOnCheckedChangeListener { _, isChecked ->
            debugSettings.setUseBundledSpa(isChecked)
            restartApp()
        }

        saveUrlButton.setOnClickListener {
            val customUrl = baseUrlEditText.text.toString().trim()
            if (customUrl.isNotEmpty()) {
                debugSettings.setCustomBaseUrl(customUrl)
                restartApp()
            }
        }

        resetUrlButton.setOnClickListener {
            debugSettings.clearCustomBaseUrl()
            restartApp()
        }

        openDebugLogButton.setOnClickListener {
            val intent = Intent(this, DebugLogActivity::class.java)
            startActivity(intent)
        }

        closeButton.setOnClickListener {
            finish()
        }
    }

    private fun updateCurrentUrlDisplay() {
        val currentUrl = Config.BASE_URL
        val customUrl = debugSettings.getCustomBaseUrl()

        currentUrlText.text = if (customUrl != null) {
            "Current: $currentUrl (custom)"
        } else {
            "Current: $currentUrl (default)"
        }

        baseUrlEditText.setText(customUrl ?: "")
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finishAffinity()
    }
}

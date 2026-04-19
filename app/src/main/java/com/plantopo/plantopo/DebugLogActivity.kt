package com.plantopo.plantopo

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DebugLogActivity : AppCompatActivity() {
    private lateinit var logTextView: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var clearButton: Button
    private lateinit var refreshButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_log)

        logTextView = findViewById(R.id.log_text_view)
        scrollView = findViewById(R.id.log_scroll_view)
        clearButton = findViewById(R.id.clear_button)
        refreshButton = findViewById(R.id.refresh_button)

        clearButton.setOnClickListener {
            DebugLogBuffer.clear()
            refreshLogs()
        }

        refreshButton.setOnClickListener {
            refreshLogs()
        }

        refreshLogs()
    }

    private fun refreshLogs() {
        val logs = DebugLogBuffer.getLogs()
        if (logs.isEmpty()) {
            logTextView.text = "No logs available"
        } else {
            logTextView.text = logs.joinToString("\n") { it.format() }

            // Scroll to bottom to show most recent logs
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }
}

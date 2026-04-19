package com.plantopo.plantopo

import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

object DebugLogBuffer {
    private const val MAX_LOGS = 500
    private val logs = CopyOnWriteArrayList<LogEntry>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    data class LogEntry(
        val timestamp: Long,
        val priority: Int,
        val tag: String?,
        val message: String
    ) {
        fun format(): String {
            val time = dateFormat.format(Date(timestamp))
            val level = when (priority) {
                2 -> "V"
                3 -> "D"
                4 -> "I"
                5 -> "W"
                6 -> "E"
                7 -> "A"
                else -> "?"
            }
            val tagStr = tag?.let { "[$it] " } ?: ""
            return "$time $level $tagStr$message"
        }
    }

    fun addLog(priority: Int, tag: String?, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), priority, tag, message)
        logs.add(entry)

        // Keep only the most recent MAX_LOGS entries
        while (logs.size > MAX_LOGS) {
            logs.removeAt(0)
        }
    }

    fun getLogs(): List<LogEntry> = logs.toList()

    fun clear() {
        logs.clear()
    }

    class BufferingTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            val fullMessage = if (t != null) {
                "$message\n${t.stackTraceToString()}"
            } else {
                message
            }
            addLog(priority, tag, fullMessage)
        }
    }
}
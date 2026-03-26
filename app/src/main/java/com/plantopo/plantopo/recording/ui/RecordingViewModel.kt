package com.plantopo.plantopo.recording.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantopo.plantopo.recording.data.model.Recording
import com.plantopo.plantopo.recording.data.repository.RecordingRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

class RecordingViewModel(
    private val repository: RecordingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecordingUiState>(RecordingUiState.Idle)
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private val _currentRecording = MutableStateFlow<Recording?>(null)
    val currentRecording: StateFlow<Recording?> = _currentRecording.asStateFlow()

    private var recordingObserverJob: Job? = null

    init {
        checkForActiveRecording()
    }

    private fun checkForActiveRecording() {
        viewModelScope.launch {
            val activeRecording = repository.getActiveRecordingWithPoints()
            _currentRecording.value = activeRecording
            if (activeRecording != null) {
                _uiState.value = RecordingUiState.Active(activeRecording)
                observeRecording(activeRecording.id)
            }
        }
    }

    private fun observeRecording(recordingId: String) {
        recordingObserverJob?.cancel()
        recordingObserverJob = viewModelScope.launch {
            combine(
                repository.observeRecording(recordingId),
                repository.observeTrackPoints(recordingId)
            ) { recording, points ->
                recording?.let { Recording(it, points) }
            }.collect { recordingWithPoints ->
                _currentRecording.value = recordingWithPoints
                Timber.d("Recording updated: ${recordingWithPoints?.meta?.pointCount} points, ${recordingWithPoints?.points?.size} track points")
            }
        }
    }

    fun startRecording(name: String? = null) {
        viewModelScope.launch {
            try {
                _uiState.value = RecordingUiState.Starting
                val recordingId = repository.startRecording(name)
                val recording = repository.getActiveRecordingWithPoints()
                _currentRecording.value = recording
                _uiState.value = RecordingUiState.Active(recording!!)
                observeRecording(recordingId)
                Timber.d("Recording started: $recordingId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start recording")
                _uiState.value = RecordingUiState.Error(e.message ?: "Failed to start recording")
            }
        }
    }

    fun stopRecording() {
        val recording = _currentRecording.value ?: return
        viewModelScope.launch {
            try {
                _uiState.value = RecordingUiState.Stopping
                repository.stopRecording(recording.id)
                recordingObserverJob?.cancel()
                recordingObserverJob = null
                _currentRecording.value = null
                _uiState.value = RecordingUiState.Stopped(recording.id)
                Timber.d("Recording stopped: ${recording.id}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop recording")
                _uiState.value = RecordingUiState.Error(e.message ?: "Failed to stop recording")
            }
        }
    }

    fun acknowledgeState() {
        _uiState.value = RecordingUiState.Idle
    }
}

sealed class RecordingUiState {
    data object Idle : RecordingUiState()
    data object Starting : RecordingUiState()
    data class Active(val recording: Recording) : RecordingUiState()
    data object Stopping : RecordingUiState()
    data class Stopped(val recordingId: String) : RecordingUiState()
    data class Error(val message: String) : RecordingUiState()
}

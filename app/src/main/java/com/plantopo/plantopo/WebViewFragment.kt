package com.plantopo.plantopo

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.plantopo.plantopo.recording.data.db.RecordingDatabase
import com.plantopo.plantopo.recording.data.repository.RecordingRepository
import com.plantopo.plantopo.recording.service.RecordingService
import com.plantopo.plantopo.recording.sync.RecordingSyncWorker
import com.plantopo.plantopo.recording.ui.RecordingUiState
import com.plantopo.plantopo.recording.ui.RecordingViewModel
import com.plantopo.plantopo.recording.util.PermissionHandler
import kotlinx.coroutines.launch
import timber.log.Timber

class WebViewFragment : Fragment() {
    private var webView: WebView? = null
    private lateinit var authManager: AuthManager
    private lateinit var oauthManager: OAuthManager

    private val recordingViewModel: RecordingViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = RecordingDatabase.getInstance(requireContext())
                val repository = RecordingRepository(
                    recordingDao = database.recordingDao(),
                    trackPointDao = database.trackPointDao(),
                    trpcClient = TrpcClient(authManager)
                )
                @Suppress("UNCHECKED_CAST")
                return RecordingViewModel(repository) as T
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Timber.i("All permissions granted, starting recording")
            recordingViewModel.startRecording()
        } else {
            Timber.w("Permissions denied")
            Toast.makeText(
                requireContext(),
                "Location permissions are required to record tracks",
                Toast.LENGTH_LONG
            ).show()
            pushRecordTrackState(isRecording = false, pointCount = 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager(requireContext())
        oauthManager = OAuthManager(requireContext())

        // Enable WebView debugging in debug builds
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // Listen for authentication completion from MainActivity
        parentFragmentManager.setFragmentResultListener("auth_completed", this) { _, _ ->
            Timber.i("Auth completed, refreshing fragment view")
            // Recreate the view to show authenticated WebView
            parentFragmentManager.beginTransaction()
                .detach(this)
                .commit()
            parentFragmentManager.beginTransaction()
                .attach(this)
                .commit()
        }

        // Observe recording state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                recordingViewModel.uiState.collect { state ->
                    handleRecordingState(state)
                }
            }
        }

        // Observe current recording for live updates
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                recordingViewModel.currentRecording.collect { recording ->
                    if (recording != null) {
                        pushRecordTrackState(isRecording = true, pointCount = recording.pointCount)
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // If token exchange is in progress, show loading view
        if (authManager.isExchangingToken) {
            Timber.i("Token exchange in progress, showing loading view")
            return inflater.inflate(R.layout.fragment_loading, container, false)
        }

        // Only create WebView if authenticated
        if (!authManager.isAuthenticated()) {
            Timber.i("Not authenticated, showing login view")
            // Show login screen with button
            val loginView = inflater.inflate(R.layout.fragment_login, container, false)
            loginView.findViewById<Button>(R.id.loginButton).setOnClickListener {
                oauthManager.launchOAuthFlow(this)
            }
            return loginView
        }

        // Inflate the fragment layout
        val view = inflater.inflate(R.layout.fragment_webview, container, false)
        val webviewContainer = view.findViewById<ViewGroup>(R.id.webview_container)

        webView = WebView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = "PlanTopoNative $userAgentString"
            }

            // Add JavaScript interface for Android-WebView communication
            addJavascriptInterface(
                WebAppInterface(this@WebViewFragment),
                "Native"
            )

            webViewClient = PlanTopoWebViewClient()

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    val level = when (consoleMessage.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
                        ConsoleMessage.MessageLevel.WARNING -> Log.WARN
                        ConsoleMessage.MessageLevel.DEBUG -> Log.DEBUG
                        else -> Log.INFO
                    }
                    Timber.tag("WebView").log(level, "console: ${consoleMessage.message()} [${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}]")

                    return true
                }
            }

            // Session cookies already set by AuthManager during OAuth callback
            // Just load the app directly
            Timber.i("Loading ${Config.BASE_URL}")
            loadUrl(Config.BASE_URL)
        }

        // Add WebView to container
        webviewContainer.addView(webView)

        Timber.d("Returning WebView container from onCreateView")
        return view
    }

    override fun onResume() {
        super.onResume()
        // If user just completed OAuth and we don't have a WebView yet, recreate the view
        if (authManager.isAuthenticated() && webView == null) {
            Timber.i("onResume: just completed oauth, no webview yet")
            // Trigger view recreation by replacing fragment
            parentFragmentManager.beginTransaction()
                .detach(this)
                .commit()
            parentFragmentManager.beginTransaction()
                .attach(this)
                .commit()
        }
        // Note: No need to refresh session on resume - better-auth automatically
        // refreshes the session when it's used, and it has a 1 year expiry
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webView?.destroy()
        webView = null
    }

    private fun doLogout() {
        Timber.i("Logging out")
        authManager.clearToken()

        // Recreate the view to show login screen
        parentFragmentManager.beginTransaction()
            .detach(this)
            .commit()
        parentFragmentManager.beginTransaction()
            .attach(this)
            .commit()
    }

    private fun startRecordingTrack() {
        Timber.i("Start recording track requested")
        if (PermissionHandler.hasAllRequiredPermissions(requireContext())) {
            recordingViewModel.startRecording()
        } else {
            Timber.i("Requesting permissions")
            permissionLauncher.launch(PermissionHandler.getRequiredPermissions())
        }
    }

    private fun stopRecordingTrack() {
        Timber.i("Stop recording track requested")
        recordingViewModel.stopRecording()
    }

    private fun onRecordTrackReady() {
        Timber.i("Record track ready callback from web")
        val recording = recordingViewModel.currentRecording.value
        pushRecordTrackState(
            isRecording = recording != null,
            pointCount = recording?.pointCount ?: 0
        )
    }

    private fun handleRecordingState(state: RecordingUiState) {
        when (state) {
            is RecordingUiState.Idle -> {
                pushRecordTrackState(isRecording = false, pointCount = 0)
            }
            is RecordingUiState.Starting -> {
                Timber.d("Recording starting...")
            }
            is RecordingUiState.Active -> {
                Timber.d("Recording started: ${state.recording.id}")
                startRecordingService(state.recording.id)
                pushRecordTrackState(
                    isRecording = true,
                    pointCount = state.recording.pointCount
                )
            }
            is RecordingUiState.Stopping -> {
                Timber.d("Recording stopping...")
            }
            is RecordingUiState.Stopped -> {
                Timber.d("Recording stopped: ${state.recordingId}")
                stopRecordingService()
                pushRecordTrackState(isRecording = false, pointCount = 0)
                // Trigger sync in background
                RecordingSyncWorker.enqueue(requireContext())
                recordingViewModel.acknowledgeState()
            }
            is RecordingUiState.Error -> {
                Timber.e("Recording error: ${state.message}")
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                pushRecordTrackState(isRecording = false, pointCount = 0)
                recordingViewModel.acknowledgeState()
            }
        }
    }

    private fun startRecordingService(recordingId: Long) {
        val intent = Intent(requireContext(), RecordingService::class.java).apply {
            action = RecordingService.ACTION_START_RECORDING
            putExtra(RecordingService.EXTRA_RECORDING_ID, recordingId)
        }
        ContextCompat.startForegroundService(requireContext(), intent)
    }

    private fun stopRecordingService() {
        val intent = Intent(requireContext(), RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP_RECORDING
        }
        requireContext().startService(intent)
    }

    private fun pushRecordTrackState(isRecording: Boolean, pointCount: Int) {
        webView?.let { wv ->
            val state = """"{\"isRecording\":${isRecording},\"pointCount\":${pointCount}}""""
            wv.post {
                wv.evaluateJavascript("window.onRecordTrackState?.(JSON.parse($state))", null)
            }
            Timber.d("Pushed record track state: isRecording=$isRecording, pointCount=$pointCount")
        }
    }

    @Suppress("unused")
    class WebAppInterface(
        private val fragment: WebViewFragment
    ) {
        @android.webkit.JavascriptInterface
        fun logout() {
            fragment.doLogout()
        }

        @android.webkit.JavascriptInterface
        fun startRecordingTrack() {
            fragment.startRecordingTrack()
        }

        @android.webkit.JavascriptInterface
        fun stopRecordingTrack() {
            fragment.stopRecordingTrack()
        }

        @android.webkit.JavascriptInterface
        fun recordTrackReady() {
            fragment.onRecordTrackReady()
        }
    }
}

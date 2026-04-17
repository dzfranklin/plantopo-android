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
import android.widget.TextView
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
import com.plantopo.plantopo.recording.data.model.Recording
import com.plantopo.plantopo.recording.data.repository.RecordingRepository
import com.plantopo.plantopo.recording.service.RecordingService
import com.plantopo.plantopo.recording.sync.RecordingSyncWorker
import com.plantopo.plantopo.recording.ui.RecordingUiState
import com.plantopo.plantopo.recording.ui.RecordingViewModel
import com.plantopo.plantopo.recording.util.PermissionHandler
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

class WebViewFragment : Fragment() {
    private var webView: WebView? = null
    private lateinit var authManager: AuthManager
    private lateinit var oauthManager: OAuthManager
    private var currentRecordingServiceId: String? = null
    private var webViewState: Bundle? = null
    private var isReauthenticating = false
    private var reauthView: View? = null
    private var skipStateRestore = false  // Flag to skip state restore after reauth

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
            pushRecordTrackState(null)
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

        // Configure CookieManager for better persistence
        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

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
                recordingViewModel.currentRecording.collect { recordingWithPoints ->
                    pushRecordTrackState(recordingWithPoints)
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
        // If re-authentication is in progress, show reauthorizing view
        if (isReauthenticating) {
            Timber.i("Re-authentication in progress, showing reauthorizing view")
            val view = inflater.inflate(R.layout.fragment_reauthorizing, container, false)
            reauthView = view
            return view
        }

        // If token exchange is in progress, show loading view
        if (authManager.isExchangingToken) {
            Timber.i("Token exchange in progress, showing loading view")
            return inflater.inflate(R.layout.fragment_loading, container, false)
        }

        // Only create WebView if authenticated
        if (!authManager.isAuthenticated()) {
            Timber.i("Not authenticated, showing login view")
            // Show login screen with buttons
            val loginView = inflater.inflate(R.layout.fragment_login, container, false)
            loginView.findViewById<Button>(R.id.signInButton).setOnClickListener {
                oauthManager.launchOAuthFlow(this, "/login")
            }
            loginView.findViewById<Button>(R.id.signUpButton).setOnClickListener {
                oauthManager.launchOAuthFlow(this, "/signup")
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
                databaseEnabled = true  // Enable database storage
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT  // Use cache when available
                userAgentString = "PlanTopoNative $userAgentString"
                setGeolocationEnabled(true)
            }

            // Add JavaScript interface for Android-WebView communication
            addJavascriptInterface(
                WebAppInterface(this@WebViewFragment),
                "Native"
            )

            webViewClient = PlanTopoWebViewClient()

            webChromeClient = object : WebChromeClient() {
                // Intercept geolocation permission requests and auto-grant them
                // This prevents double permission popups (native + WebView)
                override fun onGeolocationPermissionsShowPrompt(
                    origin: String,
                    callback: android.webkit.GeolocationPermissions.Callback
                ) {
                    // Auto-grant permission to the WebView without showing a popup
                    // The native app already handles location permissions
                    callback.invoke(origin, true, false)
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    val level = when (consoleMessage.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
                        ConsoleMessage.MessageLevel.WARNING -> Log.WARN
                        ConsoleMessage.MessageLevel.DEBUG -> Log.DEBUG
                        else -> Log.INFO
                    }
                    Timber.tag("WebView").log(
                        level,
                        "console: ${consoleMessage.message()} [${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}]"
                    )

                    return true
                }
            }

            // Session cookies already set by AuthManager during OAuth callback
            // Restore state if available, otherwise load the app directly
            // Skip restore after re-authentication to get fresh session
            if (webViewState != null && !skipStateRestore) {
                Timber.i("Restoring WebView state")
                restoreState(webViewState!!)
            } else {
                if (skipStateRestore) {
                    Timber.i("Skipping state restore after re-auth, loading fresh: ${Config.BASE_URL}")
                    skipStateRestore = false  // Reset flag
                } else {
                    Timber.i("Loading ${Config.BASE_URL}")
                }
                loadUrl(Config.BASE_URL)
            }
        }

        // Add WebView to container
        webviewContainer.addView(webView)

        Timber.d("Returning WebView container from onCreateView")
        return view
    }

    override fun onResume() {
        super.onResume()
        // If user just completed OAuth and we don't have a WebView yet, recreate the view
        // Don't do this if we're in the middle of reauthorizing
        if (authManager.isAuthenticated() && webView == null && !isReauthenticating) {
            Timber.i("onResume: just completed oauth, no webview yet")
            // Trigger view recreation by replacing fragment
            parentFragmentManager.beginTransaction()
                .detach(this)
                .commit()
            parentFragmentManager.beginTransaction()
                .attach(this)
                .commit()
        }

        // Check if there's an active recording and ensure service is running
        val currentRecording = recordingViewModel.currentRecording.value
        if (currentRecording != null && currentRecordingServiceId != currentRecording.id) {
            Timber.i("Found active recording ${currentRecording.id} on resume, restarting service")
            startRecordingService(currentRecording.id)
            currentRecordingServiceId = currentRecording.id
        }

        // Note: No need to refresh session on resume - better-auth automatically
        // refreshes the session when it's used, and it has a 1 year expiry
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView?.let {
            if (webViewState == null) {
                webViewState = Bundle()
            }
            it.saveState(webViewState!!)
            Timber.i("WebView state saved")
        }
    }

    override fun onDestroyView() {
        // Save state before destroying
        webView?.let {
            if (webViewState == null) {
                webViewState = Bundle()
            }
            it.saveState(webViewState!!)
            Timber.i("WebView state saved on destroy")
        }
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

    private fun onReportUnauthorized() {
        // JavaScript bridge calls come from JavaBridge thread, switch to main thread
        activity?.runOnUiThread {
            Timber.w("WebView reported unauthorized error")

            // Prevent concurrent re-authentication attempts
            if (isReauthenticating) {
                Timber.d("Re-authentication already in progress")
                return@runOnUiThread
            }

            // Check if we have a native token to use for re-auth
            if (!authManager.isAuthenticated()) {
                Timber.i("No native token available, logging out")
                doLogout()
                return@runOnUiThread
            }

            // Set flag - frontend is in unusable state, we must respond
            Timber.i("Session desync detected, starting re-authentication")
            isReauthenticating = true

            // Stop the WebView to prevent errors during teardown
            webView?.stopLoading()

            // Recreate fragment ONCE to show reauthorizing screen
            parentFragmentManager.beginTransaction()
                .detach(this)
                .commit()
            parentFragmentManager.beginTransaction()
                .attach(this)
                .commit()

            // Start re-authentication in background
            lifecycleScope.launch {
                attemptReauthentication()
            }
        }
    }

    private suspend fun attemptReauthentication() {
        Timber.i("Attempting re-authentication")
        showReauthError(null) // Clear any previous error

        val success = authManager.refreshWebViewSession()

        if (success) {
            Timber.i("Re-authentication successful")
            // Clear flag and set skip restore flag for fresh load
            isReauthenticating = false
            skipStateRestore = true
            // Recreate fragment to show WebView
            parentFragmentManager.beginTransaction()
                .detach(this)
                .commit()
            parentFragmentManager.beginTransaction()
                .attach(this)
                .commit()
        } else {
            Timber.e("Re-authentication failed")
            if (!authManager.isAuthenticated()) {
                // Token was invalid/expired, show login
                isReauthenticating = false
                doLogout()
            } else {
                // Network error or endpoint not ready - show error and retry
                Timber.w("Re-auth failed but token still valid - will retry")
                showReauthError("Connection error, retrying...")

                // Wait a bit and retry automatically
                kotlinx.coroutines.delay(3000)
                attemptReauthentication()
            }
        }
    }

    private fun showReauthError(message: String?) {
        reauthView?.let { view ->
            val errorText = view.findViewById<TextView>(R.id.errorText)
            if (message != null) {
                errorText?.text = message
                errorText?.visibility = View.VISIBLE
            } else {
                errorText?.visibility = View.GONE
            }
        }
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
        val recordingWithPoints = recordingViewModel.currentRecording.value
        pushRecordTrackState(recordingWithPoints)
    }

    private fun handleRecordingState(state: RecordingUiState) {
        when (state) {
            is RecordingUiState.Idle -> {
                pushRecordTrackState(null)
            }

            is RecordingUiState.Starting -> {
                Timber.d("Recording starting...")
            }

            is RecordingUiState.Active -> {
                Timber.d("Recording started: ${state.recording.id}")
                // Only start service if not already started for this recording
                if (currentRecordingServiceId != state.recording.id) {
                    startRecordingService(state.recording.id)
                    currentRecordingServiceId = state.recording.id
                }
                // Note: live updates come from currentRecordingWithPoints flow
            }

            is RecordingUiState.Stopping -> {
                Timber.d("Recording stopping...")
            }

            is RecordingUiState.Stopped -> {
                Timber.d("Recording stopped: ${state.recordingId}")
                stopRecordingService()
                currentRecordingServiceId = null
                pushRecordTrackState(null)
                // Trigger sync in background
                RecordingSyncWorker.enqueue(requireContext())
                recordingViewModel.acknowledgeState()
            }

            is RecordingUiState.Error -> {
                Timber.e("Recording error: ${state.message}")
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                pushRecordTrackState(null)
                recordingViewModel.acknowledgeState()
            }
        }
    }

    private fun startRecordingService(recordingId: String) {
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

    private fun pushRecordTrackState(recording: Recording?) {
        val wv = webView;
        if (wv == null) return;

        val state = RecordTrackState(recording)
        val stateJson = Json.encodeToString(Json.encodeToString(state))

        wv.post {
            wv.evaluateJavascript("window.onRecordTrackState?.(JSON.parse($stateJson))", null)
        }

        Timber.d("Pushed record track state: ${recording?.points?.size} track points")
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

        @android.webkit.JavascriptInterface
        fun reportUnauthorized() {
            fragment.onReportUnauthorized()
        }
    }

    @Serializable
    private data class RecordTrackState(val recording: Recording?)
}

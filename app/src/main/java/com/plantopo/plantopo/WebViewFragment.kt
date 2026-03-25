package com.plantopo.plantopo

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import timber.log.Timber

class WebViewFragment : Fragment() {
    private var webView: WebView? = null
    private lateinit var authManager: AuthManager
    private lateinit var oauthManager: OAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager(requireContext())
        oauthManager = OAuthManager(requireContext())

        // Enable WebView debugging in debug builds
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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
                databaseEnabled = true
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

    private fun startRecording() {
        findNavController().navigate(R.id.action_webViewFragment_to_recordingFragment)
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

    @Suppress("unused")
    class WebAppInterface(
        private val fragment: WebViewFragment
    ) {
        @android.webkit.JavascriptInterface
        fun startRecording() {
            fragment.startRecording()
        }

        @android.webkit.JavascriptInterface
        fun logout() {
            fragment.doLogout()
        }
    }
}

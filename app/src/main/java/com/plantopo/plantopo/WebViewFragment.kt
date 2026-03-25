package com.plantopo.plantopo

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import timber.log.Timber
import java.net.URLEncoder

class WebViewFragment : Fragment() {
    private var webView: WebView? = null
    private lateinit var authManager: AuthManager
    private lateinit var oauthManager: OAuthManager
    private var lastKnownToken: String? = null

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
                WebAppInterface { startRecording() },
                "Android"
            )

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString()
                    Timber.tag("WebView").d("shouldOverrideUrlLoading: $url")

                    // Only allow navigation within BASE_URL
                    if (url?.startsWith(Config.BASE_URL) == false) {
                        Timber.tag("WebView").w("Blocked navigation to external URL: $url")
                        return true  // Block navigation
                    }

                    return false  // Allow navigation
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Timber.tag("WebView").d("Page started loading: $url")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Timber.tag("WebView").d("Page finished loading: $url")

                    // Sync session token from JavaScript context to native storage
                    syncSessionToken()
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    Timber.tag("WebView").e("WebView error: ${error?.description} (${error?.errorCode}) for ${request?.url}")
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                    errorResponse: android.webkit.WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    Timber.tag("WebView").e("HTTP error: ${errorResponse?.statusCode} for ${request?.url}")
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    val level = when (consoleMessage.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
                        ConsoleMessage.MessageLevel.WARNING -> Log.WARN
                        ConsoleMessage.MessageLevel.DEBUG -> Log.DEBUG
                        else -> Log.INFO
                    }
                    Timber.tag("WebView").log(level, "${consoleMessage.message()} [${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}]")

                    return true
                }
            }

            // Complete native login by POSTing token, then server redirects to /
            val token = authManager.getToken()
            if (token != null) {
                completeNativeLogin(token)
            } else {
                loadUrl(Config.BASE_URL)
            }
        }

        return webView!!
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webView?.destroy()
        webView = null
    }

    private fun completeNativeLogin(token: String) {
        Timber.i("Completing native login")
        // POST token to server as form-encoded data, which sets cookie and redirects to /
        val encodedToken = URLEncoder.encode(token, "UTF-8")
        val postData = "token=$encodedToken".toByteArray()
        webView?.postUrl("${Config.BASE_URL}/api/v1/complete-native-login", postData)
    }

    private fun syncSessionToken() {
        // Read __INITIAL__SESSION__?.session?.token from JavaScript context
        // This captures both auth and deauth (when token becomes null/undefined)
        val js = """
            (function() {
                return window.__INITIAL__SESSION__?.session?.token;
            })();
        """.trimIndent()

        webView?.evaluateJavascript(js) { result ->
            // result is "null", "undefined", or a quoted string like "\"token_value\""
            val token = when {
                result == null || result == "null" || result == "undefined" -> null
                result.startsWith("\"") && result.endsWith("\"") -> {
                    // Remove quotes and unescape
                    result.substring(1, result.length - 1)
                }
                else -> null
            }

            if (token != lastKnownToken) {
                lastKnownToken = token
                if (token != null) {
                    authManager.saveToken(token)
                } else {
                    // Token is null/undefined, user is deauthenticated
                    authManager.clearToken()
                }
            }
        }
    }

    private fun startRecording() {
        findNavController().navigate(R.id.action_webViewFragment_to_recordingFragment)
    }

    class WebAppInterface(
        private val onStartRecording: () -> Unit
    ) {
        @android.webkit.JavascriptInterface
        fun startRecording() {
            onStartRecording()
        }
    }
}

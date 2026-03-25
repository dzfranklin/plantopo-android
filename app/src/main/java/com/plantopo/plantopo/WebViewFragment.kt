package com.plantopo.plantopo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class WebViewFragment : Fragment() {
    private var webView: WebView? = null
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager(requireContext())

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
        webView = WebView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                userAgentString = "PlanTopoAndroid $userAgentString"
            }

            // Add JavaScript interface for Android-WebView communication
            addJavascriptInterface(
                WebAppInterface { startRecording() },
                "Android"
            )

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    // Intercept the login completion redirect
                    if (request.url.path == "/android-complete-login") {
                        syncTokenFromCookies()
                        // Navigate back to home after successful login
                        view.loadUrl(Config.BASE_URL)
                        return true
                    }
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Sync token after each page load in case it was refreshed
                    syncTokenFromCookies()
                }
            }

            // Load initial URL
            if (authManager.isAuthenticated()) {
                // Already authenticated, load the main app
                loadUrl(Config.BASE_URL)
            } else {
                // Not authenticated, start login flow
                loadUrl("${Config.BASE_URL}/login?returnTo=/android-complete-login")
            }
        }

        return webView!!
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webView?.destroy()
        webView = null
    }

    private fun syncTokenFromCookies() {
        val cookies = CookieManager.getInstance().getCookie(Config.BASE_URL)
        val token = cookies?.split(";")
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("better-auth.session_token=") }
            ?.removePrefix("better-auth.session_token=")

        if (token != null && token != authManager.getToken()) {
            authManager.saveToken(token)
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

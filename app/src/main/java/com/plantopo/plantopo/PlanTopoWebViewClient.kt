package com.plantopo.plantopo

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import timber.log.Timber

class PlanTopoWebViewClient(
    private val spaAssetManager: SpaAssetManager
) : WebViewClient() {

    private val externalLinkHandler = ExternalLinkHandler()

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        val url = request?.url?.toString()
        Timber.tag("WebView").d("shouldOverrideUrlLoading: $url")

        // Allow navigation within BASE_URL
        if (url?.startsWith(Config.BASE_URL) == true) {
            return false  // Allow navigation in WebView
        }

        // Handle external URLs
        if (url != null) {
            val context = view?.context
            if (context != null) {
                val handled = externalLinkHandler.handleUrl(context, url)
                if (!handled) {
                    Timber.tag("WebView").w("Could not handle external URL: $url")
                }
            } else {
                Timber.tag("WebView").w("No context available to handle external URL: $url")
            }
        }

        return true  // Block navigation in WebView
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        Timber.tag("WebView").d("Page started loading: $url")
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        Timber.tag("WebView").d("Page finished loading: $url")
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        Timber.tag("WebView")
            .e("WebView error: ${error?.description} (${error?.errorCode}) for ${request?.url}")
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        Timber.tag("WebView").e("HTTP error: ${errorResponse?.statusCode} for ${request?.url}")
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString() ?: return null

        // Check if we should use bundled assets
        val debugSettings = view?.context?.let { DebugSettings.getInstance(it) }
        if (debugSettings?.getUseBundledSpa() == false) {
            Timber.tag("WebView").d("Bundled SPA disabled, loading from network: $url")
            return null
        }

        // Only intercept requests to our BASE_URL
        if (!url.startsWith(Config.BASE_URL)) {
            return null
        }

        // Extract path from URL
        val path = url.substring(Config.BASE_URL.length).trimStart('/')

        if (path.startsWith("api/")) {
            return null
        }

        // dev server routes
        if (path.startsWith("src/") or path.startsWith("@fs/") or path.startsWith("node_modules/")) {
            return null
        }

        // Try to serve from bundled assets
        val response = spaAssetManager.serveAsset(path)
        if (response != null) {
            Timber.tag("WebView").d("Serving from bundled assets: $path")
            return response
        }

        // If not found in assets, let it load from network
        Timber.tag("WebView").d("Asset not found, loading from network: $path")
        return null
    }
}

package com.plantopo.plantopo

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import timber.log.Timber

class PlanTopoWebViewClient : WebViewClient() {

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
        Timber.tag("WebView").e("WebView error: ${error?.description} (${error?.errorCode}) for ${request?.url}")
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        Timber.tag("WebView").e("HTTP error: ${errorResponse?.statusCode} for ${request?.url}")
    }
}

package com.plantopo.plantopo

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import timber.log.Timber

class PlanTopoWebViewClient : WebViewClient() {

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

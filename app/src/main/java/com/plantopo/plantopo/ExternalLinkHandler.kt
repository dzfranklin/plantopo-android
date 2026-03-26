package com.plantopo.plantopo

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import timber.log.Timber

class ExternalLinkHandler {

    fun handleUrl(context: Context, url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            Timber.tag("ExternalLink").d("Handling URL: $url")

            when (uri.scheme?.lowercase()) {
                "http", "https" -> {
                    Timber.tag("ExternalLink").d("Detected HTTP/HTTPS scheme")
                    launchInCustomTabs(context, url)
                }
                "tel", "mailto", "sms", "geo" -> {
                    Timber.tag("ExternalLink").d("Detected special scheme: ${uri.scheme}")
                    handleSpecialScheme(context, uri)
                }
                else -> {
                    Timber.tag("ExternalLink").w("Unknown URL scheme: ${uri.scheme}")
                    false
                }
            }
        } catch (e: Exception) {
            Timber.tag("ExternalLink").e(e, "Error handling URL: $url")
            false
        }
    }

    private fun launchInCustomTabs(context: Context, url: String): Boolean {
        return try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()

            customTabsIntent.launchUrl(context, Uri.parse(url))
            Timber.tag("ExternalLink").i("Launched in Chrome Custom Tabs: $url")
            true
        } catch (e: ActivityNotFoundException) {
            Timber.tag("ExternalLink").w("No browser available to handle URL: $url")
            false
        } catch (e: Exception) {
            Timber.tag("ExternalLink").e(e, "Error launching Custom Tabs for URL: $url")
            false
        }
    }

    private fun handleSpecialScheme(context: Context, uri: Uri): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
            Timber.tag("ExternalLink").i("Launched intent for scheme: ${uri.scheme}")
            true
        } catch (e: ActivityNotFoundException) {
            Timber.tag("ExternalLink").w("No app available to handle scheme: ${uri.scheme}")
            false
        } catch (e: Exception) {
            Timber.tag("ExternalLink").e(e, "Error handling special scheme: ${uri.scheme}")
            false
        }
    }
}

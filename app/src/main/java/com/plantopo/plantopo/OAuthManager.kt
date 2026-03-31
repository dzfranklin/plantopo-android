package com.plantopo.plantopo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.Fragment

class OAuthManager(private val context: Context) {

    fun launchOAuthFlow(fragment: Fragment, path: String = "/login") {
        val callbackUri = "${BuildConfig.OAUTH_SCHEME}://$OAUTH_CALLBACK_HOST"
        val loginUrl = "${Config.BASE_URL}${path}?returnTo=$callbackUri"

        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()

        customTabsIntent.launchUrl(context, Uri.parse(loginUrl))
    }

    companion object {
        const val OAUTH_CALLBACK_HOST = "oauth-callback"

        fun getCallbackUri(): Uri {
            return Uri.parse("${BuildConfig.OAUTH_SCHEME}://$OAUTH_CALLBACK_HOST")
        }
    }
}

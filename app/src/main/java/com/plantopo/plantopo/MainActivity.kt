package com.plantopo.plantopo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager(this)
        setContentView(R.layout.activity_main)

        handleOAuthCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthCallback(intent)
    }

    private fun handleOAuthCallback(intent: Intent?) {
        intent?.data?.let { uri ->
            if (uri.scheme == OAuthManager.OAUTH_CALLBACK_SCHEME &&
                uri.host == OAuthManager.OAUTH_CALLBACK_HOST
            ) {
                // Extract token from query parameters
                val token = uri.getQueryParameter("token")
                if (token != null) {
                    authManager.saveToken(token)
                }
                // The navigation component will automatically show WebViewFragment
                // which will now load the authenticated app
            }
        }
    }
}
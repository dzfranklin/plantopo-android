package com.plantopo.plantopo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import timber.log.Timber

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
                // Extract short-lived initiation token from query parameters (15 min expiry)
                val initiationToken = uri.getQueryParameter("token")
                if (initiationToken != null) {
                    // Exchange immediately for API token and set session cookies
                    authManager.exchangeInitiationToken(initiationToken) { success ->
                        runOnUiThread {
                            if (!success) {
                                Timber.tag("MainActivity").e("Failed to exchange initiation token")
                                Toast.makeText(
                                    this,
                                    "Login failed. Please try again.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            // The navigation component will automatically show WebViewFragment
                            // which will now load the authenticated app with cookies already set
                        }
                    }
                }
            }
        }
    }
}
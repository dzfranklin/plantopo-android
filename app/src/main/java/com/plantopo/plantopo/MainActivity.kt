package com.plantopo.plantopo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
                    lifecycleScope.launch {
                        val success = authManager.exchangeInitiationToken(initiationToken)

                        // Notify WebViewFragment that auth exchange completed (success or failure)
                        // WebViewFragment is inside NavHostFragment, so we need to use the
                        // NavHostFragment's childFragmentManager
                        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                        Timber.tag("MainActivity").i("Sending auth_completed fragment result (success=$success)")
                        navHostFragment?.childFragmentManager?.setFragmentResult(
                            "auth_completed",
                            android.os.Bundle()
                        )

                        if (!success) {
                            Timber.tag("MainActivity").e("Failed to exchange initiation token")
                            Toast.makeText(
                                this@MainActivity,
                                "Login failed. Please try again.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }
}
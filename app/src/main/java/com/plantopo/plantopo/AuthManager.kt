package com.plantopo.plantopo

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import timber.log.Timber
import androidx.core.content.edit

class AuthManager(private val context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) {
        Timber.i("Saving authentication token")
        sharedPreferences.edit {
            putString(KEY_SESSION_TOKEN, token)
        }
    }

    fun getToken(): String? {
        val token = sharedPreferences.getString(KEY_SESSION_TOKEN, null)
        return token
    }

    fun clearToken() {
        Timber.i("Clearing authentication token")
        sharedPreferences.edit()
            .remove(KEY_SESSION_TOKEN)
            .apply()
    }

    fun isAuthenticated(): Boolean {
        val authenticated = getToken() != null
        return authenticated
    }

    // Exchanges a short-lived OAuth initiation token (15 min expiry) for a long-lived
    // API token and sets up the WebView session cookies.
    // Must be called immediately when receiving the initiation token from OAuth callback.
    fun exchangeInitiationToken(initiationToken: String, onComplete: (Boolean) -> Unit) {
        Timber.i("Exchanging initiation token for API token")

        Thread {
            try {
                val url = java.net.URL("${Config.BASE_URL}/api/v1/native-session")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $initiationToken")
                connection.setRequestProperty("Content-Length", "0")
                connection.instanceFollowRedirects = false

                val responseCode = connection.responseCode
                Timber.i("Token exchange response: $responseCode")

                if (responseCode in 200..299) {
                    // Extract API token from response body
                    val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseBody)
                    val apiToken = json.getString("token")

                    // Store the API token (not the initiation token)
                    saveToken(apiToken)
                    Timber.i("API token stored successfully")

                    // Extract and store session cookies for WebView
                    val cookies = connection.headerFields["Set-Cookie"]
                    if (cookies != null) {
                        val cookieManager = android.webkit.CookieManager.getInstance()
                        cookies.forEach { cookie ->
                            cookieManager.setCookie(Config.BASE_URL, cookie)
                            Timber.d("Set cookie: ${cookie.take(50)}...")
                        }
                        cookieManager.flush()
                        Timber.i("Session cookies set successfully")
                    }

                    onComplete(true)
                } else {
                    Timber.e("Token exchange failed: $responseCode")
                    val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Timber.e("Error response: $errorBody")
                    onComplete(false)
                }

                connection.disconnect()
            } catch (e: Exception) {
                Timber.e(e, "Error exchanging initiation token")
                onComplete(false)
            }
        }.start()
    }

    companion object {
        private const val KEY_SESSION_TOKEN = "session_token"
    }
}
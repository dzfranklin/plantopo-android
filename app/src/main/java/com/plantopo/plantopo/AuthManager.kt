package com.plantopo.plantopo

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

/**
 * Provides access to the current session data.
 */
interface UserProvider {
    fun getUser(): JsonObject?
}

/**
 * Native session response containing token and user data.
 */
@Serializable
data class NativeSessionResponse(
    val token: String,
    val user: JsonObject
)

class AuthManager(
    private val context: Context,
    private val baseUrl: String = Config.BASE_URL,
    httpClient: OkHttpClient? = null
) : TokenProvider, UserProvider {
    private val sharedPreferences = context.getSharedPreferences(
        "auth_prefs",
        Context.MODE_PRIVATE
    )

    private val httpClient = httpClient ?: OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    override fun getUser(): JsonObject? {
        val userJson = sharedPreferences.getString(KEY_USER, null)
        return if (userJson != null) {
            json.decodeFromString(JsonObject.serializer(), userJson)
        } else {
            null
        }
    }

    override fun getToken(): String? {
        val token = sharedPreferences.getString(KEY_SESSION_TOKEN, null)
        return token
    }

    fun logout() {
        Timber.i("Clearing authentication data")
        sharedPreferences.edit {
            remove(KEY_SESSION_TOKEN)
            remove(KEY_USER)
        }
    }

    fun isAuthenticated(): Boolean {
        val authenticated = getToken() != null
        return authenticated
    }

    @Volatile
    private var _isExchangingToken = false

    val isExchangingToken: Boolean
        get() = _isExchangingToken

    // Exchanges a short-lived OAuth initiation token (15 min expiry) for a long-lived
    // API token and sets up the WebView session cookies.
    // Must be called immediately when receiving the initiation token from OAuth callback.
    suspend fun exchangeInitiationToken(initiationToken: String): Boolean {
        Timber.i("Exchanging initiation token for API token")
        _isExchangingToken = true

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/v1/native-session")
                    .post(ByteArray(0).toRequestBody())
                    .addHeader("Authorization", "Bearer $initiationToken")
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body.string()

                if (response.isSuccessful) {
                    val sessionResponse = json.decodeFromString<NativeSessionResponse>(responseBody)
                    updateSessionFromResponse(sessionResponse)

                    // Extract and store session cookies for WebView
                    val cookies = response.headers("Set-Cookie")
                    if (cookies.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            val cookieManager = android.webkit.CookieManager.getInstance()
                            cookies.forEachIndexed { index, cookie ->
                                cookieManager.setCookie(baseUrl, cookie) { success ->
                                    Timber.d(
                                        "Set cookie ${index + 1}/${cookies.size}: ${
                                            cookie.take(
                                                50
                                            )
                                        }... - Success: $success"
                                    )
                                }
                            }
                            // Force write to disk after all cookies are set
                            cookieManager.flush()
                            Timber.i("Session cookies set successfully")
                        }
                    }

                    _isExchangingToken = false
                    true
                } else {
                    Timber.e("Token exchange failed: ${response.code}")
                    Timber.e("Error response: $responseBody")
                    _isExchangingToken = false
                    false
                }
            } catch (e: Exception) {
                Timber.e(e, "Error exchanging initiation token")
                _isExchangingToken = false
                false
            }
        }
    }

    suspend fun refreshWebViewSession(): Boolean {
        val apiToken = getToken()
        if (apiToken == null) {
            Timber.e("Cannot refresh WebView session: no API token")
            return false
        }

        Timber.i("Refreshing WebView session cookies")

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/v1/refresh-native-session")
                    .post(ByteArray(0).toRequestBody())
                    .addHeader("Authorization", "Bearer $apiToken")
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body.string()

                if (response.isSuccessful) {
                    val sessionResponse =
                        json.decodeFromString<NativeSessionResponse>(responseBody)
                    updateSessionFromResponse(sessionResponse)

                    val cookies = response.headers("Set-Cookie")
                    if (cookies.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            val cookieManager = android.webkit.CookieManager.getInstance()
                            cookies.forEachIndexed { index, cookie ->
                                cookieManager.setCookie(baseUrl, cookie) { success ->
                                    Timber.d("Refresh: Set cookie ${index + 1}/${cookies.size}: Success=$success")
                                }
                            }
                            cookieManager.flush()
                        }
                        true
                    } else {
                        Timber.w("Refresh successful but no cookies returned")
                        false
                    }
                } else {
                    Timber.e("WebView session refresh failed: ${response.code}")
                    if (response.code == 401) {
                        Timber.e("API token appears expired")
                        logout()
                    }
                    false
                }
            } catch (e: Exception) {
                Timber.e(e, "Error refreshing WebView session")
                false
            }
        }
    }

    private fun updateSessionFromResponse(response: NativeSessionResponse) {
        sharedPreferences.edit {
            putString(KEY_SESSION_TOKEN, response.token)
            putString(KEY_USER, json.encodeToString(JsonObject.serializer(), response.user))
        }
        Timber.i("Updated session")
    }

    companion object {
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_USER = "user"
    }
}
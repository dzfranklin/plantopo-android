package com.plantopo.plantopo

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber

class AuthManager(context: Context) {
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
        sharedPreferences.edit()
            .putString(KEY_SESSION_TOKEN, token)
            .apply()
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

    companion object {
        private const val KEY_SESSION_TOKEN = "session_token"
    }
}
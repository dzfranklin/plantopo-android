package com.plantopo.plantopo

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AuthManagerTest {

    private lateinit var authManager: AuthManager
    private lateinit var mockWebServer: MockWebServer
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        authManager = AuthManager(context, baseUrl)
        authManager.clearToken()

        // Clear cookies
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    @After
    fun teardown() {
        authManager.clearToken()
        mockWebServer.shutdown()
    }

    @Test
    fun successfulTokenExchange_savesTokenAndCookies() = runTest {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"token": "api-token-123"}""")
                .addHeader("Set-Cookie", "better-auth.session_token=cookie123; Path=/; HttpOnly")
        )

        // When
        val success = authManager.exchangeInitiationToken("initiation-token")

        // Then
        assertThat(success).isTrue()
        assertThat(authManager.getToken()).isEqualTo("api-token-123")
        assertThat(authManager.isAuthenticated()).isTrue()

        // Verify request
        val request = mockWebServer.takeRequest()
        assertThat(request.path).isEqualTo("/api/v1/native-session")
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer initiation-token")

        // Verify cookie was set
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(mockWebServer.url("/").toString())
        assertThat(cookies).contains("better-auth.session_token=cookie123")
    }

    @Test
    fun failedTokenExchange_doesNotSaveToken() = runTest {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error": "Unauthorized"}""")
        )

        // When
        val success = authManager.exchangeInitiationToken("invalid-token")

        // Then
        assertThat(success).isFalse()
        assertThat(authManager.getToken()).isNull()
        assertThat(authManager.isAuthenticated()).isFalse()
    }

    @Test
    fun networkError_returnsFalse() = runTest {
        // Given
        mockWebServer.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        )

        // When
        val success = authManager.exchangeInitiationToken("token")

        // Then
        assertThat(success).isFalse()
        assertThat(authManager.getToken()).isNull()
    }

    @Test
    fun malformedResponse_returnsFalse() = runTest {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("not valid json {{{")
        )

        // When
        val success = authManager.exchangeInitiationToken("token")

        // Then
        assertThat(success).isFalse()
        assertThat(authManager.getToken()).isNull()
    }

    @Test
    fun missingTokenInResponse_returnsFalse() = runTest {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"wrong_field": "value"}""")
        )

        // When
        val success = authManager.exchangeInitiationToken("token")

        // Then
        assertThat(success).isFalse()
        assertThat(authManager.getToken()).isNull()
    }

    @Test
    fun saveToken_persistsToken() {
        // Given
        val token = "test-token-123"

        // When
        authManager.saveToken(token)

        // Then
        assertThat(authManager.getToken()).isEqualTo(token)
        assertThat(authManager.isAuthenticated()).isTrue()
    }

    @Test
    fun clearToken_removesToken() {
        // Given
        authManager.saveToken("test-token")
        assertThat(authManager.isAuthenticated()).isTrue()

        // When
        authManager.clearToken()

        // Then
        assertThat(authManager.getToken()).isNull()
        assertThat(authManager.isAuthenticated()).isFalse()
    }

    @Test
    fun tokenPersistsAcrossInstances() {
        // Given
        val token = "persistent-token"
        authManager.saveToken(token)

        // When - Create new instance
        val newAuthManager = AuthManager(context)

        // Then
        assertThat(newAuthManager.getToken()).isEqualTo(token)
        assertThat(newAuthManager.isAuthenticated()).isTrue()
    }

    @Test
    fun isExchangingToken_flagSetDuringExchange() = runTest {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setBodyDelay(100, TimeUnit.MILLISECONDS)
                .setResponseCode(200)
                .setBody("""{"token": "api-token"}""")
        )

        // When - Before exchange
        assertThat(authManager.isExchangingToken).isFalse()

        // Start exchange (don't await immediately)
        val job = CoroutineScope(Dispatchers.IO).launch {
            authManager.exchangeInitiationToken("token")
        }

        // Check flag is set during exchange
        delay(10)
        assertThat(authManager.isExchangingToken).isTrue()

        // Wait for completion
        job.join()

        // Then - After exchange
        assertThat(authManager.isExchangingToken).isFalse()
    }

    @Test
    fun multipleSetCookies_allStored() = runTest {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"token": "api-token"}""")
                .addHeader("Set-Cookie", "cookie1=value1; Path=/")
                .addHeader("Set-Cookie", "cookie2=value2; Path=/")
        )

        // When
        val success = authManager.exchangeInitiationToken("token")

        // Then
        assertThat(success).isTrue()

        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(mockWebServer.url("/").toString())
        assertThat(cookies).contains("cookie1=value1")
        assertThat(cookies).contains("cookie2=value2")
    }

    @Test
    fun serverError500_returnsFalse() = runTest {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error": "Internal server error"}""")
        )

        // When
        val success = authManager.exchangeInitiationToken("token")

        // Then
        assertThat(success).isFalse()
        assertThat(authManager.getToken()).isNull()
    }

    @Test
    fun networkDisconnectDuringRequest_returnsFalse() = runTest {
        // Given
        mockWebServer.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_DURING_REQUEST_BODY)
        )

        // When
        val success = authManager.exchangeInitiationToken("token")

        // Then
        assertThat(success).isFalse()
        assertThat(authManager.getToken()).isNull()
    }
}

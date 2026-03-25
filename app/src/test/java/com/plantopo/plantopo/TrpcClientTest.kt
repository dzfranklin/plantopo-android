package com.plantopo.plantopo

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class TrpcClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var trpcClient: TrpcClient
    private lateinit var mockTokenProvider: MockTokenProvider

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        mockTokenProvider = MockTokenProvider()
        trpcClient = TrpcClient(
            tokenProvider = mockTokenProvider,
            httpClient = OkHttpClient.Builder()
                .callTimeout(2, TimeUnit.SECONDS)
                .build(),
            baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        )
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Serializable
    data class TestInput(val message: String)

    @Serializable
    data class TestOutput(val echo: String)

    @Test
    fun `successful query returns data`() = runTest {
        // Given
        mockTokenProvider.mockToken = "test-token"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"result": {"data": {"json": {"echo": "hello"}}}}""")
        )

        // When
        val result = trpcClient.query<TestInput, TestOutput>(
            "test.echo",
            TestInput("hello")
        )

        // Then
        assertThat(result.echo).isEqualTo("hello")

        // Verify request
        val request = mockWebServer.takeRequest()
        assertThat(request.path).isEqualTo("/api/v1/trpc/test.echo")
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-token")
        assertThat(request.body.readUtf8()).contains("\"message\":\"hello\"")
    }

    @Test
    fun `successful mutation returns data`() = runTest {
        // Given
        mockTokenProvider.mockToken = "test-token"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"result": {"data": {"json": {"echo": "world"}}}}""")
        )

        // When
        val result = trpcClient.mutation<TestInput, TestOutput>(
            "test.create",
            TestInput("world")
        )

        // Then
        assertThat(result.echo).isEqualTo("world")
    }

    @Test
    fun `handles tRPC error response`() = runTest {
        // Given
        mockTokenProvider.mockToken = "test-token"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"error": {"message": "Invalid input", "code": "BAD_REQUEST"}}""")
        )

        // When
        var exception: TrpcException? = null
        try {
            trpcClient.query<TestInput, TestOutput>(
                "test.echo",
                TestInput("hello")
            )
        } catch (e: TrpcException) {
            exception = e
        }

        // Then
        assertThat(exception).isNotNull()
        assertThat(exception?.message).isEqualTo("Invalid input")
        assertThat(exception?.code).isEqualTo("BAD_REQUEST")
    }

    @Test
    fun `handles unauthorized error`() = runTest {
        // Given
        mockTokenProvider.mockToken = "invalid-token"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error": {"message": "Unauthorized", "code": "UNAUTHORIZED"}}""")
        )

        // When
        var exception: TrpcException? = null
        try {
            trpcClient.query<TestInput, TestOutput>(
                "test.echo",
                TestInput("hello")
            )
        } catch (e: TrpcException) {
            exception = e
        }

        // Then
        assertThat(exception).isNotNull()
        assertThat(exception?.code).isEqualTo("UNAUTHORIZED")
    }

    @Test
    fun `handles network disconnect`() = runTest {
        // Given
        mockTokenProvider.mockToken = "test-token"
        mockWebServer.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        )

        // When
        var exception: TrpcException? = null
        try {
            trpcClient.query<TestInput, TestOutput>(
                "test.echo",
                TestInput("hello")
            )
        } catch (e: TrpcException) {
            exception = e
        }

        // Then
        assertThat(exception).isNotNull()
        assertThat(exception?.message).contains("Network error")
    }

    @Test
    fun `handles timeout`() = runTest {
        // Given
        mockTokenProvider.mockToken = "test-token"
        mockWebServer.enqueue(
            MockResponse()
                .setBodyDelay(5, TimeUnit.SECONDS)
                .setBody("""{"result": {"data": {"json": {"echo": "hello"}}}}""")
        )

        // When
        var exception: TrpcException? = null
        try {
            trpcClient.query<TestInput, TestOutput>(
                "test.echo",
                TestInput("hello")
            )
        } catch (e: TrpcException) {
            exception = e
        }

        // Then
        assertThat(exception).isNotNull()
        assertThat(exception?.message).contains("Network error")
    }

    @Test
    fun `handles malformed JSON response`() = runTest {
        // Given
        mockTokenProvider.mockToken = "test-token"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("not valid json {{{")
        )

        // When
        var exception: TrpcException? = null
        try {
            trpcClient.query<TestInput, TestOutput>(
                "test.echo",
                TestInput("hello")
            )
        } catch (e: TrpcException) {
            exception = e
        }

        // Then
        assertThat(exception).isNotNull()
        assertThat(exception?.message).contains("Serialization error")
    }

    @Test
    fun `handles missing result data in response`() = runTest {
        // Given
        mockTokenProvider.mockToken = "test-token"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"result": {}}""")
        )

        // When
        var exception: TrpcException? = null
        try {
            trpcClient.query<TestInput, TestOutput>(
                "test.echo",
                TestInput("hello")
            )
        } catch (e: TrpcException) {
            exception = e
        }

        // Then
        assertThat(exception).isNotNull()
        assertThat(exception?.message).contains("Serialization error")
    }

    @Test
    fun `handles 500 internal server error`() = runTest {
        // Given
        mockTokenProvider.mockToken = "test-token"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error": {"message": "Internal server error", "code": "INTERNAL_SERVER_ERROR"}}""")
        )

        // When
        var exception: TrpcException? = null
        try {
            trpcClient.query<TestInput, TestOutput>(
                "test.echo",
                TestInput("hello")
            )
        } catch (e: TrpcException) {
            exception = e
        }

        // Then
        assertThat(exception).isNotNull()
        assertThat(exception?.message).isEqualTo("Internal server error")
        assertThat(exception?.code).isEqualTo("INTERNAL_SERVER_ERROR")
    }

    @Test
    fun `handles network disconnect during token exchange`() = runTest {
        // Given
        mockTokenProvider.mockToken = "test-token"
        mockWebServer.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_DURING_REQUEST_BODY)
        )

        // When
        var exception: TrpcException? = null
        try {
            trpcClient.mutation<TestInput, TestOutput>(
                "test.create",
                TestInput("hello")
            )
        } catch (e: TrpcException) {
            exception = e
        }

        // Then
        assertThat(exception).isNotNull()
        assertThat(exception?.message).contains("Network error")
    }

    /**
     * Mock TokenProvider for testing
     */
    class MockTokenProvider : TokenProvider {
        var mockToken: String? = "test-token"

        override fun getToken(): String? = mockToken
    }
}

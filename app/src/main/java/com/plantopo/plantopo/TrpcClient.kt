package com.plantopo.plantopo

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException

/**
 * Interface for providing authentication tokens
 */
interface TokenProvider {
    fun getToken(): String?
}

/**
 * Simple tRPC client for making API calls.
 *
 * Usage:
 * ```
 * val client = TrpcClient(authManager)
 *
 * // For a query
 * val result = client.query<MyInput, MyOutput>("myProcedure", input)
 *
 * // For a mutation
 * val result = client.mutation<MyInput, MyOutput>("myProcedure", input)
 * ```
 */
class TrpcClient(
    private val tokenProvider: TokenProvider,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = Config.BASE_URL
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class TrpcRequest(val json: JsonElement)

    @Serializable
    private data class TrpcSuccessResponse(val result: TrpcResult)

    @Serializable
    private data class TrpcResult(val data: TrpcData)

    @Serializable
    private data class TrpcData(val json: JsonElement)

    @Serializable
    private data class TrpcErrorResponse(val error: TrpcError)

    @Serializable
    private data class TrpcError(
        val message: String,
        val code: String? = null,
        val data: JsonElement? = null
    )

    /**
     * Makes a tRPC query (read operation).
     *
     * @param procedure The procedure name (e.g., "user.getById")
     * @param input The input data (will be JSON-stringified)
     * @return The decoded output data
     * @throws TrpcException if the call fails
     */
    suspend fun <I, O> query(
        procedure: String,
        input: I,
        inputSerializer: kotlinx.serialization.KSerializer<I>,
        outputSerializer: kotlinx.serialization.KSerializer<O>
    ): O {
        return call(procedure, input, inputSerializer, outputSerializer)
    }

    /**
     * Makes a tRPC mutation (write operation).
     *
     * @param procedure The procedure name (e.g., "recording.create")
     * @param input The input data (will be JSON-stringified)
     * @return The decoded output data
     * @throws TrpcException if the call fails
     */
    suspend fun <I, O> mutation(
        procedure: String,
        input: I,
        inputSerializer: kotlinx.serialization.KSerializer<I>,
        outputSerializer: kotlinx.serialization.KSerializer<O>
    ): O {
        return call(procedure, input, inputSerializer, outputSerializer)
    }

    private suspend fun <I, O> call(
        procedure: String,
        input: I,
        inputSerializer: kotlinx.serialization.KSerializer<I>,
        outputSerializer: kotlinx.serialization.KSerializer<O>
    ): O {
        val token = tokenProvider.getToken()
            ?: throw TrpcException("Not authenticated")

        // Encode input
        val inputJson = json.encodeToJsonElement(inputSerializer, input)
        val requestBody = TrpcRequest(inputJson)
        val requestBodyJson = json.encodeToString(TrpcRequest.serializer(), requestBody)

        // Build request
        val url = "$baseUrl/api/v1/trpc/$procedure"
        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $token")
            .build()

        Timber.d("tRPC call: $procedure")

        // Execute request
        return try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: throw TrpcException("Empty response body")

            if (response.isSuccessful) {
                // Parse success response
                val successResponse = json.decodeFromString<TrpcSuccessResponse>(responseBody)
                json.decodeFromJsonElement(outputSerializer, successResponse.result.data.json)
            } else {
                // Parse error response
                try {
                    val errorResponse = json.decodeFromString<TrpcErrorResponse>(responseBody)
                    throw TrpcException(
                        message = errorResponse.error.message,
                        code = errorResponse.error.code
                    )
                } catch (e: SerializationException) {
                    throw TrpcException("HTTP ${response.code}: $responseBody")
                }
            }
        } catch (e: TrpcException) {
            Timber.e("tRPC error: ${e.message}")
            throw e
        } catch (e: IOException) {
            Timber.e(e, "Network error calling $procedure")
            throw TrpcException("Network error: ${e.message}", cause = e)
        } catch (e: SerializationException) {
            Timber.e(e, "Serialization error calling $procedure")
            throw TrpcException("Serialization error: ${e.message}", cause = e)
        }
    }
}

/**
 * Exception thrown by tRPC client.
 */
class TrpcException(
    message: String,
    val code: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)

// Extension functions for cleaner API with reified types

/**
 * Makes a tRPC query with automatic serializer inference.
 *
 * Usage:
 * ```
 * @Serializable
 * data class GetUserInput(val id: String)
 *
 * @Serializable
 * data class User(val id: String, val name: String)
 *
 * val user = client.query<GetUserInput, User>("user.getById", GetUserInput("123"))
 * ```
 */
suspend inline fun <reified I, reified O> TrpcClient.query(
    procedure: String,
    input: I
): O = query(
    procedure = procedure,
    input = input,
    inputSerializer = kotlinx.serialization.serializer(),
    outputSerializer = kotlinx.serialization.serializer()
)

/**
 * Makes a tRPC mutation with automatic serializer inference.
 *
 * Usage:
 * ```
 * @Serializable
 * data class CreateRecordingInput(val name: String)
 *
 * @Serializable
 * data class Recording(val id: String, val name: String)
 *
 * val recording = client.mutation<CreateRecordingInput, Recording>(
 *     "recording.create",
 *     CreateRecordingInput("My Recording")
 * )
 * ```
 */
suspend inline fun <reified I, reified O> TrpcClient.mutation(
    procedure: String,
    input: I
): O = mutation(
    procedure = procedure,
    input = input,
    inputSerializer = kotlinx.serialization.serializer(),
    outputSerializer = kotlinx.serialization.serializer()
)

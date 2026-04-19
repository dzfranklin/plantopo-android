package com.plantopo.plantopo

import android.content.Context
import android.content.SharedPreferences
import android.webkit.WebResourceResponse
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream

class SpaAssetManager private constructor(
    private val context: Context,
    private val userProvider: UserProvider
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private val spaDir: File
        get() = File(context.filesDir, "spa")

    private val currentVersionDir: File
        get() = File(spaDir, "current")

    private val nextVersionDir: File
        get() = File(spaDir, "next")

    private val downloadedAssetFile: File
        get() = File(spaDir, "downloaded.tar.gz")

    var updateAvailable: Boolean = false
        private set

    private var updateListener: ((Boolean) -> Unit)? = null

    fun setUpdateListener(listener: (Boolean) -> Unit) {
        updateListener = listener
    }

    private fun notifyUpdateAvailable(available: Boolean) {
        updateAvailable = available
        updateListener?.invoke(available)
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        Timber.i("Initializing SPA asset manager")

        // If there's a next version ready, swap it in
        if (nextVersionDir.exists()) {
            Timber.i("Found pending update, swapping to new version")
            if (currentVersionDir.exists()) {
                currentVersionDir.deleteRecursively()
            }
            nextVersionDir.renameTo(currentVersionDir)
            // Keep the ETag - it represents the version we just installed
            notifyUpdateAvailable(false)
        }

        // If current version doesn't exist, extract bundled assets
        if (!currentVersionDir.exists()) {
            Timber.i("No current version found, extracting bundled assets")
            extractBundledAssets()
        }

        Timber.i("SPA asset manager initialized")
    }

    suspend fun checkForUpdates() = withContext(Dispatchers.IO) {
        try {
            Timber.i("Checking for SPA updates")

            val assetsUrl = "${Config.BASE_URL}/native-assets.tar.gz"
            val lastEtag = prefs.getString(KEY_LAST_ETAG, null)
            val request = Request.Builder()
                .url(assetsUrl)
                .apply {
                    if (lastEtag != null) {
                        addHeader("If-None-Match", lastEtag)
                    }
                }
                .build()

            httpClient.newCall(request).execute().use { response ->
                when (response.code) {
                    304 -> {
                        Timber.i("SPA is up to date (304 Not Modified)")
                        notifyUpdateAvailable(false)
                    }

                    200 -> {
                        val newEtag = response.header("ETag")
                        if (newEtag != null && newEtag != lastEtag) {
                            Timber.i("New SPA version available (ETag: $newEtag)")

                            // Download to file
                            response.body.byteStream().use { input ->
                                downloadedAssetFile.parentFile?.mkdirs()
                                downloadedAssetFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }

                            // Extract to next version directory
                            extractTarGz(downloadedAssetFile, nextVersionDir)

                            // Clean up the downloaded archive
                            downloadedAssetFile.delete()

                            // Save new ETag
                            prefs.edit {
                                putString(KEY_LAST_ETAG, newEtag)
                            }

                            // Notify update available
                            notifyUpdateAvailable(true)

                            Timber.i("Downloaded and extracted new SPA version")
                        }
                    }

                    else -> {
                        Timber.w("Unexpected response code when checking for updates: ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking for SPA updates")
        }
    }

    private fun extractBundledAssets() {
        try {
            // File is named .bin to prevent Android build from auto-decompressing it
            context.assets.open("native-assets.bin").use { input ->
                extractTarGz(input, currentVersionDir)
            }
            Timber.i("Extracted bundled assets to ${currentVersionDir.absolutePath}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract bundled assets")
            throw e
        }
    }

    private fun extractTarGz(file: File, destDir: File) {
        FileInputStream(file).use { fis ->
            extractTarGz(fis, destDir)
        }
    }

    private fun extractTarGz(input: InputStream, destDir: File) {
        destDir.deleteRecursively()
        destDir.mkdirs()

        GZIPInputStream(input).use { gzipStream ->
            TarArchiveInputStream(gzipStream).use { tarStream ->
                var entry = tarStream.nextEntry
                while (entry != null) {
                    if (!tarStream.canReadEntryData(entry)) {
                        Timber.w("Cannot read entry: ${entry.name}")
                        entry = tarStream.nextEntry
                        continue
                    }

                    val file = File(destDir, entry.name)

                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        file.outputStream().use { output ->
                            tarStream.copyTo(output)
                        }
                    }

                    entry = tarStream.nextEntry
                }
            }
        }

        Timber.i("Extracted tar.gz to ${destDir.absolutePath}")
    }

    fun serveAsset(path: String): WebResourceResponse? {
        try {
            // Remove leading slash and query params
            val cleanPath = path.trimStart('/').split('?')[0]

            var file = File(currentVersionDir, cleanPath)

            // If file doesn't exist or is a directory, try index.html for SPA routing
            if (!file.exists() || file.isDirectory) {
                file = File(currentVersionDir, "index.html")
            }

            val mimeType = getMimeType(file.name)

            // Check if this is an HTML file that needs user injection
            val isHtml = file.name.endsWith(".html", ignoreCase = true)
            if (isHtml) {
                val user = userProvider.getUser()
                if (user == null) throw IllegalStateException("Expected user to inject")

                val htmlContent = file.readText(Charsets.UTF_8)
                val injectedHtml = injectUserIntoHtml(htmlContent, user)
                val inputStream = ByteArrayInputStream(injectedHtml.toByteArray(Charsets.UTF_8))
                return WebResourceResponse(mimeType, "UTF-8", inputStream)
            }

            val inputStream = FileInputStream(file)
            return WebResourceResponse(mimeType, "UTF-8", inputStream)
        } catch (e: Exception) {
            Timber.e(e, "Error serving asset: $path")
            return null
        }
    }

    private fun injectUserIntoHtml(
        htmlContent: String,
        user: kotlinx.serialization.json.JsonObject
    ): String {
        // Serialize user to JSON string
        val userJson = json.encodeToString(user)

        // Double-encode for JavaScript (same as server-side: JSON.stringify(JSON.stringify(...)))
        val escapedJson = json.encodeToString(userJson)

        // Create script tag
        val scriptTag = "<script>window.__INITIAL_USER__ = JSON.parse($escapedJson);</script>"

        // Inject before </head>
        if (htmlContent.contains("</head>", ignoreCase = true)) {
            return htmlContent.replace("</head>", "$scriptTag\n</head>", ignoreCase = true)
        } else {
            Timber.w("No </head> tag found in HTML, unable to inject user")
            return htmlContent
        }
    }

    private fun getMimeType(filename: String): String {
        return when {
            filename.endsWith(".html") -> "text/html"
            filename.endsWith(".js") -> "application/javascript"
            filename.endsWith(".css") -> "text/css"
            filename.endsWith(".json") -> "application/json"
            filename.endsWith(".png") -> "image/png"
            filename.endsWith(".jpg") || filename.endsWith(".jpeg") -> "image/jpeg"
            filename.endsWith(".svg") -> "image/svg+xml"
            filename.endsWith(".woff") -> "font/woff"
            filename.endsWith(".woff2") -> "font/woff2"
            filename.endsWith(".ttf") -> "font/ttf"
            filename.endsWith(".ico") -> "image/x-icon"
            else -> "application/octet-stream"
        }
    }

    companion object {
        private const val PREFS_NAME = "spa_assets"
        private const val KEY_LAST_ETAG = "last_etag"

        @Volatile
        private var instance: SpaAssetManager? = null

        fun getInstance(context: Context, userProvider: UserProvider): SpaAssetManager {
            return instance ?: synchronized(this) {
                instance ?: SpaAssetManager(
                    context.applicationContext,
                    userProvider
                ).also { instance = it }
            }
        }
    }
}

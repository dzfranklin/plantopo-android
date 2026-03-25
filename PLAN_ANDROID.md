# PlanTopo — Android App Plan

## Purpose

Native Kotlin app for GPS track recording. Planning features are delivered via
an embedded WebView that loads the existing web client. The app has a small,
deliberate native footprint.

## Repo

Standalone Gradle project, separate from the TypeScript monorepo (../plantopo).

## Architecture

- **Native UI (Jetpack Compose):** trip list, recording controls, sync status
- **WebView:** loads the web client for planning (route drawing, map editing)
- **Foreground service:** `FusedLocationProviderClient` for continuous GPS in
  the background, survives screen-off
- **Offline by default:** writes GPS points to local SQLite, syncs to API when
  connectivity returns
- **Auth:** bearer token stored in encrypted SharedPreferences, sent as
  `Authorization: Bearer $token` on all API calls

## API Integration

tRPC endpoints are plain HTTP — no special client library needed. Every call is:

```
POST $BASE_URL/api/v1/trpc/$procedure
Content-Type: application/json
Authorization: Bearer $token

{"json": <input>}
```

Responses are `{"result": {"data": {"json": <output>}}}` on success or
`{"error": {...}}` on failure.

The app only needs a small subset of procedures. Define Kotlin data classes
matching the tRPC input/output shapes and use Retrofit or Ktor client directly.

### Base URL

- Production: the deployed server (configure as a build variant)
- Dev: `http://10.0.2.2:4000` (emulator loopback to host) or host machine IP for
  a physical device

## Authentication

Better Auth with the `bearer` plugin is already configured on the server.
Session tokens are resolved from `Authorization: Bearer` headers identically to
cookies — no server-side changes needed.

### Login flow

Load `/login?returnTo=/android-complete-login` in a WebView. The existing login
page passes `returnTo` through as `callbackURL` to Better Auth. Better Auth
accepts it because it's a same-origin path, not a custom scheme. After OAuth
completes the WebView navigates to `/android-complete-login`. Intercept that
navigation, read the session cookie from `CookieManager`, and save it. No
changes needed to the web client or server.

```kotlin
webView.webViewClient = object : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (request.url.path == "/android-complete-login") {
            val cookies = CookieManager.getInstance().getCookie(BASE_URL)
            val token = cookies?.split(";")
                ?.map { it.trim() }
                ?.firstOrNull { it.startsWith("better-auth.session_token=") }
                ?.removePrefix("better-auth.session_token=")
            if (token != null) saveToken(token) // EncryptedSharedPreferences
            return true
        }
        return false
    }
}
webView.loadUrl("$BASE_URL/login?returnTo=/android-complete-login")
```

Store the token in `EncryptedSharedPreferences`.

### Session schema (from server)

The `session` table has:

| field       | type   | notes                          |
|-------------|--------|--------------------------------|
| `id`        | text   | session ID                     |
| `token`     | text   | the bearer token to store      |
| `userId`    | text   | FK → `user.id`                 |
| `expiresAt` | ts     | check before use; refresh if   |

The `user` table has `id`, `name`, `email`, `image`.

## Recording Service

```kotlin
class RecordingService : Service() {
    // Foreground service — shows persistent notification
    // FusedLocationProviderClient with HIGH_ACCURACY
    // Writes GpsPoint(lat, lon, ele, accuracy, timestamp) to local Room DB
    // Enqueues sync work via WorkManager when connectivity available
}
```

Key decisions:
- Use `FusedLocationProviderClient` (not raw GPS) for battery efficiency
- Foreground service mandatory for background location on Android 10+
- Request `ACCESS_FINE_LOCATION` and `ACCESS_BACKGROUND_LOCATION` permissions
- Target interval: 5s, fastest interval: 2s — tune based on battery testing
- Persist to Room immediately; sync is best-effort and separate

## Local Storage (Room)

```kotlin
@Entity
data class GpsPoint(
    // ...
)

@Entity
data class Track(
    // ...
)
```

## Sync

TODO

## WebView (Planning, viewing past tracks, etc)

```kotlin
webView.settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true
    databaseEnabled = true
}
webView.loadUrl("$BASE_URL")
```

- The web client handles its own auth via cookies; the WebView shares the cookie
  jar automatically when loading the same origin
- The `__INITIAL_SESSION__` injection on the server works as-is (server reads
  the session cookie from the WebView request and injects user data)
- No JS bridge needed for Phase 1 — planning and recording are decoupled via the
  backend

## Screen Structure

```
MainActivity
├── WebScreen          ← WebView (default, fullscreen)
└── RecordingScreen    ← Compose, replaces WebView when recording
```

The web client is the primary UI. The WebView sets a custom user-agent suffix
(`PlanTopoAndroid`) so the web client can detect it:

```kotlin
webView.settings.userAgentString += " PlanTopoAndroid"
```

```ts
// web client
const isAndroid = navigator.userAgent.includes("PlanTopoAndroid")
```

When in Android mode, the web client adds a "Record" button to its navbar.
Tapping it fires an Android intent:

```kotlin
// Injected JS bridge
webView.addJavascriptInterface(object {
    @JavascriptInterface
    fun startRecording() {
        // navigate to RecordingScreen
    }
}, "Android")
```

```ts
// web client — called when Record button tapped
window.Android?.startRecording()
```

`RecordingScreen` is a full-screen Compose view with a back button (top-left)
that returns to the WebScreen. The WebView stays alive in the back stack so
state isn't lost.



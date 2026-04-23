import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

android {
    namespace = "com.plantopo.plantopo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.plantopo.plantopo"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            // Uses the default debug keystore automatically
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "BASE_URL", "\"http://localhost:4000\"")
            buildConfigField("String", "OAUTH_SCHEME", "\"plantopo-debug\"")
            manifestPlaceholders["oauthScheme"] = "plantopo-debug"
        }
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            buildConfigField("String", "BASE_URL", "\"https://plantopo.com\"")
            buildConfigField("String", "OAUTH_SCHEME", "\"plantopo\"")
            manifestPlaceholders["oauthScheme"] = "plantopo"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Create download task for each variant using androidComponents API
androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }

        // Create the output directory property outside the task
        val assetsOutputDir = project.objects.directoryProperty()
        assetsOutputDir.set(layout.buildDirectory.dir("generated/assets/${variant.name}"))

        val downloadTask = tasks.register("download${variantName}SpaAssets") {
            description = "Download native-assets.tar.gz for ${variant.name}"
            group = "build"

            outputs.dir(assetsOutputDir)
            outputs.upToDateWhen { false }  // Always redownload on every build

            doLast {
                val assetsDir = assetsOutputDir.get().asFile
                assetsDir.mkdirs()

                // Use .bin extension to prevent Android from auto-decompressing
                val outputFile = File(assetsDir, "native-assets.bin")

                // Get BASE_URL based on variant build type
                val baseUrl = when (variant.buildType) {
                    "debug" -> "http://localhost:4000"
                    else -> "https://plantopo.com"
                }
                val downloadUrl = "$baseUrl/native-assets.tar.gz"

                println("Downloading native-assets.tar.gz from $downloadUrl for ${variant.name}...")
                val uri = URI(downloadUrl)
                uri.toURL().openStream().use { input ->
                    Files.copy(input, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                println("Downloaded native-assets.bin (${outputFile.length() / 1024} KB)")
            }
        }

        // Register the generated assets directory with this variant
        variant.sources.assets?.addGeneratedSourceDirectory(
            downloadTask,
            { assetsOutputDir }
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // AppCompat
    implementation(libs.androidx.appcompat)

    // Fragments
    implementation(libs.androidx.fragment.ktx)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Security (EncryptedSharedPreferences)
    implementation(libs.androidx.security.crypto)

    // WebView
    implementation(libs.androidx.webkit)

    // Chrome Custom Tabs for OAuth
    implementation(libs.androidx.browser)

    // Timber for logging
    implementation(libs.timber)

    // HTTP client
    implementation(libs.okhttp)

    // Apache Commons Compress for tar.gz extraction
    implementation(libs.commons.compress)

    // JSON serialization
    implementation(libs.kotlinx.serialization.json)

    // Room database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager for background sync
    implementation(libs.androidx.work.runtime.ktx)

    // Location services
    implementation(libs.play.services.location)

    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.mockwebserver)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
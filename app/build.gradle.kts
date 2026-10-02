import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.sentry)
}

// Read Supabase keys from local.properties (gitignored) so they aren't committed.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String, fallback: String = ""): String =
    localProperties.getProperty(key) ?: System.getenv(key) ?: fallback

android {
    namespace = "com.gratitudegarden.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.gratitudegarden.app"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String", "SUPABASE_URL",
            "\"${secret("SUPABASE_URL", "https://wllqgdjkkhztbefdvvsf.supabase.co")}\"",
        )
        buildConfigField(
            "String", "SUPABASE_ANON_KEY",
            "\"${secret("SUPABASE_ANON_KEY")}\"",
        )
        // Where crash reports go (see util.Diagnostics). Empty means none are sent.
        buildConfigField("String", "SENTRY_DSN", "\"${secret("SENTRY_DSN")}\"")
    }

    // `consumer` is what ships to Play. `staging` is the same app plus the admin dashboard
    // (src/staging), installed side by side under its own ID so it never reaches the store.
    flavorDimensions += "distribution"
    productFlavors {
        create("consumer") {
            dimension = "distribution"
        }
        create("staging") {
            dimension = "distribution"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
        }
    }

    // Upload key for Play. The keystore lives outside the repo; its path and passwords come
    // from local.properties or the environment. Without them, release still builds, unsigned.
    val uploadStoreFile = secret("UPLOAD_STORE_FILE")
    val uploadSigning = if (uploadStoreFile.isNotEmpty()) {
        signingConfigs.create("upload") {
            storeFile = file(uploadStoreFile)
            storePassword = secret("UPLOAD_STORE_PASSWORD")
            keyAlias = secret("UPLOAD_KEY_ALIAS", "upload")
            keyPassword = secret("UPLOAD_KEY_PASSWORD")
        }
    } else null

    buildTypes {
        release {
            signingConfig = uploadSigning
            isMinifyEnabled = true
            isShrinkResources = true
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

// Room writes each schema version here so migrations can be tested against the real history.
// Committed on purpose: once a version ships, its JSON must never change.
room {
    schemaDirectory("$projectDir/schemas")
}

// Sentry's build side. It ties each release build to its R8 mapping so stack traces read as
// real names, and uploads the mapping when SENTRY_AUTH_TOKEN is set. Everything else it can
// add to the bytecode stays off: no network, database or logcat capture, which could carry
// request URLs or journal text, and the SDK is a normal dependency below rather than
// auto-installed.
val sentryAuthToken = secret("SENTRY_AUTH_TOKEN")
sentry {
    org.set(secret("SENTRY_ORG"))
    projectName.set(secret("SENTRY_PROJECT"))
    authToken.set(sentryAuthToken)
    includeProguardMapping.set(true)
    autoUploadProguardMapping.set(sentryAuthToken.isNotEmpty())
    uploadNativeSymbols.set(false)
    includeSourceContext.set(false)
    tracingInstrumentation { enabled.set(false) }
    autoInstallation { enabled.set(false) }
    telemetry.set(false)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Navigation + lifecycle-aware Compose state collection / viewModel()
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Daily reminder: AlarmManager schedules the notification (see ReminderScheduler);
    // DataStore stores the on-device reminder preferences (enabled + time-of-day).
    implementation(libs.androidx.datastore.preferences)

    // Room: the on-device copy of the user's data that every screen reads from
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)

    // The home-screen widget: the garden, the streak, and a way in to write
    implementation(libs.androidx.glance.appwidget)

    // Entry photos: drawn from the files the repository keeps on the device
    implementation(libs.coil.compose)

    // Supabase (auth + postgrest + storage) over Ktor, using kotlinx-serialization
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)

    // Crash and ANR reports, scrubbed on the device first (util.Diagnostics)
    implementation(libs.sentry.android.core)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    // GardenRepositoryTest: the real repository against a fake Supabase served by Ktor
    androidTestImplementation(libs.ktor.client.mock)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

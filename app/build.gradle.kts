import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.baselineprofile)
}

// Load Supabase credentials from local.properties (kept out of version control).
val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) localFile.inputStream().use { load(it) }
}
val supabaseUrl: String = localProperties.getProperty("supabase.url") ?: ""
val supabaseKey: String = localProperties.getProperty("supabase.key") ?: ""

// Release signing. The keystore and its passwords are the app's identity on Play — they never
// belong in version control, so they are read from keystore.properties (gitignored) or, for CI,
// from the environment. When neither is present the release build is simply left unsigned rather
// than failing, so an unconfigured checkout can still build and be inspected.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(env)

val releaseStoreFile: String? = signingValue("storeFile", "FINANCE_KEYSTORE_FILE")
val releaseStorePassword: String? = signingValue("storePassword", "FINANCE_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = signingValue("keyAlias", "FINANCE_KEY_ALIAS")
val releaseKeyPassword: String? = signingValue("keyPassword", "FINANCE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
).all { !it.isNullOrBlank() } && file(releaseStoreFile!!).exists()

android {
    namespace = "com.example.financemanager"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.stack.finance"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "2.1.0"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_KEY", "\"$supabaseKey\"")

        // Room writes one JSON schema per version here. They are the reference a migration test
        // replays an old database against, so they belong in version control.
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    // Migration tests read the exported schemas off the device, as assets.
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // The dependency set is dominated by material-icons-extended, which contributes
            // roughly 10,000 icon classes for the few dozen the app draws. Without R8 all of them
            // ship. See proguard-rules.pro for what is deliberately kept.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        aidl = false
        buildConfig = true
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Arch Components
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    
    // Drag-to-reorder
    implementation("sh.calvin.reorderable:reorderable:2.4.3")
    // Instrumented tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Local tests: jUnit, coroutines, Android runner
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests: jUnit rules and runners
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)

    // Navigation
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    annotationProcessor(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.room.testing)
    annotationProcessor("org.jetbrains.kotlin:kotlin-metadata-jvm:2.3.0")

    // Biometrics
    implementation(libs.androidx.biometric)
    // Modern fragment — biometric 1.1.0 pulls an old fragment whose 16-bit
    // requestCode check crashes the Activity Result API (permission/launcher calls)
    implementation(libs.androidx.fragment.ktx)

    // ML Kit OCR
    implementation(libs.play.services.mlkit.text.recognition)

    // Extended Icons
    implementation(libs.androidx.compose.material.icons.extended)
    // QR rendering for UPI settlement links
    implementation("com.google.zxing:core:3.5.3")

    // Supabase (cloud sync + anonymous auth)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.okhttp)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Ahead-of-time compilation of the startup path from app/src/main/baseline-prof.txt.
    // profileinstaller is what applies it on the device; the module below regenerates it.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
}

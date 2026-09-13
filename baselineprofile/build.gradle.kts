plugins {
    id("com.android.test")
    alias(libs.plugins.baselineprofile)
}

// This module exists only to produce app/src/main/baseline-prof.txt. It is never shipped: the
// generator drives the release app on a device, records which classes and methods the startup
// path actually touches, and writes that list out so ART can compile them ahead of time on the
// user's phone instead of interpreting them on first launch.
android {
    namespace = "com.example.financemanager.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
}

kotlin {
    jvmToolchain(17)
}

baselineProfile {
    // One run is enough for a startup profile; more only helps when measuring variance.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.benchmark.macro.junit4)
}

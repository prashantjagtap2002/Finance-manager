package com.example.financemanager.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the startup baseline profile.
 *
 * Run with `./gradlew :app:generateReleaseBaselineProfile` on a connected device. It launches the
 * app, waits for the first frame, and walks the tabs a user hits first, so the classes behind the
 * dashboard and its charts are compiled ahead of time rather than interpreted on first launch.
 *
 * The journey deliberately stops at read-only navigation: this runs against the real app and
 * would otherwise write rows into whatever database is on the device.
 */
@RunWith(AndroidJUnit4::class)
class StartupBaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndBrowse() = rule.collect(
        packageName = "com.stack.finance",
        // Also emit a startup profile, which drives dex layout so the classes needed for the
        // first frame sit together in the file instead of scattered across it.
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()

        // The dashboard is what every launch lands on, so its composables matter most.
        device.waitForIdle()

        // Then the tabs along the bottom, which is where a user goes next. Tapping by description
        // keeps this working if the bar is laid out differently on another screen size.
        listOf("Budget", "Insights", "Home").forEach { label ->
            device.findObject(androidx.test.uiautomator.By.text(label))?.let {
                it.click()
                device.waitForIdle()
            }
        }
    }
}

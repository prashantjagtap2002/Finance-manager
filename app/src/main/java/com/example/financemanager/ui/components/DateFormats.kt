package com.example.financemanager.ui.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Formats [millis] with [pattern], reusing one [SimpleDateFormat] per pattern per thread.
 *
 * Constructing a SimpleDateFormat is far more expensive than using one: it loads the locale's
 * date symbols and clones a Calendar. List rows used to build a fresh one every time they
 * composed, so every row scrolling into view paid that cost on the main thread. SimpleDateFormat
 * is not thread-safe, hence the per-thread cache; the locale and time zone are re-checked on every
 * call so a change in system settings is picked up without restarting the app.
 */
fun formatDate(millis: Long, pattern: String): String {
    val locale = Locale.getDefault()
    val formats = cache.get()!!
    val format = formats[pattern]?.takeIf { it.locale == locale }
        ?: LocaleBoundFormat(pattern, locale).also { formats[pattern] = it }
    return format.format(millis)
}

private class LocaleBoundFormat(pattern: String, val locale: Locale) {
    private val delegate = SimpleDateFormat(pattern, locale)
    private val date = Date()

    fun format(millis: Long): String {
        val zone = TimeZone.getDefault()
        if (delegate.timeZone.id != zone.id) delegate.timeZone = zone
        date.time = millis
        return delegate.format(date)
    }
}

// ThreadLocal.withInitial is API 26; minSdk is 24.
private val cache = object : ThreadLocal<HashMap<String, LocaleBoundFormat>>() {
    override fun initialValue() = HashMap<String, LocaleBoundFormat>()
}

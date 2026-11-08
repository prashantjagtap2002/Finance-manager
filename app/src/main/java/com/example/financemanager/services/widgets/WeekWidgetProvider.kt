package com.example.financemanager.services.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.example.financemanager.R
import com.example.financemanager.ui.components.moneyString
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * "Last 7 days": a bar per day, the week's total against the week before, and the category that
 * took the most. A widget can't draw, so the bars are rendered into a bitmap sized to the widget.
 */
class WeekWidgetProvider : FinanceAppWidget() {

    override suspend fun render(context: Context, manager: AppWidgetManager, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_week)
        views.setOnClickPendingIntent(android.R.id.background, openAppIntent(context))
        fun color(res: Int) = ContextCompat.getColor(context, res)

        val week = runCatching { loadWeekSnapshot(context) }.getOrNull()
        if (week == null) {
            views.setTextViewText(R.id.w_week_total, "—")
            views.setTextViewText(R.id.w_week_change, "Tap to open")
            views.setTextColor(R.id.w_week_change, color(R.color.widget_muted))
            views.setTextViewText(R.id.w_week_top, "")
            return views
        }

        views.setTextViewText(R.id.w_week_total, moneyString(week.total, decimals = 0))

        // Spending less is the good direction, so "less" is sage and "more" is coral.
        val (change, changeColor) = when {
            week.previousTotal <= 0.0 && week.total <= 0.0 -> "No spending" to R.color.widget_muted
            week.previousTotal <= 0.0 -> "Nothing the week before" to R.color.widget_muted
            else -> {
                val pct = ((week.total - week.previousTotal) / week.previousTotal * 100).roundToInt()
                when {
                    pct == 0 -> "Same as the week before" to R.color.widget_muted
                    pct < 0 -> "${abs(pct)}% less than before" to R.color.widget_ok
                    else -> "$pct% more than before" to R.color.widget_warn
                }
            }
        }
        views.setTextViewText(R.id.w_week_change, change)
        views.setTextColor(R.id.w_week_change, color(changeColor))

        views.setTextViewText(
            R.id.w_week_top,
            week.topCategory?.let { "Most on $it · ${moneyString(week.topCategoryAmount, decimals = 0)}" }
                ?: "No spending logged this week"
        )

        // The chart fills the space between header and footer. The launcher reports the widget's
        // size in dp: portrait width is MIN_WIDTH and portrait height MAX_HEIGHT.
        val options = manager.getAppWidgetOptions(widgetId)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180)
        val chartWidthDp = (widthDp - 28).coerceAtLeast(120)
        // 28 padding + 46 header + 8 gap + 24 footer.
        val chartHeightDp = (heightDp - 106).coerceIn(36, 220)
        views.setImageViewBitmap(R.id.w_chart, drawChart(context, week, chartWidthDp, chartHeightDp))
        return views
    }

    /** Seven rounded bars over day initials, today in gold and the rest in quiet white. */
    private fun drawChart(context: Context, week: WeekSnapshot, widthDp: Int, heightDp: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        // Capped so a large widget on a dense screen stays well under the RemoteViews bitmap limit.
        val scale = minOf(density, 900f / widthDp)
        val w = (widthDp * scale).roundToInt()
        val h = (heightDp * scale).roundToInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.widget_muted)
            textSize = 10f * scale
            textAlign = Paint.Align.CENTER
        }
        val todayLabel = Paint(label).apply {
            color = ContextCompat.getColor(context, R.color.widget_on_surface)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x59FFFFFF }
        val todayBar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.widget_accent)
        }
        val stub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.widget_track)
        }

        val labelSpace = 14f * scale
        val chartBottom = h - labelSpace
        val slot = w / 7f
        val barWidth = slot * 0.46f
        val radius = barWidth / 2.5f
        val max = week.days.maxOf { it.second }.takeIf { it > 0 } ?: 1.0
        val dayFormat = SimpleDateFormat("EEEEE", Locale.getDefault())

        week.days.forEachIndexed { i, (dayStart, amount) ->
            val isToday = i == week.days.lastIndex
            val cx = slot * i + slot / 2
            val left = cx - barWidth / 2
            val right = cx + barWidth / 2
            if (amount > 0) {
                // Every non-zero day gets at least a visible sliver, so a small spend isn't lost.
                val height = ((amount / max) * (chartBottom - 2 * scale)).toFloat().coerceAtLeast(4f * scale)
                canvas.drawRoundRect(RectF(left, chartBottom - height, right, chartBottom), radius, radius, if (isToday) todayBar else bar)
            } else {
                canvas.drawRoundRect(RectF(left, chartBottom - 3f * scale, right, chartBottom), radius, radius, stub)
            }
            canvas.drawText(dayFormat.format(dayStart), cx, h - 2f * scale, if (isToday) todayLabel else label)
        }
        return bitmap
    }
}

package dev.bananajeans.eduschedule

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews

/** Presentation only: cached schedule data stays separate from launcher rendering. */
internal data class WidgetRow(val time: String = "", val subject: String, val details: String = "")

/** The configuration preview and launcher use exactly the same hierarchy and sizing. */
internal fun scheduleWidgetViews(
    context: Context, options: WidgetOptions, colors: WidgetColors,
    widthDp: Int, heightDp: Int, rows: List<WidgetRow>, emptyMessage: String,
    fontScale: Float = context.resources.configuration.fontScale
): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_schedule)
    // One pixel per dp keeps the rounded background below RemoteViews' bitmap budget.
    views.setImageViewBitmap(R.id.widget_background,
        widgetBackground(widthDp, heightDp, colors, options.border, 1f))
    views.setTextViewText(R.id.widget_title, context.getString(options.mode.label()))
    views.setTextColor(R.id.widget_title, colors.accent)
    views.removeAllViews(R.id.widget_rows)
    views.setTextViewText(R.id.widget_empty, emptyMessage)
    views.setTextColor(R.id.widget_empty, colors.secondaryText)
    views.setViewVisibility(R.id.widget_empty, if (rows.isEmpty()) View.VISIBLE else View.GONE)

    // Reserve scaled title/padding space, then fit whole rows. At least one row remains
    // available on compact widgets; Android ellipsizes its text rather than shrinking it.
    var available = heightDp - 32 - 24 * fontScale
    for ((index, row) in rows.take(options.maxRows).withIndex()) {
        val rowHeight = 12 + (if (row.details.isBlank()) 24 else 42) * fontScale
        if (index > 0 && available < rowHeight) break
        available -= rowHeight
        val item = RemoteViews(context.packageName, R.layout.widget_lesson)
        item.setTextViewText(R.id.widget_time, row.time)
        item.setViewVisibility(R.id.widget_time, if (row.time.isBlank()) View.GONE else View.VISIBLE)
        item.setTextViewText(R.id.widget_subject, row.subject)
        item.setTextViewText(R.id.widget_details, row.details)
        item.setViewVisibility(R.id.widget_details, if (row.details.isBlank()) View.GONE else View.VISIBLE)
        item.setTextColor(R.id.widget_time, colors.text)
        item.setTextColor(R.id.widget_subject, colors.text)
        item.setTextColor(R.id.widget_details, colors.secondaryText)
        // Material titleSmall/titleMedium/bodySmall roles, expressed for RemoteViews.
        item.setTextViewTextSize(R.id.widget_time, TypedValue.COMPLEX_UNIT_SP, 14f)
        item.setTextViewTextSize(R.id.widget_subject, TypedValue.COMPLEX_UNIT_SP, 16f)
        item.setTextViewTextSize(R.id.widget_details, TypedValue.COMPLEX_UNIT_SP, 12f)
        views.addView(R.id.widget_rows, item)
    }
    return views
}

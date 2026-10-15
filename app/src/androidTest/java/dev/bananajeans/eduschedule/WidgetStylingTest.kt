package dev.bananajeans.eduschedule

import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic, offline examples; screenshots include actual launcher RemoteViews. */
class WidgetStylingTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val rows = listOf(WidgetRow("08:45", "Mathematics", "204 · Teacher A"),
        WidgetRow("10:00", "English", "105 · Teacher B"), WidgetRow("11:05", "Physics", "301"))

    private fun screenshot(name: String) {
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val dir = File(output?.let { File(it) } ?: context.getExternalFilesDir(null)!!,
            "widget-screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun comparison(name: String, theme: String, palette: String, scale: Float = 1f) {
        val prefs = Preferences(context)
        val oldTheme = prefs.theme
        val oldPalette = prefs.palette
        val oldDynamic = prefs.dynamic
        try {
            prefs.theme = theme; prefs.palette = palette; prefs.dynamic = false
            val colors = widgetColors(context, prefs, WidgetOptions())
            compose.setContent {
                val density = LocalDensity.current
                val config = Configuration(LocalContext.current.resources.configuration).apply { fontScale = scale }
                val widgetContext = LocalContext.current.createConfigurationContext(config)
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    EduTheme(theme, false, palette) {
                        Surface {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("In app", style = MaterialTheme.typography.titleMedium)
                                LessonCard(Lesson("example", "Mathematics", 0, "1", LocalTime.of(8, 45),
                                    LocalTime.of(9, 30), emptyList(), emptyList(), emptyList(), emptyList(),
                                    "", "Teacher A", "204", "", "")) {}
                                Text("Home screen widget", style = MaterialTheme.typography.titleMedium)
                                AndroidView(modifier = Modifier.fillMaxWidth().height(240.dp), factory = { hostContext ->
                                    FrameLayout(hostContext).apply {
                                        addView(scheduleWidgetViews(widgetContext, WidgetOptions(), colors, 320, 240,
                                            rows, "", scale).apply(widgetContext, this))
                                    }
                                })
                            }
                        }
                    }
                }
            }
            compose.onNodeWithText("In app").assertIsDisplayed()
            compose.onNodeWithText("Home screen widget").assertIsDisplayed()
            screenshot(name)
        } finally { prefs.theme = oldTheme; prefs.palette = oldPalette; prefs.dynamic = oldDynamic }
    }

    @Test fun defaultLightComparison() = comparison("default-light", "Light", "Default")
    @Test fun catppuccinDarkComparison() = comparison("catppuccin-dark", "Dark", "Catppuccin")
    @Test fun oceanLightLargeFontComparison() = comparison("ocean-light-large-font", "Light", "Ocean", 1.5f)

    @Test fun remoteViewsKeepHierarchyAndFitWholeRowsAtLargeFonts() {
        compose.runOnUiThread {
            val colors = WidgetColors(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF426437.toInt(),
                0xFF888888.toInt(), 0xFF555555.toInt())
            val host = FrameLayout(context)
            fun render(height: Int, scale: Float = 1f, data: List<WidgetRow> = rows) =
                scheduleWidgetViews(context, WidgetOptions(), colors, 280, height, data, "No lessons", scale)
                    .apply(context, host)
            val normal = render(240)
            assertEquals(3, normal.findViewById<LinearLayout>(R.id.widget_rows).childCount)
            assertEquals("08:45", normal.findViewById<TextView>(R.id.widget_time).text.toString())
            assertEquals("Mathematics", normal.findViewById<TextView>(R.id.widget_subject).text.toString())
            assertEquals(colors.secondaryText, normal.findViewById<TextView>(R.id.widget_details).currentTextColor)
            assertEquals(2, render(240, 1.5f).findViewById<LinearLayout>(R.id.widget_rows).childCount)
            assertEquals(1, render(100).findViewById<LinearLayout>(R.id.widget_rows).childCount)
            val compactWeek = scheduleWidgetViews(context, WidgetOptions(mode = WidgetMode.WEEK), colors,
                180, 100, listOf(WidgetRow(subject = "Mon 14", details = "6 lessons")), "").apply(context, host)
            assertEquals("Mon 14 · 6 lessons", compactWeek.findViewById<TextView>(R.id.widget_subject).text.toString())
            val empty = render(128, data = emptyList())
            assertEquals(View.VISIBLE, empty.findViewById<View>(R.id.widget_empty).visibility)
            assertEquals(0, empty.findViewById<LinearLayout>(R.id.widget_rows).childCount)
        }
    }

    @Test fun compactModesAndBordersRenderOnAndroid() {
        compose.runOnUiThread {
            val colors = widgetColors(context, Preferences(context), WidgetOptions())
            val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            val dir = File(output?.let { File(it) } ?: context.getExternalFilesDir(null)!!,
                "widget-screenshots").apply { mkdirs() }
            for (mode in WidgetMode.entries) {
                val options = WidgetOptions(mode = mode, border = WidgetBorder.DASHED)
                val height = if (mode == WidgetMode.NEXT) 100 else 240
                val data = if (mode == WidgetMode.WEEK)
                    listOf(WidgetRow(subject = "Mon 14", details = "6 lessons"),
                        WidgetRow(subject = "Tue 15", details = "5 lessons")) else rows
                val view = scheduleWidgetViews(context, options, colors, 180, height, data, "")
                    .apply(context, FrameLayout(context))
                val density = context.resources.displayMetrics.density
                val w = (180 * density).toInt(); val h = (height * density).toInt()
                view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, w, h)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                File(dir, "compact-${mode.name.lowercase()}.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                assertTrue(view.findViewById<LinearLayout>(R.id.widget_rows).childCount > 0)
            }
        }
    }

    @Test fun followAppMatchesEveryPresetInBothModes() {
        val prefs = Preferences(context)
        val oldTheme = prefs.theme; val oldPalette = prefs.palette; val oldDynamic = prefs.dynamic
        try {
            prefs.dynamic = false
            for (dark in listOf(false, true)) for (palette in listOf("Default", "Catppuccin", "Ocean", "Custom")) {
                prefs.theme = if (dark) "Dark" else "Light"; prefs.palette = palette
                val scheme = themeScheme(palette, dark, prefs.customColors)
                val actual = widgetColors(context, prefs, WidgetOptions())
                assertEquals(scheme.surfaceContainer.toArgb(), actual.surface)
                assertEquals(scheme.onSurfaceVariant.toArgb(), actual.secondaryText)
            }
        } finally { prefs.theme = oldTheme; prefs.palette = oldPalette; prefs.dynamic = oldDynamic }
    }
}

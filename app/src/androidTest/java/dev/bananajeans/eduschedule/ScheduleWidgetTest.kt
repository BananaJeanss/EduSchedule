package dev.bananajeans.eduschedule

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleWidgetTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun widgetSettingsAreIndependentAndRemovedWithTheirInstance() {
        val a = 91001
        val b = 91002
        try {
            ScheduleWidgets.save(context, a, WidgetOptions(WidgetMode.UPCOMING, rooms = false,
                maxRows = 2, border = WidgetBorder.DASHED, palette = WidgetPalette.OCEAN))
            ScheduleWidgets.save(context, b, WidgetOptions(WidgetMode.TOMORROW, teachers = true,
                border = WidgetBorder.SOLID, palette = WidgetPalette.CATPPUCCIN))
            assertEquals(WidgetMode.UPCOMING, ScheduleWidgets.options(context, a).mode)
            assertEquals(2, ScheduleWidgets.options(context, a).maxRows)
            assertEquals(WidgetBorder.DASHED, ScheduleWidgets.options(context, a).border)
            assertEquals(WidgetPalette.OCEAN, ScheduleWidgets.options(context, a).palette)
            ScheduleWidgets.remove(context, intArrayOf(a))
            assertEquals(WidgetMode.TODAY, ScheduleWidgets.options(context, a).mode)
            assertEquals(WidgetMode.TOMORROW, ScheduleWidgets.options(context, b).mode)
            assertTrue(ScheduleWidgets.options(context, b).teachers)
        } finally { ScheduleWidgets.remove(context, intArrayOf(a, b)) }
    }

    @Test fun pickerHasDistinctPreviewLayouts() {
        val manager = AppWidgetManager.getInstance(context)
        val providers = manager.installedProviders.filter { it.provider.packageName == context.packageName }
        val expected = listOf(ScheduleWidgetProvider::class.java, NextScheduleWidgetProvider::class.java,
            UpcomingScheduleWidgetProvider::class.java, WeekScheduleWidgetProvider::class.java)
        expected.forEach { type ->
            val widget = providers.single { it.provider == ComponentName(context, type) }
            assertTrue("Missing preview for ${type.simpleName}", widget.previewLayout != 0)
        }
    }

    @Test fun everyPickerFallbackHasRenderedContentAndPreservesItsAspectRatio() {
        val previews = listOf(R.drawable.widget_preview_today, R.drawable.widget_preview_next,
            R.drawable.widget_preview_upcoming, R.drawable.widget_preview_week)
        for (resource in previews) {
            val bitmap = BitmapFactory.decodeResource(context.resources, resource)
            assertNotNull("Preview must be complete bitmap artwork", bitmap)
            assertTrue(bitmap.width >= 360)
            val background = bitmap.getPixel(bitmap.width / 2, bitmap.height - 5)
            // Sample inside the rounded surface, excluding its edge and the example caption.
            var ink = 0
            for (y in 40 until minOf(220, bitmap.height - 80)) {
                for (x in 60 until bitmap.width - 60) {
                    if (bitmap.getPixel(x, y) != background) ink++
                }
            }
            assertTrue("Preview contains no title or lesson text", ink > 1000)
            if (resource == R.drawable.widget_preview_next) assertTrue(bitmap.width > bitmap.height)
        }
    }

    @Test fun bordersRenderOnlyForSelectedStyle() {
        val colors = WidgetColors(Color.WHITE, Color.BLACK, Color.BLACK, Color.RED)
        val plain = widgetBackground(180, 180, colors, WidgetBorder.NONE, 1f)
        val solid = widgetBackground(180, 180, colors, WidgetBorder.SOLID, 1f)
        val dashed = widgetBackground(180, 180, colors, WidgetBorder.DASHED, 1f)
        assertEquals(Color.WHITE, plain.getPixel(90, 2))
        assertEquals(Color.RED, solid.getPixel(90, 2))
        val samples = (45..135).map { dashed.getPixel(it, 2) }.toSet()
        assertTrue(samples.contains(Color.RED))
        assertTrue(samples.contains(Color.WHITE))
        assertEquals(Color.WHITE, dashed.getPixel(90, 90))
    }
}


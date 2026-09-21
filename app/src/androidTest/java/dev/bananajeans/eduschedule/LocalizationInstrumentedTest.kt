package dev.bananajeans.eduschedule

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizationInstrumentedTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun explicitEnglishAndEstonianContextsResolveExpectedCopy() {
        val english = AppLocale.wrap(context, AppLanguage.ENGLISH)
        val estonian = AppLocale.wrap(context, AppLanguage.ESTONIAN)

        assertEquals("Settings", english.getString(R.string.settings))
        assertEquals("Seaded", estonian.getString(R.string.settings))
        assertEquals("EduSchedule", estonian.getString(R.string.app_name))
    }

    @Test
    fun explicitLocaleIsVisibleToDateFormattingConsumers() {
        assertEquals("en", AppLocale.locale(context, AppLanguage.ENGLISH).language)
        assertEquals("et", AppLocale.locale(context, AppLanguage.ESTONIAN).language)
    }

    @Test
    fun systemLanguageDoesNotOverrideTheInstrumentationConfiguration() {
        val expected = context.resources.configuration.locales[0]
        assertEquals(expected, AppLocale.locale(context, AppLanguage.SYSTEM))
    }
}

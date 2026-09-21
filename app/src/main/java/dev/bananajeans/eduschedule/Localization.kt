package dev.bananajeans.eduschedule

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import java.util.Locale

enum class AppLanguage(val preferenceValue: String, val languageTag: String?) {
    SYSTEM("system", null),
    ENGLISH("en", "en"),
    ESTONIAN("et", "et");

    companion object {
        fun fromPreference(value: String?): AppLanguage =
            entries.firstOrNull { it.preferenceValue == value } ?: SYSTEM
    }
}

object AppLocale {
    fun wrap(context: Context, language: AppLanguage): Context {
        val tag = language.languageTag ?: return context
        val locale = Locale.forLanguageTag(tag)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLocales(LocaleList(locale))
        }
        return context.createConfigurationContext(configuration)
    }

    fun locale(context: Context, language: AppLanguage): Locale =
        wrap(context, language).resources.configuration.locales[0]

    fun string(context: Context, language: AppLanguage, @StringRes resource: Int, vararg args: Any): String =
        wrap(context, language).getString(resource, *args)
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@StringRes
fun ScheduleKind.pluralLabelResource(): Int = when (this) {
    ScheduleKind.CLASS -> R.string.schedule_kind_classes
    ScheduleKind.TEACHER -> R.string.schedule_kind_teachers
    ScheduleKind.ROOM -> R.string.schedule_kind_rooms
}

@StringRes
fun ScheduleKind.singularLabelResource(): Int = when (this) {
    ScheduleKind.CLASS -> R.string.schedule_kind_class
    ScheduleKind.TEACHER -> R.string.schedule_kind_teacher
    ScheduleKind.ROOM -> R.string.schedule_kind_room
}

@StringRes
fun ScheduleKind.searchLabelResource(): Int = when (this) {
    ScheduleKind.CLASS -> R.string.search_classes
    ScheduleKind.TEACHER -> R.string.search_teachers
    ScheduleKind.ROOM -> R.string.search_rooms
}

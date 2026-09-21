package dev.bananajeans.eduschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationTest {
    @Test
    fun supportedLanguagesRoundTripPreferences() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromPreference(null))
        AppLanguage.entries.forEach { language ->
            assertEquals(language, AppLanguage.fromPreference(language.preferenceValue))
        }
        assertEquals(setOf(null, "en", "et"), AppLanguage.entries.map { it.languageTag }.toSet())
    }

    @Test
    fun scheduleKindsHaveDedicatedLocalizedResources() {
        val plural = ScheduleKind.entries.map { it.pluralLabelResource() }
        val singular = ScheduleKind.entries.map { it.singularLabelResource() }
        val search = ScheduleKind.entries.map { it.searchLabelResource() }

        assertEquals(plural.size, plural.toSet().size)
        assertEquals(singular.size, singular.toSet().size)
        assertEquals(search.size, search.toSet().size)
        assertNotEquals(plural.toSet(), singular.toSet())
    }

    @Test
    fun estonianResourcesMatchEnglishKeysAndFormatArguments() {
        val english = resources(resourceFile("values/strings.xml"), skipNonTranslatable = true)
        val estonian = resources(resourceFile("values-et/strings.xml"), skipNonTranslatable = false)

        assertEquals("Translation keys differ", english.keys, estonian.keys)
        english.forEach { (key, placeholders) ->
            assertEquals("Format arguments differ for $key", placeholders, estonian.getValue(key))
        }
    }

    private fun resourceFile(relative: String): File =
        listOf(
            File("src/main/res/$relative"),
            File("app/src/main/res/$relative")
        ).firstOrNull(File::isFile)
            ?: error("Could not find $relative from ${File(".").absolutePath}")

    private fun resources(file: File, skipNonTranslatable: Boolean): Map<String, List<String>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val result = linkedMapOf<String, List<String>>()
        val children = document.documentElement.childNodes

        for (index in 0 until children.length) {
            val element = children.item(index) as? Element ?: continue
            if (skipNonTranslatable && element.getAttribute("translatable") == "false") continue
            val name = element.getAttribute("name")
            when (element.tagName) {
                "string" -> result["string:$name"] = placeholders(element.textContent)
                "plurals" -> {
                    val items = element.childNodes
                    for (itemIndex in 0 until items.length) {
                        val item = items.item(itemIndex) as? Element ?: continue
                        if (item.tagName != "item") continue
                        val quantity = item.getAttribute("quantity")
                        result["plurals:$name:$quantity"] = placeholders(item.textContent)
                    }
                }
            }
        }
        return result
    }

    private fun placeholders(value: String): List<String> =
        Regex("%(?:\\d+\\$)?[a-zA-Z]").findAll(value).map { it.value }.toList()
}

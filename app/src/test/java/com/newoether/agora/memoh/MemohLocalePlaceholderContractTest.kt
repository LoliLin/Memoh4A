package com.newoether.agora.memoh

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every `memoh_ui_*` string that callers format must use the positional `%1$s` form.
 *
 * Android's `Resources.getString(id, arg)` only substitutes `%N$` placeholders. A legacy `{0}`
 * body looks like it takes an argument but is passed through verbatim, and an argument with no
 * placeholder throws `IllegalFormatException` at runtime — a crash on a confirmation dialog, long
 * after the string compiled cleanly.
 */
class MemohLocalePlaceholderContractTest {
    private val directories = listOf(
        "values", "values-ar", "values-de", "values-es", "values-fr", "values-ja",
        "values-ko", "values-pt-rBR", "values-ru", "values-vi", "values-zh", "values-zh-rTW",
    )

    /** Call sites that pass an argument into getString(). */
    private val formattedKeys = setOf("memoh_ui_remove_provider_confirm")

    @Test
    fun formattedStringsUsePositionalPlaceholdersInEveryLocale() {
        val root = locateResources()
        directories.forEach { directory ->
            val strings = readStrings(File(root, "$directory/memoh_ui_strings.xml"))
            formattedKeys.forEach { key ->
                val value = strings[key]
                assertTrue("$directory $key missing", !value.isNullOrBlank())
                assertTrue(
                    "$directory $key uses a legacy brace placeholder: $value",
                    !Regex("""\{\d+\}""").containsMatchIn(value!!),
                )
                assertTrue(
                    "$directory $key has no positional placeholder: $value",
                    Regex("""%\d+\$[a-zA-Z]""").containsMatchIn(value),
                )
            }
        }
    }

    @Test
    fun aFormattedStringIsNeverMissingItsArgument() {
        val root = locateResources()
        val defaults = readStrings(File(root, "values/memoh_ui_strings.xml"))
        assertEquals(1, Regex("""%\d+\$[a-zA-Z]""").findAll(defaults.getValue("memoh_ui_remove_provider_confirm")).count())
    }

    private fun readStrings(file: File): Map<String, String> = buildMap {
        if (!file.isFile) return@buildMap
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        repeat(nodes.length) { index ->
            val node = nodes.item(index)
            val key = node.attributes.getNamedItem("name")?.nodeValue ?: return@repeat
            put(key, node.textContent)
        }
    }

    private fun locateResources(): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            listOf(File(directory, "app/src/main/res"), File(directory, "src/main/res"))
                .firstOrNull(File::isDirectory)?.let { return it }
            directory = directory.parentFile ?: error("Reached filesystem root")
        }
        error("Unable to locate resource directory")
    }
}

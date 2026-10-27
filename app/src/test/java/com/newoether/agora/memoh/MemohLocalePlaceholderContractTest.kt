package com.newoether.agora.memoh

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locale contracts for `memoh_ui_*` / `memoh_error_*` strings.
 *
 * Android's `Resources.getString(id, arg)` only substitutes `%N$` placeholders. A legacy `{0}`
 * body looks like it takes an argument but is passed through verbatim, and an argument passed to a
 * string with no placeholder throws `IllegalFormatException` — a crash on a confirmation dialog,
 * long after the string compiled cleanly. A locale that drops a placeholder is the same bug with a
 * different trigger.
 */
class MemohLocalePlaceholderContractTest {
    private val directories = listOf(
        "values", "values-ar", "values-de", "values-es", "values-fr", "values-ja",
        "values-ko", "values-pt-rBR", "values-ru", "values-vi", "values-zh", "values-zh-rTW",
    )

    private val placeholder = Regex("""%(\d+)\$[a-zA-Z]""")

    @Test
    fun noLocaleUsesLegacyBracePlaceholders() {
        val root = locateResources()
        directories.forEach { directory ->
            readStrings(File(root, "$directory/memoh_ui_strings.xml")).forEach { (key, value) ->
                assertTrue(
                    "$directory $key uses a legacy brace placeholder: $value",
                    !Regex("""\{\d+\}""").containsMatchIn(value),
                )
            }
        }
    }

    @Test
    fun everyLocaleKeepsTheBasePlaceholdersForKeysThatTakeArguments() {
        val root = locateResources()
        val base = readStrings(File(root, "values/memoh_ui_strings.xml"))
        val withPlaceholders = base.filterValues { placeholder.containsMatchIn(it) }
        assertTrue("expected formatted strings in the base locale", withPlaceholders.isNotEmpty())

        directories.drop(1).forEach { directory ->
            val strings = readStrings(File(root, "$directory/memoh_ui_strings.xml"))
            withPlaceholders.forEach { (key, baseValue) ->
                val value = strings[key]
                assertTrue("$directory $key missing", !value.isNullOrBlank())
                assertEquals(
                    "$directory $key placeholders",
                    placeholders(baseValue),
                    placeholders(value!!),
                )
            }
        }
    }

    @Test
    fun theRemoveProviderConfirmationKeepsItsSingleArgument() {
        val root = locateResources()
        val defaults = readStrings(File(root, "values/memoh_ui_strings.xml"))
        val value = defaults.getValue("memoh_ui_remove_provider_confirm")
        assertEquals(listOf("%1\$s"), placeholders(value))
    }

    /** User-visible errors must be localized, never a bare English literal. */
    @Test
    fun everyUserFacingErrorHasAResourceInEveryLocale() {
        val root = locateResources()
        // Production sources only: this test file contains the forbidden pattern itself.
        val sources = locateMemoSourceDirectory().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        assertTrue("no Memoh sources found", sources.isNotEmpty())

        val literal = Regex("""error\s*=\s*"[A-Z]""")
        sources.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                assertTrue(
                    "${file.name}:${index + 1} assigns a hardcoded English error",
                    !literal.containsMatchIn(line),
                )
            }
        }

        val errorKeys = sources.flatMap { file ->
            Regex("""R\.string\.(memoh_error_[a-z0-9_]+)""").findAll(file.readText())
                .map { it.groupValues[1] }.toList()
        }.toSet()
        assertTrue("expected localized error keys", errorKeys.isNotEmpty())
        errorKeys.forEach { key ->
            directories.forEach { directory ->
                val strings = readStrings(File(root, "$directory/memoh_ui_strings.xml"))
                assertTrue("$directory missing $key", !strings[key].isNullOrBlank())
            }
        }
    }

    private fun locateMemoSourceDirectory(): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            listOf(
                File(directory, "app/src/main/java/com/newoether/agora/memoh"),
                File(directory, "src/main/java/com/newoether/agora/memoh"),
            ).firstOrNull(File::isDirectory)?.let { return it }
            directory = directory.parentFile ?: error("Reached filesystem root")
        }
        error("Unable to locate Memoh source directory")
    }

    private fun placeholders(value: String): List<String> =
        placeholder.findAll(value).map { it.value }.sorted().toList()

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

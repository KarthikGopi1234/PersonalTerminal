package dev.personalterminal.upgrade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **Public-contract gate** – the names Android (and the user's home screen, Tasker flows, adb
 * scripts) hold on to across updates. Renaming or removing any of these silently breaks something
 * that worked before the update:
 *
 *  - widget receivers → every placed widget disappears from the home screen,
 *  - launcher-icon aliases → the app icon vanishes from the launcher / dock until re-added,
 *  - the automation receiver + its actions → Tasker / adb intents stop working,
 *  - services → the running timer notification / Quick Settings tile break,
 *  - the FileProvider authority → share sheets from older shortcuts fail,
 *  - the database name and DataStore file → an update would start with empty data.
 *
 * The inventory lives in `app/contract.txt`; adding a component is fine (append it there), removing
 * one is a deliberate act that must come with a migration story – edit the file in the same commit
 * and say why in the message.
 */
class ContractTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val contract = File("contract.txt").readLines().map { it.substringBefore('#').trim() }.filter { it.isNotBlank() }

    @Test fun `every component in the contract is still declared in the manifest`() {
        val missing = contract.filter { entry ->
            when {
                entry.startsWith("action:") -> !manifest.contains("android:name=\"${entry.removePrefix("action:")}\"")
                entry.startsWith("authority:") -> !manifest.contains("android:authorities=\"${entry.removePrefix("authority:")}\"")
                entry.startsWith("db:") -> dev.personalterminal.data.db.AppDatabase.NAME != entry.removePrefix("db:")
                entry.startsWith("datastore:") -> !File("src/main/java/dev/personalterminal/data/prefs/UserPrefs.kt").readText().contains("preferencesDataStore(name = \"${entry.removePrefix("datastore:")}\")")
                else -> !manifest.contains("android:name=\"$entry\"")
            }
        }
        assertTrue("public contract broken – these were shipped in an earlier release and are gone now:\n  " + missing.joinToString("\n  "), missing.isEmpty())
    }

    @Test fun `package name and applicationId are unchanged`() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("applicationId = \"dev.personalterminal\""))
        assertTrue(gradle.contains("namespace = \"dev.personalterminal\""))
        assertTrue(manifest.contains("android:allowBackup=\"true\""))
    }

    @Test fun `widget descriptors keep their resource names`() {
        // The AppWidgetProviderInfo xml is referenced by name from the manifest; renaming it orphans the widget.
        listOf("habit_widget_info", "streak_widget_info", "timer_widget_info").forEach {
            assertTrue("res/xml/$it.xml missing", File("src/main/res/xml/$it.xml").exists())
            assertTrue("manifest no longer references @xml/$it", manifest.contains("@xml/$it"))
        }
    }

    @Test fun `every launcher alias is either the default or disabled by default`() {
        // Exactly one alias enabled at install time; the app switches at runtime. Two enabled = two icons after update.
        val aliases = Regex("<activity-alias[\\s\\S]*?</activity-alias>").findAll(manifest).map { it.value }.filter { it.contains("android.intent.category.LAUNCHER") }.toList()
        val enabled = aliases.count { it.contains("android:enabled=\"true\"") }
        assertEquals("launcher aliases enabled by default", 1, enabled)
    }
}

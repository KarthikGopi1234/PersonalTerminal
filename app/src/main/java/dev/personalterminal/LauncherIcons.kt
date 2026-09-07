package dev.personalterminal

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Selectable launcher icons. Each variant is an `activity-alias` in the manifest that targets
 * [MainActivity]; exactly one is enabled at a time, so the launcher shows the chosen artwork while
 * every explicit intent (widgets, shortcuts, notifications) keeps working against the real activity.
 */
object LauncherIcons {
    data class Option(val id: String, val label: String, val blurb: String, val foreground: Int, val background: Long, val alias: String)

    val options: List<Option> = listOf(
        Option("classic", "classic", "chevron & cursor in a watch ring", R.drawable.ic_launcher_foreground, 0xFF282A36, "LauncherClassic"),
        Option("prompt", "prompt", "just  >_", R.drawable.ic_launcher_prompt_foreground, 0xFF1E1F29, "LauncherPrompt"),
        Option("watch", "watch", "a dial whose hands are the prompt", R.drawable.ic_launcher_watch_foreground, 0xFF0F1117, "LauncherWatch"),
        Option("checkbox", "checkbox", "[✓] – the habit side", R.drawable.ic_launcher_checkbox_foreground, 0xFF282A36, "LauncherCheckbox"),
        Option("amber", "amber", "the classic mark in 1978 phosphor", R.drawable.ic_launcher_amber_foreground, 0xFF0A0700, "LauncherAmber"),
        Option("mono", "mono", "white on black, one red accent", R.drawable.ic_launcher_mono_foreground, 0xFF000000, "LauncherMono"),
    )

    fun option(id: String): Option = options.firstOrNull { it.id == id } ?: options.first()

    /** Currently enabled alias according to the package manager (survives reinstall/backup restores). */
    fun current(context: Context): String {
        val pm = context.packageManager
        val explicit = options.firstOrNull { pm.getComponentEnabledSetting(component(context, it)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }
        return explicit?.id ?: options.first().id
    }

    /** Enable the alias for [id] and disable the rest – the icon swaps within a few seconds on most launchers. */
    fun apply(context: Context, id: String) {
        val pm = context.packageManager
        val chosen = option(id)
        // enable the new one first so there is never a moment with no launcher entry
        pm.setComponentEnabledSetting(component(context, chosen), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        options.filter { it.id != chosen.id }.forEach {
            pm.setComponentEnabledSetting(component(context, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
        // dynamic shortcuts hang off the enabled launcher activity – re-publish them against the new alias
        AppShortcuts.publish(context)
    }

    private fun component(context: Context, o: Option) = ComponentName(context.packageName, "dev.personalterminal.${o.alias}")
}

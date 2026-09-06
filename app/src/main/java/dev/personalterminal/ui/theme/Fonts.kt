package dev.personalterminal.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import dev.personalterminal.R

/** Selectable monospace families (all bundled – no network fonts). */
object Fonts {
    data class Option(val id: String, val label: String, val family: FontFamily)

    val FiraCode = FontFamily(
        Font(R.font.fira_code_regular, FontWeight.Normal),
        Font(R.font.fira_code_medium, FontWeight.Medium),
        Font(R.font.fira_code_bold, FontWeight.Bold),
    )
    val RobotoMono = FontFamily(
        Font(R.font.roboto_mono_regular, FontWeight.Normal),
        Font(R.font.roboto_mono_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.roboto_mono_medium, FontWeight.Medium),
        Font(R.font.roboto_mono_bold, FontWeight.Bold),
    )

    val options: List<Option> = listOf(
        Option("jetbrains", "JetBrains Mono", JetBrainsMono),
        Option("fira", "Fira Code", FiraCode),
        Option("roboto", "Roboto Mono", RobotoMono),
        Option("system", "system mono", FontFamily.Monospace),
    )

    fun family(id: String): FontFamily = options.firstOrNull { it.id == id }?.family ?: JetBrainsMono
}

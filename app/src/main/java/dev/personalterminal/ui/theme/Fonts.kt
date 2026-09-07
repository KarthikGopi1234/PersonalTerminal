package dev.personalterminal.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.personalterminal.R

/** Selectable monospace families (all bundled under the OFL – no network fonts). */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
object Fonts {
    data class Option(val id: String, val label: String, val family: FontFamily, val blurb: String)

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
    val IbmPlexMono = FontFamily(
        Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_mono_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
        Font(R.font.ibm_plex_mono_bold, FontWeight.Bold),
    )
    /** Variable font: one file, weight axis. */
    val SourceCodePro = FontFamily(
        Font(R.font.source_code_pro, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.source_code_pro, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.source_code_pro, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )
    val VictorMono = FontFamily(
        Font(R.font.victor_mono, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.victor_mono_italic, FontWeight.Normal, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.victor_mono, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.victor_mono, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )
    val SpaceMono = FontFamily(
        Font(R.font.space_mono_regular, FontWeight.Normal),
        Font(R.font.space_mono_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.space_mono_bold, FontWeight.Medium),
        Font(R.font.space_mono_bold, FontWeight.Bold),
    )
    /** Single-weight CRT bitmap look; medium/bold map to the same face. */
    val Vt323 = FontFamily(
        Font(R.font.vt323_regular, FontWeight.Normal),
        Font(R.font.vt323_regular, FontWeight.Medium),
        Font(R.font.vt323_regular, FontWeight.Bold),
    )

    val options: List<Option> = listOf(
        Option("jetbrains", "JetBrains Mono", JetBrainsMono, "the default – tall x-height, clear 0/O"),
        Option("fira", "Fira Code", FiraCode, "humanist, ligature-friendly"),
        Option("roboto", "Roboto Mono", RobotoMono, "android's own mono"),
        Option("plex", "IBM Plex Mono", IbmPlexMono, "typewriter warmth, great italics"),
        Option("source", "Source Code Pro", SourceCodePro, "adobe's classic, very neutral"),
        Option("victor", "Victor Mono", VictorMono, "narrow, cursive italics"),
        Option("space", "Space Mono", SpaceMono, "geometric, a bit retro-futurist"),
        Option("vt323", "VT323", Vt323, "green-phosphor terminal bitmap"),
        Option("system", "system mono", FontFamily.Monospace, "whatever the device ships"),
    )

    fun family(id: String): FontFamily = options.firstOrNull { it.id == id }?.family ?: JetBrainsMono

    /**
     * Some faces render smaller / larger than JetBrains Mono at the same nominal size; this keeps the
     * layout density comparable when switching (VT323 in particular is a small bitmap-style face).
     */
    fun sizeFactor(id: String): Float = when (id) {
        "vt323" -> 1.34f
        "space" -> 0.96f
        "victor" -> 1.04f
        else -> 1f
    }
}

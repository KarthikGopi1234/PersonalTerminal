package dev.personalterminal.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.json.JSONObject

/**
 * Importable colour schemes.
 *
 * Accepts three JSON dialects – the terminal community mostly ships one of these:
 *  • Personal Terminal: `{ "name": "...", "bg": "#282a36", "fg": "...", "red": ..., ... }` (our own keys)
 *  • Windows Terminal / iTerm-ish: `{ "background", "foreground", "red", "brightRed", "cyan", ... }`
 *  • Base16: `{ "scheme": "...", "base00": "282a36", ... "base0F": "..." }`
 * The scheme becomes both the dark and the light variant (light = derived by swapping bg/fg
 * unless the background is already light).
 */
object CustomPalette {
    data class Pair(val dark: TerminalPalette, val light: TerminalPalette)

    fun parse(json: String): Pair? {
        if (json.isBlank()) return null
        return runCatching {
            val o = JSONObject(json)
            val name = o.optString("name", o.optString("scheme", "custom")).ifBlank { "custom" }
            val base = when {
                o.has("base00") -> fromBase16(o, name)
                o.has("background") -> fromTerminal(o, name)
                o.has("bg") -> fromOwn(o, name)
                else -> return null
            }
            val light = if (!base.dark) base else base.copy(
                dark = false, name = "${base.name} (light)",
                bg = base.fg, bgAlt = base.fg.mix(base.bg, 0.06f), bgHighlight = base.fg.mix(base.bg, 0.14f),
                fg = base.bg, fgDim = base.bg.mix(base.fg, 0.35f), border = base.fg.mix(base.bg, 0.2f),
            )
            val dark = if (base.dark) base else base.copy(
                dark = true, name = "${base.name} (dark)",
                bg = base.fg, bgAlt = base.fg.mix(base.bg, 0.06f), bgHighlight = base.fg.mix(base.bg, 0.14f),
                fg = base.bg, fgDim = base.bg.mix(base.fg, 0.35f), border = base.fg.mix(base.bg, 0.2f),
            )
            Pair(dark, light)
        }.getOrNull()
    }

    /** Serialises [p] with our own keys so a palette can be exported / shared. */
    fun export(p: TerminalPalette): String {
        val o = JSONObject()
        o.put("name", p.name)
        listOf(
            "bg" to p.bg, "bgAlt" to p.bgAlt, "bgHighlight" to p.bgHighlight, "fg" to p.fg, "fgDim" to p.fgDim, "border" to p.border,
            "red" to p.red, "green" to p.green, "yellow" to p.yellow, "blue" to p.blue, "purple" to p.purple, "cyan" to p.cyan,
            "orange" to p.orange, "pink" to p.pink,
        ).forEach { (k, c) -> o.put(k, c.hex()) }
        return o.toString(2)
    }

    private fun fromOwn(o: JSONObject, name: String): TerminalPalette {
        val bg = o.color("bg")!!
        val fg = o.color("fg") ?: if (bg.luminance() < 0.5f) Color(0xFFF8F8F2) else Color(0xFF1F1F1F)
        val dark = bg.luminance() < 0.5f
        return TerminalPalette(
            id = Palettes.CUSTOM_ID, name = name, dark = dark,
            bg = bg, bgAlt = o.color("bgAlt") ?: bg.mix(fg, 0.05f), bgHighlight = o.color("bgHighlight") ?: bg.mix(fg, 0.14f),
            fg = fg, fgDim = o.color("fgDim") ?: fg.mix(bg, 0.45f), border = o.color("border") ?: bg.mix(fg, 0.2f),
            red = o.color("red") ?: Color(0xFFFF5555), green = o.color("green") ?: Color(0xFF50FA7B),
            yellow = o.color("yellow") ?: Color(0xFFF1FA8C), blue = o.color("blue") ?: Color(0xFF6272A4),
            purple = o.color("purple") ?: Color(0xFFBD93F9), cyan = o.color("cyan") ?: Color(0xFF8BE9FD),
            orange = o.color("orange") ?: Color(0xFFFFB86C), pink = o.color("pink") ?: Color(0xFFFF79C6),
        )
    }

    private fun fromTerminal(o: JSONObject, name: String): TerminalPalette {
        val bg = o.color("background")!!
        val fg = o.color("foreground") ?: Color(0xFFF8F8F2)
        val dark = bg.luminance() < 0.5f
        fun c(k: String, bright: String, fallback: Color) = o.color(bright) ?: o.color(k) ?: fallback
        return TerminalPalette(
            id = Palettes.CUSTOM_ID, name = name, dark = dark,
            bg = bg, bgAlt = bg.mix(fg, 0.05f), bgHighlight = o.color("selectionBackground") ?: bg.mix(fg, 0.14f),
            fg = fg, fgDim = o.color("brightBlack") ?: fg.mix(bg, 0.45f), border = bg.mix(fg, 0.2f),
            red = c("red", "brightRed", Color(0xFFFF5555)), green = c("green", "brightGreen", Color(0xFF50FA7B)),
            yellow = c("yellow", "brightYellow", Color(0xFFF1FA8C)), blue = c("blue", "brightBlue", Color(0xFF6272A4)),
            purple = c("purple", "brightPurple", o.color("magenta") ?: Color(0xFFBD93F9)), cyan = c("cyan", "brightCyan", Color(0xFF8BE9FD)),
            orange = o.color("orange") ?: (o.color("yellow") ?: Color(0xFFFFB86C)), pink = o.color("brightMagenta") ?: (o.color("magenta") ?: Color(0xFFFF79C6)),
        )
    }

    private fun fromBase16(o: JSONObject, name: String): TerminalPalette {
        fun b(k: String) = o.color(k) ?: Color.Magenta
        val bg = b("base00"); val fg = b("base05")
        return TerminalPalette(
            id = Palettes.CUSTOM_ID, name = name, dark = bg.luminance() < 0.5f,
            bg = bg, bgAlt = b("base01"), bgHighlight = b("base02"), fg = fg, fgDim = b("base03"), border = b("base02"),
            red = b("base08"), orange = b("base09"), yellow = b("base0A"), green = b("base0B"), cyan = b("base0C"),
            blue = b("base0D"), purple = b("base0E"), pink = b("base0F"),
        )
    }

    private fun JSONObject.color(key: String): Color? {
        val v = optString(key, "").trim().removePrefix("#")
        if (v.isEmpty()) return null
        val hex = when (v.length) { 3 -> v.map { "$it$it" }.joinToString(""); 6, 8 -> v; else -> return null }
        val n = hex.toLongOrNull(16) ?: return null
        return if (hex.length == 6) Color((0xFF000000L or n).toInt()) else Color(n.toInt())
    }

    private fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

    private fun Color.mix(other: Color, t: Float): Color = Color(
        red = red + (other.red - red) * t, green = green + (other.green - green) * t,
        blue = blue + (other.blue - blue) * t, alpha = 1f,
    )
}

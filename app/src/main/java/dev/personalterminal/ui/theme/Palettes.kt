package dev.personalterminal.ui.theme

import androidx.compose.ui.graphics.Color

/** A terminal colour scheme – 16-ish ANSI-inspired slots. */
data class TerminalPalette(
    val id: String,
    val name: String,
    val dark: Boolean,
    val bg: Color,
    val bgAlt: Color,        // panels / cards
    val bgHighlight: Color,  // selection / hover
    val fg: Color,
    val fgDim: Color,        // comments / muted
    val border: Color,
    val red: Color,
    val green: Color,
    val yellow: Color,
    val blue: Color,
    val purple: Color,
    val cyan: Color,
    val orange: Color,
    val pink: Color,
) {
    val accent: Color get() = green
    val prompt: Color get() = green
    val cursor: Color get() = fg

    fun named(name: String): Color = when (name.lowercase()) {
        "red" -> red; "green" -> green; "yellow" -> yellow; "blue" -> blue
        "purple" -> purple; "cyan" -> cyan; "orange" -> orange; "pink" -> pink
        else -> fg
    }
}

/** Theme families; each has a dark and a light variant. */
enum class ThemeFamily(val id: String, val label: String) {
    DRACULA("dracula", "Dracula"),
    NORD("nord", "Nord"),
    SOLARIZED("solarized", "Solarized"),
    GRUVBOX("gruvbox", "Gruvbox"),
    MONOKAI("monokai", "Monokai"),
    CATPPUCCIN("catppuccin", "Catppuccin"),
    MATRIX("matrix", "Matrix");

    companion object {
        fun fromId(id: String): ThemeFamily = entries.firstOrNull { it.id == id } ?: DRACULA
    }
}

object Palettes {
    val colorNames = listOf("green", "cyan", "blue", "purple", "pink", "red", "orange", "yellow")

    /** Theme id used in settings for an imported colour scheme. */
    const val CUSTOM_ID = "custom"

    /**
     * Resolves the palette for [themeId]: a built-in family, or [CUSTOM_ID] backed by the user's
     * imported scheme (falls back to Dracula when the JSON is missing or invalid).
     */
    fun resolve(themeId: String, dark: Boolean, customJson: String): TerminalPalette {
        if (themeId == CUSTOM_ID) {
            CustomPalette.parse(customJson)?.let { c -> return if (dark) c.dark else c.light }
        }
        return get(ThemeFamily.fromId(themeId), dark)
    }

    fun get(family: ThemeFamily, dark: Boolean): TerminalPalette = when (family) {
        ThemeFamily.DRACULA -> if (dark) draculaDark else draculaLight
        ThemeFamily.NORD -> if (dark) nordDark else nordLight
        ThemeFamily.SOLARIZED -> if (dark) solarizedDark else solarizedLight
        ThemeFamily.GRUVBOX -> if (dark) gruvboxDark else gruvboxLight
        ThemeFamily.MONOKAI -> if (dark) monokaiDark else monokaiLight
        ThemeFamily.CATPPUCCIN -> if (dark) catppuccinMocha else catppuccinLatte
        ThemeFamily.MATRIX -> if (dark) matrixDark else matrixLight
    }

    // ---- Dracula ----------------------------------------------------------------
    val draculaDark = TerminalPalette(
        id = "dracula", name = "Dracula", dark = true,
        bg = Color(0xFF282A36), bgAlt = Color(0xFF21222C), bgHighlight = Color(0xFF44475A),
        fg = Color(0xFFF8F8F2), fgDim = Color(0xFF6272A4), border = Color(0xFF44475A),
        red = Color(0xFFFF5555), green = Color(0xFF50FA7B), yellow = Color(0xFFF1FA8C), blue = Color(0xFF8BE9FD),
        purple = Color(0xFFBD93F9), cyan = Color(0xFF8BE9FD), orange = Color(0xFFFFB86C), pink = Color(0xFFFF79C6),
    )
    val draculaLight = TerminalPalette( // "Alucard" – official Dracula light
        id = "dracula", name = "Dracula (Alucard)", dark = false,
        bg = Color(0xFFFFFBEB), bgAlt = Color(0xFFF5F0DC), bgHighlight = Color(0xFFE8E2CC),
        fg = Color(0xFF1F1F1F), fgDim = Color(0xFF6C664B), border = Color(0xFFD9D3BC),
        red = Color(0xFFCB3A2A), green = Color(0xFF14710A), yellow = Color(0xFF846E15), blue = Color(0xFF036A96),
        purple = Color(0xFF644AC9), cyan = Color(0xFF036A96), orange = Color(0xFFA34D14), pink = Color(0xFFA3144D),
    )

    // ---- Nord -------------------------------------------------------------------
    val nordDark = TerminalPalette(
        id = "nord", name = "Nord", dark = true,
        bg = Color(0xFF2E3440), bgAlt = Color(0xFF3B4252), bgHighlight = Color(0xFF434C5E),
        fg = Color(0xFFECEFF4), fgDim = Color(0xFF7B88A1), border = Color(0xFF4C566A),
        red = Color(0xFFBF616A), green = Color(0xFFA3BE8C), yellow = Color(0xFFEBCB8B), blue = Color(0xFF81A1C1),
        purple = Color(0xFFB48EAD), cyan = Color(0xFF88C0D0), orange = Color(0xFFD08770), pink = Color(0xFFB48EAD),
    )
    val nordLight = TerminalPalette(
        id = "nord", name = "Nord Light", dark = false,
        bg = Color(0xFFECEFF4), bgAlt = Color(0xFFE5E9F0), bgHighlight = Color(0xFFD8DEE9),
        fg = Color(0xFF2E3440), fgDim = Color(0xFF6B7489), border = Color(0xFFD8DEE9),
        red = Color(0xFFBF616A), green = Color(0xFF6F8F5C), yellow = Color(0xFFB08D3B), blue = Color(0xFF5E81AC),
        purple = Color(0xFF8F6A8A), cyan = Color(0xFF3F8FA8), orange = Color(0xFFC0693F), pink = Color(0xFF8F6A8A),
    )

    // ---- Solarized --------------------------------------------------------------
    val solarizedDark = TerminalPalette(
        id = "solarized", name = "Solarized Dark", dark = true,
        bg = Color(0xFF002B36), bgAlt = Color(0xFF073642), bgHighlight = Color(0xFF0A4452),
        fg = Color(0xFF93A1A1), fgDim = Color(0xFF586E75), border = Color(0xFF0E4B5A),
        red = Color(0xFFDC322F), green = Color(0xFF859900), yellow = Color(0xFFB58900), blue = Color(0xFF268BD2),
        purple = Color(0xFF6C71C4), cyan = Color(0xFF2AA198), orange = Color(0xFFCB4B16), pink = Color(0xFFD33682),
    )
    val solarizedLight = TerminalPalette(
        id = "solarized", name = "Solarized Light", dark = false,
        bg = Color(0xFFFDF6E3), bgAlt = Color(0xFFEEE8D5), bgHighlight = Color(0xFFE4DDC6),
        fg = Color(0xFF586E75), fgDim = Color(0xFF93A1A1), border = Color(0xFFD9D2BD),
        red = Color(0xFFDC322F), green = Color(0xFF859900), yellow = Color(0xFFB58900), blue = Color(0xFF268BD2),
        purple = Color(0xFF6C71C4), cyan = Color(0xFF2AA198), orange = Color(0xFFCB4B16), pink = Color(0xFFD33682),
    )

    // ---- Gruvbox ----------------------------------------------------------------
    val gruvboxDark = TerminalPalette(
        id = "gruvbox", name = "Gruvbox Dark", dark = true,
        bg = Color(0xFF282828), bgAlt = Color(0xFF3C3836), bgHighlight = Color(0xFF504945),
        fg = Color(0xFFEBDBB2), fgDim = Color(0xFF928374), border = Color(0xFF504945),
        red = Color(0xFFFB4934), green = Color(0xFFB8BB26), yellow = Color(0xFFFABD2F), blue = Color(0xFF83A598),
        purple = Color(0xFFD3869B), cyan = Color(0xFF8EC07C), orange = Color(0xFFFE8019), pink = Color(0xFFD3869B),
    )
    val gruvboxLight = TerminalPalette(
        id = "gruvbox", name = "Gruvbox Light", dark = false,
        bg = Color(0xFFFBF1C7), bgAlt = Color(0xFFEBDBB2), bgHighlight = Color(0xFFD5C4A1),
        fg = Color(0xFF3C3836), fgDim = Color(0xFF928374), border = Color(0xFFD5C4A1),
        red = Color(0xFF9D0006), green = Color(0xFF79740E), yellow = Color(0xFFB57614), blue = Color(0xFF076678),
        purple = Color(0xFF8F3F71), cyan = Color(0xFF427B58), orange = Color(0xFFAF3A03), pink = Color(0xFF8F3F71),
    )

    // ---- Monokai ----------------------------------------------------------------
    val monokaiDark = TerminalPalette(
        id = "monokai", name = "Monokai", dark = true,
        bg = Color(0xFF272822), bgAlt = Color(0xFF1E1F1C), bgHighlight = Color(0xFF3E3D32),
        fg = Color(0xFFF8F8F2), fgDim = Color(0xFF75715E), border = Color(0xFF49483E),
        red = Color(0xFFF92672), green = Color(0xFFA6E22E), yellow = Color(0xFFE6DB74), blue = Color(0xFF66D9EF),
        purple = Color(0xFFAE81FF), cyan = Color(0xFF66D9EF), orange = Color(0xFFFD971F), pink = Color(0xFFF92672),
    )
    val monokaiLight = TerminalPalette(
        id = "monokai", name = "Monokai Light", dark = false,
        bg = Color(0xFFFAFAFA), bgAlt = Color(0xFFF0F0F0), bgHighlight = Color(0xFFE0E0E0),
        fg = Color(0xFF49483E), fgDim = Color(0xFF9A9A8E), border = Color(0xFFDADADA),
        red = Color(0xFFD01F5C), green = Color(0xFF5C9A00), yellow = Color(0xFF9A8A00), blue = Color(0xFF0089A8),
        purple = Color(0xFF7C4DFF), cyan = Color(0xFF0089A8), orange = Color(0xFFD97A00), pink = Color(0xFFD01F5C),
    )

    // ---- Catppuccin -------------------------------------------------------------
    val catppuccinMocha = TerminalPalette(
        id = "catppuccin", name = "Catppuccin Mocha", dark = true,
        bg = Color(0xFF1E1E2E), bgAlt = Color(0xFF181825), bgHighlight = Color(0xFF313244),
        fg = Color(0xFFCDD6F4), fgDim = Color(0xFF6C7086), border = Color(0xFF45475A),
        red = Color(0xFFF38BA8), green = Color(0xFFA6E3A1), yellow = Color(0xFFF9E2AF), blue = Color(0xFF89B4FA),
        purple = Color(0xFFCBA6F7), cyan = Color(0xFF94E2D5), orange = Color(0xFFFAB387), pink = Color(0xFFF5C2E7),
    )
    val catppuccinLatte = TerminalPalette(
        id = "catppuccin", name = "Catppuccin Latte", dark = false,
        bg = Color(0xFFEFF1F5), bgAlt = Color(0xFFE6E9EF), bgHighlight = Color(0xFFCCD0DA),
        fg = Color(0xFF4C4F69), fgDim = Color(0xFF9CA0B0), border = Color(0xFFBCC0CC),
        red = Color(0xFFD20F39), green = Color(0xFF40A02B), yellow = Color(0xFFDF8E1D), blue = Color(0xFF1E66F5),
        purple = Color(0xFF8839EF), cyan = Color(0xFF179299), orange = Color(0xFFFE640B), pink = Color(0xFFEA76CB),
    )

    // ---- Matrix (pure green phosphor) -------------------------------------------
    val matrixDark = TerminalPalette(
        id = "matrix", name = "Matrix", dark = true,
        bg = Color(0xFF000000), bgAlt = Color(0xFF060A06), bgHighlight = Color(0xFF0D1F0D),
        fg = Color(0xFF00FF41), fgDim = Color(0xFF008F11), border = Color(0xFF0D3B0D),
        red = Color(0xFF00FF41), green = Color(0xFF00FF41), yellow = Color(0xFF7CFC9A), blue = Color(0xFF00C832),
        purple = Color(0xFF00C832), cyan = Color(0xFF7CFC9A), orange = Color(0xFF00FF41), pink = Color(0xFF7CFC9A),
    )
    val matrixLight = TerminalPalette(
        id = "matrix", name = "Paper Terminal", dark = false,
        bg = Color(0xFFF4F4EF), bgAlt = Color(0xFFE9E9E1), bgHighlight = Color(0xFFD8D8CC),
        fg = Color(0xFF0F3D0F), fgDim = Color(0xFF6E8B6E), border = Color(0xFFC6CCBE),
        red = Color(0xFF0F3D0F), green = Color(0xFF117A11), yellow = Color(0xFF2B6B2B), blue = Color(0xFF0F5F0F),
        purple = Color(0xFF0F5F0F), cyan = Color(0xFF2B6B2B), orange = Color(0xFF117A11), pink = Color(0xFF2B6B2B),
    )
}

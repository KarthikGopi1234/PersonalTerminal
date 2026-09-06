package dev.personalterminal.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.personalterminal.ui.theme.Term
import kotlin.math.roundToInt

/** `user@android $ command` header line. */
@Composable
fun PromptLine(command: String, modifier: Modifier = Modifier, trailing: String? = null) {
    val p = Term.palette
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = p.prompt, fontWeight = FontWeight.Bold)) { append(Term.prompt) }
                withStyle(SpanStyle(color = p.fgDim)) { append(" $ ") }
                withStyle(SpanStyle(color = p.fg, fontWeight = FontWeight.Medium)) { append(command) }
            },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) Text(trailing, color = p.fgDim, style = MaterialTheme.typography.bodySmall)
    }
}

/** Faint comment line: `# some note` */
@Composable
fun Comment(text: String, modifier: Modifier = Modifier, color: Color = Term.palette.fgDim) {
    Text("# $text", color = color, style = MaterialTheme.typography.bodySmall, modifier = modifier)
}

/** A blinking block cursor ▌ */
@Composable
fun Cursor(modifier: Modifier = Modifier, color: Color = Term.palette.cursor) {
    if (dev.personalterminal.ui.theme.LocalAccessible.current) {
        Box(modifier.width(9.dp).height(16.dp).background(color)) // no blinking in accessibility mode
        return
    }
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530, easing = LinearEasing), RepeatMode.Reverse), label = "blink",
    )
    Box(modifier.width(9.dp).height(16.dp).alpha(alpha).background(color))
}

fun asciiBar(fraction: Float, width: Int = 10, filled: Char = '█', empty: Char = '░'): String {
    val f = (fraction.coerceIn(0f, 1f) * width).roundToInt()
    return buildString { repeat(f) { append(filled) }; repeat(width - f) { append(empty) } }
}

/** `████░░░░░░ 40%` */
@Composable
fun AsciiProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    width: Int = 10,
    color: Color = Term.palette.green,
    label: String? = null,
    showPercent: Boolean = true,
) {
    val p = Term.palette
    val pct = (fraction.coerceIn(0f, 1f) * 100).roundToInt()
    val f = (fraction.coerceIn(0f, 1f) * width).roundToInt()
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = color)) { append("█".repeat(f)) }
            withStyle(SpanStyle(color = p.fgDim.copy(alpha = 0.6f))) { append("░".repeat(width - f)) }
            if (showPercent) withStyle(SpanStyle(color = p.fgDim)) { append(" ${pct.toString().padStart(3)}%") }
            if (label != null) withStyle(SpanStyle(color = p.fgDim)) { append("  $label") }
        },
        style = LocalTextStyle.current,
        maxLines = 1,
        modifier = modifier,
    )
}

/** `[✓]` / `[ ]` / `[~]` bracketed checkbox glyph. */
@Composable
fun BracketCheckbox(checked: Boolean, color: Color, partial: Boolean = false, modifier: Modifier = Modifier) {
    val p = Term.palette
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = p.fgDim)) { append("[") }
            when {
                checked -> withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append("✓") }
                partial -> withStyle(SpanStyle(color = color)) { append("~") }
                else -> append(" ")
            }
            withStyle(SpanStyle(color = p.fgDim)) { append("]") }
        },
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier
            .then(if (dev.personalterminal.ui.theme.LocalAccessible.current) Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 40.dp) else Modifier)
            .semantics { stateDescription = if (checked) "done" else if (partial) "in progress" else "not done"; role = androidx.compose.ui.semantics.Role.Checkbox },
    )
}

/** Bordered panel with an optional `── title ──` caption, like a TUI box. */
@Composable
fun TerminalPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    titleColor: Color = Term.palette.fgDim,
    borderColor: Color = Term.palette.border,
    background: Color = Term.palette.bgAlt,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background, shape)
            .border(1.dp, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
    ) {
        if (title != null) {
            Text(
                text = "── $title ──",
                color = titleColor,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
        }
        content()
    }
}

/** `[ label ]` terminal-style button. */
@Composable
fun TermButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Term.palette.green,
    enabled: Boolean = true,
    filled: Boolean = false,
) {
    val p = Term.palette
    val accessible = dev.personalterminal.ui.theme.LocalAccessible.current
    val shape = RoundedCornerShape(4.dp)
    val bg = if (filled) color else Color.Transparent
    val fg = if (filled) p.bg else color
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .background(bg, shape)
            .border(1.dp, color, shape)
            .clickable(enabled = enabled, onClick = onClick, role = androidx.compose.ui.semantics.Role.Button)
            .then(if (accessible) Modifier.defaultMinSize(minHeight = 48.dp) else Modifier)
            .padding(horizontal = 12.dp, vertical = if (accessible) 12.dp else 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (filled) label else "[ $label ]", color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Small inline chip used for tags such as `daily` or `5/8 cups`. */
@Composable
fun Tag(text: String, color: Color = Term.palette.fgDim, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** Terminal text field:  `> label: value▌` */
@Composable
fun TermTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onImeAction: (() -> Unit)? = null,
    prompt: String = "> ",
) {
    val p = Term.palette
    val interaction = remember { MutableInteractionSource() }
    Column(modifier) {
        if (label != null) Text("$label:", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(p.bgAlt, RoundedCornerShape(4.dp))
                .border(1.dp, p.border, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            if (prompt.isNotEmpty()) Text(prompt, color = p.green, style = MaterialTheme.typography.bodyLarge)
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, color = p.fgDim.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.fg),
                    cursorBrush = SolidColor(p.cursor),
                    singleLine = singleLine,
                    interactionSource = interaction,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { onImeAction?.invoke() }, onGo = { onImeAction?.invoke() }, onSearch = { onImeAction?.invoke() }, onSend = { onImeAction?.invoke() },
                    ),
                    visualTransformation = visualTransformation,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Horizontal selectable options rendered like `(•) a  ( ) b`. */
@Composable
fun <T> RadioRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    color: Color = Term.palette.green,
) {
    val p = Term.palette
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEach { opt ->
            val isSel = opt == selected
            Text(
                text = (if (isSel) "(•) " else "( ) ") + label(opt),
                color = if (isSel) color else p.fgDim,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { onSelect(opt) }.padding(vertical = 4.dp),
            )
        }
    }
}

/** Section divider `────────` */
@Composable
fun Rule(modifier: Modifier = Modifier, color: Color = Term.palette.border) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** `key ........ value` aligned pair. */
@Composable
fun KeyValue(key: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Term.palette.fg) {
    val p = Term.palette
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = p.fgDim, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** Stepper `[-] 5 [+]` */
@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, min: Int = 0, max: Int = 999, color: Color = Term.palette.cyan, suffix: String = "") {
    val p = Term.palette
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("[-]", color = if (value > min) color else p.fgDim, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clickable(enabled = value > min) { onChange(value - 1) }.padding(4.dp))
        Text("$value$suffix", color = p.fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("[+]", color = if (value < max) color else p.fgDim, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clickable(enabled = value < max) { onChange(value + 1) }.padding(4.dp))
    }
}

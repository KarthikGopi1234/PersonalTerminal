package dev.personalterminal.ui.theme

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * CRT look, two layers:
 *  • an overlay drawn on every API level – repeating scanlines (one tiled gradient, no per-line
 *    draw calls) plus a vignette; and
 *  • on Android 13+ an AGSL [RuntimeShader] applied to the whole layer (overlay included, so the
 *    lines bend with the picture): barrel distortion, chromatic aberration, RGB sub-pixel mask
 *    and a little phosphor glow.
 * Software renderers (previews, the screenshot suite) simply skip the shader.
 */
@Composable
fun CrtOverlay(enabled: Boolean, palette: TerminalPalette, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val shaderModifier = if (Build.VERSION.SDK_INT >= 33) {
        val shader = remember { RuntimeShader(CRT_SHADER) }
        Modifier.graphicsLayer {
            shader.setFloatUniform("resolution", size.width, size.height)
            shader.setFloatUniform("bgColor", palette.bg.red, palette.bg.green, palette.bg.blue)
            renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
            clip = true
        }
    } else Modifier
    Box(Modifier.fillMaxSize().then(shaderModifier)) {
        content()
        Canvas(Modifier.fillMaxSize()) {
            val line = Color.Black.copy(alpha = if (palette.dark) 0.22f else 0.10f)
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent, 0.66f to Color.Transparent, 0.66f to line, 1f to line,
                    startY = 0f, endY = 3f * density, tileMode = TileMode.Repeated,
                ),
            )
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = if (palette.dark) 0.45f else 0.2f)), radius = size.maxDimension * 0.75f))
        }
    }
}

private const val CRT_SHADER = """
uniform shader content;
uniform float2 resolution;
uniform float3 bgColor;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution;
    // barrel distortion
    float2 c = uv * 2.0 - 1.0;
    float r2 = dot(c, c);
    c *= 1.0 + 0.045 * r2;
    float2 duv = (c + 1.0) * 0.5;
    if (duv.x < 0.0 || duv.x > 1.0 || duv.y < 0.0 || duv.y > 1.0) {
        return half4(half3(bgColor) * 0.35, 1.0);
    }
    float2 p = duv * resolution;
    // chromatic aberration
    float shift = 0.9 + 1.2 * r2;
    half4 col;
    col.r = content.eval(p + float2(shift, 0.0)).r;
    col.g = content.eval(p).g;
    col.b = content.eval(p - float2(shift, 0.0)).b;
    col.a = 1.0;
    // rgb sub-pixel mask
    float m = mod(p.x, 3.0);
    half3 mask = half3(m < 1.0 ? 1.06 : 0.94, (m >= 1.0 && m < 2.0) ? 1.06 : 0.94, m >= 2.0 ? 1.06 : 0.94);
    col.rgb *= mask;
    // glow: lift bright pixels a little
    half lum = dot(col.rgb, half3(0.299, 0.587, 0.114));
    col.rgb += col.rgb * lum * 0.12;
    return col;
}
"""

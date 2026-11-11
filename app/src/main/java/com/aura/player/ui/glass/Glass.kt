package com.aura.player.ui.glass

import android.annotation.SuppressLint
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Outline
import com.aura.player.art.AmbientColors
import com.aura.player.ui.theme.AuraColors
import kotlin.math.roundToInt

/**
 * AURA Liquid Glass engine.
 *
 * Compose has no built-in backdrop blur (Modifier.blur blurs the node's OWN content, not what is
 * behind it). Real backdrop blur is built here from three parts:
 *
 *  1. The scene (ambient + screens) is recorded into one [GraphicsLayer] (see AuraRoot).
 *  2. A floating glass surface re-draws that layer, shifted by its own position in the root, into
 *     its own small layer that carries a RenderEffect blur + saturation boost (API 31+).
 *  3. Tint, specular highlight and an illuminated edge are painted on top, cached per size.
 *
 * Step 2 is UNCHANGED from the original blur pipeline (same layer record, same blur radii, same saturation
 * boost, same base tint and fill alphas) - that look is the one to preserve. The Liquid Glass upgrade only
 * ADDS layers around it:
 *
 *   - API 33+: an AGSL edge-refraction pass chained AFTER the blur (lens-like bending + faint chromatic
 *     dispersion within a few dp of the rim). Falls back to blur-only if the shader cannot be built.
 *   - Directional rim light (bright top-left + reflected bottom-right, dark between), inner edge glow,
 *     top "lens cap" sheen, bottom bounce light, and a faint spectral fringe on the lower edge.
 *   - A soft layered drop shadow on floating surfaces (cut out of the glass interior so it never darkens it).
 *
 * Only a handful of floating surfaces (nav, mini-player, menus, dialogs, sheets) use step 2.
 * Glass inside scrolling content uses the cheap "flat" material (no backdrop sampling), which
 * is also the fallback for API < 31 or when the user picks "Frosted (no blur)".
 */
@Immutable
class Backdrop(val layer: GraphicsLayer)

val LocalBackdrop = compositionLocalOf<Backdrop?> { null }

/** 0 = frosted (no blur), 1 = balanced, 2 = high. */
val LocalGlassQuality = compositionLocalOf { 1 }

enum class GlassLevel { Subtle, Regular, Strong }

private val saturationFilter: ColorFilter by lazy {
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.45f) })
}

/** Per-level material strengths for the NEW layers (fill alphas of the original blur look stay as they were). */
private class Material(
    val topFill: Float,
    val botFill: Float,
    val rim: Float,
    val innerGlow: Float,
    val sheen: Float,
    val shadow: Float,
    val fringe: Boolean,
)

private fun GlassLevel.material(): Material = when (this) {
    GlassLevel.Subtle -> Material(0.10f, 0.03f, 0.80f, 0.60f, 0.70f, 0.55f, false)
    GlassLevel.Regular -> Material(0.17f, 0.06f, 1.00f, 0.90f, 1.00f, 0.80f, false)
    GlassLevel.Strong -> Material(0.24f, 0.10f, 1.15f, 1.20f, 1.25f, 1.00f, true)
}

/**
 * AGSL edge refraction (API 33+). Samples the already-blurred backdrop, pulling it inward by up to
 * ~0.65 x [depth] px right at the rim (quadratic falloff), so the edge band reads like a thick glass
 * bevel. With dispersion > 0 the R and B channels are sampled at slightly different offsets.
 * Built once per glass instance; uniforms are refreshed whenever the surface size changes.
 */
@RequiresApi(33)
private class RefractionFx {
    private val shader = RuntimeShader(AGSL)

    fun effect(blurPx: Float, w: Float, h: Float, radius: Float, depth: Float, dispersion: Float): ComposeRenderEffect {
        val blur = AndroidRenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
        return try {
            shader.setFloatUniform("size", w, h)
            shader.setFloatUniform("radius", radius)
            shader.setFloatUniform("depth", depth)
            shader.setFloatUniform("dispersion", dispersion)
            val refract = AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
            // inner = blur first, outer = refraction of the blurred result.
            AndroidRenderEffect.createChainEffect(refract, blur).asComposeRenderEffect()
        } catch (t: Throwable) {
            blur.asComposeRenderEffect()
        }
    }

    companion object {
        private const val AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float depth;
uniform float dispersion;

float sdRoundRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + float2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}

half4 main(float2 coord) {
    float2 c = size * 0.5;
    float2 p = coord - c;
    float d = sdRoundRect(p, c, radius);
    float t = clamp(1.0 + d / depth, 0.0, 1.0);
    if (t <= 0.0) {
        return content.eval(coord);
    }
    float2 g = float2(
        sdRoundRect(p + float2(1.0, 0.0), c, radius) - sdRoundRect(p - float2(1.0, 0.0), c, radius),
        sdRoundRect(p + float2(0.0, 1.0), c, radius) - sdRoundRect(p - float2(0.0, 1.0), c, radius)
    );
    float len = length(g);
    if (len < 0.0001) {
        return content.eval(coord);
    }
    g = g / len;
    float2 off = -g * (t * t) * depth * 0.65;
    half4 col = content.eval(coord + off);
    if (dispersion > 0.0) {
        col.r = content.eval(coord + off * (1.0 + 0.16 * dispersion)).r;
        col.b = content.eval(coord + off * (1.0 - 0.16 * dispersion)).b;
    }
    return col;
}
"""
    }
}

private fun createRefractionOrNull(): RefractionFx? = try {
    if (Build.VERSION.SDK_INT >= 33) RefractionFx() else null
} catch (t: Throwable) {
    null
}

@SuppressLint("NewApi") // RefractionFx is only ever created / used when SDK_INT >= 33 (see createRefractionOrNull)
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    level: GlassLevel = GlassLevel.Regular,
    tint: Color = Color.White,
    borderAlpha: Float = 1f,
    /** Soft drop shadow. null = automatic (on for floating surfaces that sample the backdrop). */
    elevated: Boolean? = null,
): Modifier = composed {
    val backdrop = LocalBackdrop.current
    val quality = LocalGlassQuality.current
    val density = LocalDensity.current
    val canBlur = backdrop != null && quality > 0 && Build.VERSION.SDK_INT >= 31
    val blurLayer = if (canBlur) rememberGraphicsLayer() else null
    var origin by remember { mutableStateOf(Offset.Zero) }
    val blurPx = with(density) { (if (quality >= 2) 30.dp else 18.dp).toPx() }
    val refraction = remember(canBlur) { if (canBlur) createRefractionOrNull() else null }

    if (blurLayer != null) {
        SideEffect {
            blurLayer.clip = true
            blurLayer.colorFilter = saturationFilter
        }
    }

    val overBackdrop = backdrop != null
    val floating = elevated ?: overBackdrop
    val mat = level.material()

    this
        .onGloballyPositioned { if (canBlur) origin = it.positionInRoot() }
        .drawWithCache {
            val w = size.width
            val h = size.height
            // A 0 x 0 node would crash gradient shaders (RadialGradient requires radius > 0): draw nothing.
            if (w < 2f || h < 2f) {
                return@drawWithCache onDrawWithContent { drawContent() }
            }
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply {
                when (outline) {
                    is Outline.Rounded -> addRoundRect(outline.roundRect)
                    is Outline.Rectangle -> addRect(outline.rect)
                    is Outline.Generic -> addPath(outline.path)
                }
            }
            val minSide = minOf(w, h)
            val corner = ((outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f)
                .coerceIn(0f, minSide / 2f)

            // ---- backdrop pass: original blur, optionally followed by AGSL edge refraction ----
            if (blurLayer != null) {
                val depth = minOf(14.dp.toPx(), minSide * 0.45f)
                blurLayer.renderEffect = refraction?.effect(blurPx, w, h, corner, depth, if (quality >= 2) 1f else 0f)
                    ?: BlurEffect(blurPx, blurPx, TileMode.Clamp)
            }

            val base: Color = when {
                canBlur -> AuraColors.Navy.copy(alpha = 0.30f)
                overBackdrop -> AuraColors.Ink.copy(alpha = 0.90f) // no blur available: near-opaque frosted
                else -> Color.Transparent
            }
            val fill = Brush.verticalGradient(
                listOf(tint.copy(alpha = mat.topFill), tint.copy(alpha = mat.botFill)),
            )
            // Specular: soft bloom from the upper-left, like light grazing a curved pane.
            val specular = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                center = Offset(w * 0.22f, -h * 0.15f),
                radius = maxOf(w, h) * 0.75f,
            )
            // Counter-reflection low on the opposite corner (blue/cyan caustic).
            val caustic = Brush.radialGradient(
                colors = listOf(AuraColors.Cyan.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(w * 0.95f, h * 1.1f),
                radius = maxOf(w, h) * 0.6f,
            )
            // Lens-cap sheen hugging the top edge + bounce light along the bottom edge (glass thickness cues).
            val sheen = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.15f * mat.sheen),
                0.34f to Color.Transparent,
                startY = 0f, endY = h,
            )
            val bounce = Brush.verticalGradient(
                0.70f to Color.Transparent,
                1f to AuraColors.Cyan.copy(alpha = 0.10f * mat.sheen),
                startY = 0f, endY = h,
            )
            // Directional rim: lit top-left, reflected bottom-right, dark in between (light from upper-left).
            val a = borderAlpha * mat.rim
            val rim = Brush.linearGradient(
                0f to Color.White.copy(alpha = (0.80f * a).coerceAtMost(1f)),
                0.22f to Color.White.copy(alpha = 0.16f * a),
                0.50f to Color.White.copy(alpha = 0.04f * a),
                0.78f to Color.White.copy(alpha = 0.20f * a),
                1f to Color.White.copy(alpha = (0.52f * a).coerceAtMost(1f)),
                start = Offset.Zero,
                end = Offset(w, h),
            )
            val innerGlow = Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.22f * mat.innerGlow * borderAlpha),
                0.5f to Color.White.copy(alpha = 0.03f * mat.innerGlow * borderAlpha),
                1f to Color.White.copy(alpha = 0.15f * mat.innerGlow * borderAlpha),
                start = Offset.Zero,
                end = Offset(w, h),
            )
            // Faint spectral fringe on the lower edge (dispersion cue), only on the thickest material.
            val fringe: Brush? = if (mat.fringe && w > 40f) Brush.horizontalGradient(
                0.10f to Color.Transparent,
                0.32f to Color(0xFFFFB067).copy(alpha = 0.34f * borderAlpha),
                0.52f to Color(0xFFFF6FD8).copy(alpha = 0.30f * borderAlpha),
                0.70f to Color(0xFF6FB6FF).copy(alpha = 0.34f * borderAlpha),
                0.90f to Color.Transparent,
                startX = 0f, endX = w,
            ) else null

            val rimStroke = Stroke(width = 1.1.dp.toPx())
            val glowStroke = Stroke(width = 6.dp.toPx())
            val fringeStroke = Stroke(width = 1.6.dp.toPx())
            val fringeTop = h - minOf(h * 0.5f, 14.dp.toPx())
            val shadowOffset = 6.dp.toPx()
            val shadowWidths = floatArrayOf(3.dp.toPx(), 9.dp.toPx(), 18.dp.toPx())
            val shadowAlphas = floatArrayOf(0.10f * mat.shadow, 0.07f * mat.shadow, 0.045f * mat.shadow)
            val sizeI = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1))

            onDrawWithContent {
                if (floating) {
                    // Soft layered shadow, cut out of the glass interior so it never muddies the transparency.
                    clipPath(path, clipOp = ClipOp.Difference) {
                        translate(top = shadowOffset) {
                            for (i in shadowWidths.indices) {
                                drawPath(path, Color.Black.copy(alpha = shadowAlphas[i]), style = Stroke(width = shadowWidths[i]))
                            }
                        }
                    }
                }
                if (blurLayer != null && backdrop != null) {
                    blurLayer.record(size = sizeI) {
                        translate(-origin.x, -origin.y) { drawLayer(backdrop.layer) }
                    }
                    clipPath(path) { drawLayer(blurLayer) }
                }
                if (base != Color.Transparent) drawPath(path, base)
                drawPath(path, fill)
                clipPath(path) {
                    drawRect(specular)
                    drawRect(caustic)
                    drawRect(sheen)
                    drawRect(bounce)
                    drawPath(path, innerGlow, style = glowStroke) // only the inner half survives the clip
                    if (fringe != null) clipRect(top = fringeTop) { drawPath(path, fringe, style = fringeStroke) }
                }
                drawPath(path, rim, style = rimStroke)
                drawContent()
            }
        }
}

/** Soft coloured glow behind an element (artwork, play button). Cheap radial gradient, no blur. */
fun Modifier.glow(color: Color, radius: Dp = 160.dp, alpha: Float = 0.45f): Modifier = drawWithCache {
    val r = radius.toPx()
    val brush = Brush.radialGradient(
        listOf(color.copy(alpha = alpha), Color.Transparent),
        center = Offset(size.width / 2, size.height / 2),
        radius = r,
    )
    onDrawBehind { drawCircle(brush, r, Offset(size.width / 2, size.height / 2)) }
}

/**
 * The AURA ambient scene: near-black base lit by three drifting colour fields.
 * Colours are animated with tween and only READ inside the draw lambda, so a track change
 * never recomposes anything – it only redraws one canvas.
 */
@Composable
fun AmbientBackground(colors: AmbientColors, modifier: Modifier = Modifier.fillMaxSize(), intensity: Float = 1f) {
    val a by animateColorAsState(colors.a, tween(900), label = "ambA")
    val b by animateColorAsState(colors.b, tween(900), label = "ambB")
    val c by animateColorAsState(colors.c, tween(900), label = "ambC")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(AuraColors.Bg)
        drawRect(
            Brush.radialGradient(
                listOf(a.copy(alpha = 0.55f * intensity), Color.Transparent),
                center = Offset(w * 0.10f, h * 0.08f), radius = w * 1.05f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(b.copy(alpha = 0.42f * intensity), Color.Transparent),
                center = Offset(w * 0.98f, h * 0.38f), radius = w * 0.95f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(c.copy(alpha = 0.30f * intensity), Color.Transparent),
                center = Offset(w * 0.15f, h * 0.98f), radius = w * 1.0f,
            ),
        )
        // Gentle vignette keeps text contrast.
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent, 0.55f to Color.Transparent, 1f to AuraColors.Bg.copy(alpha = 0.55f),
            ),
        )
    }
}

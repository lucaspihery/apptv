package io.github.lucaspihery.apptv.core.designsystem.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

enum class ThemeMode { DARK, LIGHT }

/** Couleurs d'accent proposées dans les réglages. */
enum class Accent(val color: Color, val secondary: Color) {
    BLUE(Color(0xFF4C8DFF), Color(0xFF8A5CFF)),
    VIOLET(Color(0xFF9B6BFF), Color(0xFFFF6BB5)),
    CORAL(Color(0xFFFF7A6B), Color(0xFFFFB04C)),
    MINT(Color(0xFF2ED3A0), Color(0xFF3AA8FF)),
}

/**
 * Niveau d'effets du verre.
 * - [FULL] : API ≥ 31. Au jalon 1, même rendu que [SIMULATED] ; le vrai flou
 *   d'arrière-plan (`RenderEffect`) ne sera activé qu'après mesures (jalon 7).
 * - [SIMULATED] : API < 31 (TCL). Fond flouté précalculé, couches translucides,
 *   bordures spéculaires. Aucun flou en direct.
 * - [REDUCED] : réglage « Effets réduits » : aplats translucides, pas d'image de fond.
 */
enum class GlassEffects { FULL, SIMULATED, REDUCED }

@Immutable
data class GlassPalette(
    val mode: ThemeMode,
    val accent: Accent,
    /** Dégradé de fond (haut-gauche → bas-droite). */
    val backgroundStart: Color,
    val backgroundEnd: Color,
    /** Remplissage translucide des panneaux. */
    val panelFill: Color,
    val panelFillFocused: Color,
    /** Reflet spéculaire : fort en haut à gauche, faible en bas à droite. */
    val rimStrong: Color,
    val rimWeak: Color,
    val innerGlow: Color,
    val shadow: Color,
    val content: Color,
    val contentMuted: Color,
    val error: Color,
) {
    companion object {
        fun of(mode: ThemeMode, accent: Accent): GlassPalette = when (mode) {
            ThemeMode.DARK -> GlassPalette(
                mode, accent,
                backgroundStart = Color(0xFF0A0F24),
                backgroundEnd = Color(0xFF1A1440),
                panelFill = Color.White.copy(alpha = 0.08f),
                panelFillFocused = Color.White.copy(alpha = 0.18f),
                rimStrong = Color.White.copy(alpha = 0.55f),
                rimWeak = Color.White.copy(alpha = 0.10f),
                innerGlow = Color.White.copy(alpha = 0.06f),
                shadow = Color.Black.copy(alpha = 0.45f),
                content = Color(0xFFF5F7FF),
                contentMuted = Color(0xFFB8BED6),
                error = Color(0xFFFF8A80),
            )
            ThemeMode.LIGHT -> GlassPalette(
                mode, accent,
                backgroundStart = Color(0xFFE8EEFB),
                backgroundEnd = Color(0xFFCAD5F3),
                panelFill = Color.White.copy(alpha = 0.50f),
                panelFillFocused = Color.White.copy(alpha = 0.80f),
                rimStrong = Color.White.copy(alpha = 0.95f),
                rimWeak = Color.White.copy(alpha = 0.35f),
                innerGlow = Color.White.copy(alpha = 0.25f),
                shadow = Color(0xFF1C2550).copy(alpha = 0.18f),
                content = Color(0xFF0E1330),
                contentMuted = Color(0xFF4A5275),
                error = Color(0xFFC62828),
            )
        }
    }
}

val LocalGlassPalette = staticCompositionLocalOf { GlassPalette.of(ThemeMode.DARK, Accent.BLUE) }
val LocalGlassEffects = staticCompositionLocalOf { GlassEffects.SIMULATED }

fun defaultGlassEffects(reducedEffects: Boolean): GlassEffects = when {
    reducedEffects -> GlassEffects.REDUCED
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> GlassEffects.FULL
    else -> GlassEffects.SIMULATED
}

@Composable
fun ApptvTheme(
    mode: ThemeMode = ThemeMode.DARK,
    accent: Accent = Accent.BLUE,
    reducedEffects: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = remember(mode, accent) { GlassPalette.of(mode, accent) }
    val effects = remember(reducedEffects) { defaultGlassEffects(reducedEffects) }
    val colorScheme = when (mode) {
        ThemeMode.DARK -> darkColorScheme(
            primary = accent.color,
            secondary = accent.secondary,
            background = palette.backgroundStart,
            surface = palette.backgroundEnd,
            onSurface = palette.content,
            onBackground = palette.content,
            error = palette.error,
        )
        ThemeMode.LIGHT -> lightColorScheme(
            primary = accent.color,
            secondary = accent.secondary,
            background = palette.backgroundStart,
            surface = palette.backgroundEnd,
            onSurface = palette.content,
            onBackground = palette.content,
            error = palette.error,
        )
    }
    CompositionLocalProvider(
        LocalGlassPalette provides palette,
        LocalGlassEffects provides effects,
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = ApptvTypography, content = content)
    }
}

object Glass {
    val palette: GlassPalette
        @Composable get() = LocalGlassPalette.current
    val effects: GlassEffects
        @Composable get() = LocalGlassEffects.current
}

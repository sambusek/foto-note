package com.sambusek.fotonote

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat

enum class ThemeMode(val label: String) { SYSTEM("Sistema"), LIGHT("Chiaro"), DARK("Scuro") }

/** Colori d'accento proposti; il primo (null) usa i colori del sistema (Material You, Android 12+). */
val ACCENTS: List<Pair<String, Color?>> = listOf(
    "Sistema" to null,
    "Blu" to Color(0xFF3F6FD8),
    "Lavanda" to Color(0xFF6B6BE0),
    "Viola" to Color(0xFF8E4BC2),
    "Verde" to Color(0xFF2E7D4F),
    "Petrolio" to Color(0xFF00838F),
    "Arancio" to Color(0xFFD9661F),
    "Rosso" to Color(0xFFC62828),
    "Grafite" to Color(0xFF5F6B76),
)

val dynamicColorAvailable = Build.VERSION.SDK_INT >= 31

/** Indice dell'accento personalizzato, scelto col selettore (dopo quelli della tavolozza). */
val CUSTOM_ACCENT: Int get() = ACCENTS.size

/** Tema scelto dall'utente; condiviso da tutte le schermate, si applica al volo. */
object ThemeStore {
    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set
    var accent by mutableIntStateOf(0)
        private set
    /** Colore scelto col selettore. */
    var custom by mutableStateOf(Color(0xFF3F6FD8))
        private set
    private var loaded = false

    private fun prefs(c: Context) = c.getSharedPreferences("fotonote", Context.MODE_PRIVATE)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(c)
        mode = ThemeMode.entries.firstOrNull { it.name == p.getString("theme", null) } ?: ThemeMode.SYSTEM
        accent = p.getInt("accent", 0).coerceIn(0, CUSTOM_ACCENT)
        custom = Color(p.getInt("accent_custom", 0xFF3F6FD8.toInt()))
        if (accent == 0 && !dynamicColorAvailable) accent = 1
    }

    fun setMode(c: Context, m: ThemeMode) {
        mode = m
        prefs(c).edit().putString("theme", m.name).apply()
    }

    fun setCustom(c: Context, color: Color) {
        custom = color
        accent = CUSTOM_ACCENT
        prefs(c).edit().putInt("accent_custom", color.toArgb()).putInt("accent", CUSTOM_ACCENT).apply()
    }

    /** Nome dell'accento attuale. */
    val accentName: String get() = if (accent == CUSTOM_ACCENT) "Personalizzato" else ACCENTS[accent].first

    fun setAccent(c: Context, i: Int) {
        accent = i
        prefs(c).edit().putInt("accent", i).apply()
    }
}

/** Stessa tinta dell'accento con luminosità (e saturazione) diverse. */
internal fun tone(c: Color, lightness: Float, saturation: Float = 1f): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(c.toArgb(), hsl)
    hsl[1] = (hsl[1] * saturation).coerceIn(0f, 1f)
    hsl[2] = lightness
    return Color(ColorUtils.HSLToColor(hsl))
}

/** Schema Material costruito da un colore d'accento, su superfici neutre. */
private fun accentScheme(seed: Color, dark: Boolean): ColorScheme =
    if (dark) darkColorScheme().copy(
        primary = tone(seed, 0.78f), onPrimary = tone(seed, 0.16f),
        primaryContainer = tone(seed, 0.30f), onPrimaryContainer = tone(seed, 0.90f),
        secondary = tone(seed, 0.75f, 0.35f), onSecondary = tone(seed, 0.18f, 0.35f),
        secondaryContainer = tone(seed, 0.28f, 0.35f), onSecondaryContainer = tone(seed, 0.90f, 0.35f),
        surfaceTint = tone(seed, 0.78f),
    ) else lightColorScheme().copy(
        primary = tone(seed, 0.42f), onPrimary = Color.White,
        primaryContainer = tone(seed, 0.90f), onPrimaryContainer = tone(seed, 0.15f),
        secondary = tone(seed, 0.40f, 0.35f), onSecondary = Color.White,
        secondaryContainer = tone(seed, 0.90f, 0.35f), onSecondaryContainer = tone(seed, 0.15f, 0.35f),
        surfaceTint = tone(seed, 0.42f),
    )

/** Forme compatte: angoli piccoli ovunque. */
private val CompactShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    ThemeStore.load(ctx)
    val dark = when (ThemeStore.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val seed = if (ThemeStore.accent == CUSTOM_ACCENT) ThemeStore.custom else ACCENTS[ThemeStore.accent].second
    val scheme = when {
        seed != null -> accentScheme(seed, dark)
        dynamicColorAvailable -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        else -> if (dark) darkColorScheme() else lightColorScheme()
    }
    // icone delle barre di sistema chiare o scure secondo il tema dell'app
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, shapes = CompactShapes, content = content)
}

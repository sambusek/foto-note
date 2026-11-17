package com.sambusek.fotonote

import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class SkinId(val label: String) { ANDROID("Android"), PLATINUM("Platinum"), PLAN9("Plan 9"), SYSTEM7("Sistema 7"), BEOS("BeOS") }

enum class Desk { DOTS, FLAT, CHECKER }
enum class TitleBar { PLATINUM, STRIPES_BW, TAG, TAB, MATERIAL }
enum class Shadow { SOFT, HARD, NONE }

/** Tutto ciò che distingue una skin dall'altra. */
data class Skin(
    val id: SkinId,
    // scrivania
    val desk: Color,
    val deskTex: Color,
    val deskStyle: Desk,
    // superfici e testi
    val bg: Color,          // corpo delle finestre
    val field: Color,       // campi di testo, liste
    val ink: Color,
    val ink2: Color,
    val off: Color,
    val hi: Color,          // luce del rilievo
    val lo: Color,          // ombra del rilievo / linee secondarie
    val accent: Color,
    val onBg: Color,        // fondo di un controllo attivo
    val onInk: Color,       // testo su controllo attivo
    val pressBg: Color,     // fondo di un controllo premuto
    val pressInk: Color,
    val canvas: Color,      // fondo dietro la foto
    val ok: Color,
    val ko: Color,
    // finestre
    val winBorder: Color,
    val winBorderW: Dp,
    val shadow: Shadow,
    val titleBar: TitleBar,
    val titleBg: Color,
    val menuBg: Color,
    val menuLine: Color,
    // controlli
    val bevel: Boolean,
    val ctlBg: Color,
    val ctlBorder: Color,
    val pushRadius: Dp,
    val progress: List<Color>,
    val alertFill: Color,
    // caratteri
    val titleFont: FontFamily,
    val bodyFont: FontFamily,
    val smallFont: FontFamily,
    val lowercase: Boolean,
)

private val PlexMono = FontFamily(
    Font(R.font.small_regular, FontWeight.Normal),
    Font(R.font.small_bold, FontWeight.Bold),
)
private val PixelBold = FontFamily(
    Font(R.font.chicago_like, FontWeight.Normal),
    Font(R.font.chicago_like, FontWeight.Bold),
)

/** Grigi Platinum di fine anni '90, scrivania azzurro-viola, selezione lavanda. */
val PlatinumSkin = Skin(
    id = SkinId.PLATINUM,
    desk = Color(0xFF6363A5), deskTex = Color(0xFF6D6DAE), deskStyle = Desk.DOTS,
    bg = Color(0xFFDDDDDD), field = Color.White,
    ink = Color(0xFF111111), ink2 = Color(0xFF444444), off = Color(0xFFA0A0A0),
    hi = Color.White, lo = Color(0xFF999999),
    accent = Color(0xFF6666CC), onBg = Color(0xFFCCCCFF), onInk = Color(0xFF111111),
    pressBg = Color(0xFF999999), pressInk = Color(0xFF111111),
    canvas = Color(0xFFA4A4A4), ok = Color(0xFF2E9E3E), ko = Color(0xFFCC3333),
    winBorder = Color(0xFF111111), winBorderW = 1.dp, shadow = Shadow.SOFT,
    titleBar = TitleBar.PLATINUM, titleBg = Color(0xFFDDDDDD),
    menuBg = Color(0xFFDDDDDD), menuLine = Color(0xFF111111),
    bevel = true, ctlBg = Color(0xFFDDDDDD), ctlBorder = Color(0xFF111111), pushRadius = 0.dp,
    progress = listOf(Color(0xFFCCCCFF), Color(0xFF6666CC), Color(0xFF9999FF)),
    alertFill = Color(0xFFFFCC00),
    titleFont = FontFamily.SansSerif, bodyFont = FontFamily.SansSerif, smallFont = PlexMono,
    lowercase = false,
)

/** Plan 9 / rio / acme: grigio piatto, bordi viola-blu, tag azzurro pallido, testi su giallo pallido. */
val Plan9Skin = Skin(
    id = SkinId.PLAN9,
    desk = Color(0xFF777777), deskTex = Color(0xFF777777), deskStyle = Desk.FLAT,
    bg = Color(0xFFFFFFEA), field = Color(0xFFFFFFEA),
    ink = Color(0xFF000000), ink2 = Color(0xFF333333), off = Color(0xFF99994C),
    hi = Color(0xFFFFFFEA), lo = Color(0xFF99994C),
    accent = Color(0xFF8888CC), onBg = Color(0xFFEEEE9E), onInk = Color(0xFF000000),
    pressBg = Color(0xFF448844), pressInk = Color(0xFFEAFFEA),
    canvas = Color(0xFFFFFFEA), ok = Color(0xFF448844), ko = Color(0xFFAA0000),
    winBorder = Color(0xFF8888CC), winBorderW = 4.dp, shadow = Shadow.NONE,
    titleBar = TitleBar.TAG, titleBg = Color(0xFFEAFFFF),
    menuBg = Color(0xFFEAFFFF), menuLine = Color(0xFF8888CC),
    bevel = false, ctlBg = Color(0xFFEAFFEA), ctlBorder = Color(0xFF88CC88), pushRadius = 0.dp,
    progress = listOf(Color(0xFF99994C), Color(0xFF99994C), Color(0xFF99994C)),
    alertFill = Color(0xFFEEEE9E),
    titleFont = PlexMono, bodyFont = PlexMono, smallFont = PlexMono,
    lowercase = true,
)

/** Bianco e nero a 1 bit: scacchiera retinata, righe nere, controlli che si invertono. */
val System7Skin = Skin(
    id = SkinId.SYSTEM7,
    desk = Color(0xFFFFFFFF), deskTex = Color(0xFF000000), deskStyle = Desk.CHECKER,
    bg = Color.White, field = Color.White,
    ink = Color.Black, ink2 = Color(0xFF222222), off = Color(0xFF999999),
    hi = Color.White, lo = Color.Black,
    accent = Color.Black, onBg = Color.Black, onInk = Color.White,
    pressBg = Color.Black, pressInk = Color.White,
    canvas = Color.White, ok = Color.Black, ko = Color.White,
    winBorder = Color.Black, winBorderW = 1.dp, shadow = Shadow.HARD,
    titleBar = TitleBar.STRIPES_BW, titleBg = Color.White,
    menuBg = Color.White, menuLine = Color.Black,
    bevel = false, ctlBg = Color.White, ctlBorder = Color.Black, pushRadius = 7.dp,
    progress = listOf(Color.Black, Color.Black, Color.Black),
    alertFill = Color.White,
    titleFont = PixelBold, bodyFont = FontFamily.SansSerif, smallFont = PlexMono,
    lowercase = false,
)

/**
 * Omaggio a BeOS: scrivania blu, finestre grigie con la linguetta gialla del titolo
 * (stretta quanto il titolo, in alto a sinistra), rilievi morbidi, controlli attivi in giallo.
 */
val BeSkin = PlatinumSkin.copy(
    id = SkinId.BEOS,
    desk = Color(0xFF336698), deskTex = Color(0xFF336698), deskStyle = Desk.FLAT,
    bg = Color(0xFFD8D8D8), field = Color.White,
    ink = Color(0xFF000000), ink2 = Color(0xFF404040), off = Color(0xFF9A9A9A),
    hi = Color.White, lo = Color(0xFF989898),
    accent = Color(0xFF3A6EA5), onBg = Color(0xFFFFE066), onInk = Color(0xFF000000),
    pressBg = Color(0xFFA8A8A8), pressInk = Color(0xFF000000),
    canvas = Color(0xFF9C9C9C),
    winBorder = Color(0xFF505050), winBorderW = 1.dp, shadow = Shadow.NONE,
    titleBar = TitleBar.TAB, titleBg = Color(0xFFFFCB00),
    menuBg = Color(0xFFD8D8D8), menuLine = Color(0xFF505050),
    bevel = true, ctlBg = Color(0xFFE4E4E4), ctlBorder = Color(0xFF505050), pushRadius = 3.dp,
    progress = listOf(Color(0xFF8CC8FF), Color(0xFF3298FF), Color(0xFF1E6FCC)),
    alertFill = Color(0xFFFFCB00),
)

/**
 * Aspetto Android: Material 3 con i colori di sistema (Material You) e il tema chiaro o
 * scuro del telefono. I colori veri si calcolano a runtime con [androidSkin].
 */
fun androidSkin(cs: ColorScheme): Skin = PlatinumSkin.copy(
    id = SkinId.ANDROID,
    desk = cs.surface, deskTex = cs.surface, deskStyle = Desk.FLAT,
    bg = cs.surface, field = cs.surfaceContainerHighest,
    ink = cs.onSurface, ink2 = cs.onSurfaceVariant, off = cs.onSurface.copy(alpha = 0.38f),
    hi = cs.outlineVariant, lo = cs.outlineVariant,
    accent = cs.primary, onBg = cs.secondaryContainer, onInk = cs.onSecondaryContainer,
    pressBg = cs.surfaceContainerHighest, pressInk = cs.onSurface,
    canvas = cs.surfaceContainerHighest, ok = cs.primary, ko = cs.error,
    winBorder = Color.Transparent, winBorderW = 0.dp, shadow = Shadow.NONE,
    titleBar = TitleBar.MATERIAL, titleBg = cs.surface,
    menuBg = cs.surface, menuLine = Color.Transparent,
    bevel = false, ctlBg = cs.surfaceContainerHigh, ctlBorder = Color.Transparent, pushRadius = 20.dp,
    progress = listOf(cs.primary, cs.primary, cs.primary),
    alertFill = cs.tertiaryContainer,
    titleFont = FontFamily.Default, bodyFont = FontFamily.Default, smallFont = FontFamily.Default,
    lowercase = false,
)

/** Segnaposto per l'aspetto Android (i colori reali arrivano dal tema di sistema). */
val AndroidSkinPlaceholder = androidSkin(lightColorScheme())

fun skinFor(id: SkinId) = when (id) {
    SkinId.ANDROID -> AndroidSkinPlaceholder
    SkinId.PLATINUM -> PlatinumSkin
    SkinId.PLAN9 -> Plan9Skin
    SkinId.SYSTEM7 -> System7Skin
    SkinId.BEOS -> BeSkin
}

val LocalSkin = staticCompositionLocalOf { PlatinumSkin }

/** Skin attiva, condivisa da tutte le schermate dell'app. */
object SkinStore {
    var current by mutableStateOf(AndroidSkinPlaceholder)
        private set
    private var loaded = false

    /** Carica la skin salvata; alla prima installazione è l'aspetto Android. */
    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val name = c.getSharedPreferences("fotonote", Context.MODE_PRIVATE).getString("skin", null)
        current = skinFor(SkinId.entries.firstOrNull { it.name == name } ?: SkinId.ANDROID)
    }

    fun set(c: Context, id: SkinId) {
        c.getSharedPreferences("fotonote", Context.MODE_PRIVATE).edit().putString("skin", id.name).apply()
        current = skinFor(id)
    }
}

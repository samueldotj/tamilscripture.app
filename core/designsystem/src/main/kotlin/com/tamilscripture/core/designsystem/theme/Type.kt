package com.tamilscripture.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tamilscripture.core.designsystem.R

private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Scripture and display face (design: 'Mukta Malar'). Static files ship 400 and 600; 700 maps to 600. */
val MuktaMalar = FontFamily(
    Font(R.font.mukta_malar_regular, FontWeight.Normal),
    Font(R.font.mukta_malar_regular, FontWeight.Medium),
    Font(R.font.mukta_malar_semibold, FontWeight.SemiBold),
    Font(R.font.mukta_malar_semibold, FontWeight.Bold),
    Font(R.font.mukta_malar_semibold, FontWeight.ExtraBold),
)

/** UI face (design: 'Noto Sans Tamil'), a variable font. */
val NotoSansTamil = FontFamily(
    variable(R.font.noto_sans_tamil, 400),
    variable(R.font.noto_sans_tamil, 500),
    variable(R.font.noto_sans_tamil, 600),
    variable(R.font.noto_sans_tamil, 700),
    variable(R.font.noto_sans_tamil, 800),
)

val NotoSerifTamil = FontFamily(
    variable(R.font.noto_serif_tamil, 400),
    variable(R.font.noto_serif_tamil, 600),
    variable(R.font.noto_serif_tamil, 700),
)

/** Latin, Greek and English scripture (design: 'Noto Sans'). */
val NotoSans = FontFamily(
    variable(R.font.noto_sans, 400),
    variable(R.font.noto_sans, 600),
    variable(R.font.noto_sans, 700),
)

/** English verses in a shared verse image (the website's 'Lora'; Latin only, one weight). */
val Lora = FontFamily(Font(R.font.lora, FontWeight.Normal))

/** References and the numeral in a shared verse image (the website's 'Cormorant Garamond'), a variable font. */
val CormorantGaramond = FontFamily(
    variable(R.font.cormorant_garamond, 400),
    variable(R.font.cormorant_garamond, 600),
)

/** The reader's typeface choice (A-2.7). */
enum class ScriptureFace(val family: FontFamily) {
    MuktaMalar(com.tamilscripture.core.designsystem.theme.MuktaMalar),
    NotoSansTamil(com.tamilscripture.core.designsystem.theme.NotoSansTamil),
    NotoSerifTamil(com.tamilscripture.core.designsystem.theme.NotoSerifTamil),
}

private val trimmed = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/** True when [this] has Tamil letters, so it is set in the Tamil face rather than Noto Sans. */
fun CharSequence.hasTamil(): Boolean = any { it in '\u0B80'..'\u0BFF' }

/**
 * Named text styles from the design. Sizes are in sp so they follow the system font scale.
 * Faces follow the website: Tamil in the reader's Tamil face (its --tamil, Mukta Malar unless
 * changed in settings), Latin in Noto Sans (its --sans and --en), kickers in the sans face.
 */
@Immutable
data class TsType(
    /** The reader's Tamil face. */
    val scripture: FontFamily = MuktaMalar,
    /** The interface face: [scripture] for a Tamil interface, Noto Sans for English. */
    val ui: FontFamily = scripture,
    /** Screen title, "வாசிப்புத் திட்டங்கள்" (26/700). */
    val screenTitle: TextStyle = TextStyle(fontFamily = ui, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    /** App bar title, "யோவான் 3" (18/600). */
    val barTitle: TextStyle = TextStyle(fontFamily = ui, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    /** Card headline, "யோவான் 3" in continue reading (24/700). */
    val headline: TextStyle = TextStyle(fontFamily = ui, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    /** Sheet title (22/700). */
    val sheetTitle: TextStyle = TextStyle(fontFamily = ui, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    /** Card title (17–18/700). */
    val cardTitle: TextStyle = TextStyle(fontFamily = ui, fontWeight = FontWeight.Bold, fontSize = 17.sp),
    /** Section heading inside scripture (15/600, amber). */
    val sectionHeading: TextStyle = TextStyle(fontFamily = ui, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    /** Kicker: 11/700, tracking 0.12em, uppercase; the website sets it in the sans face. */
    val kicker: TextStyle = TextStyle(fontFamily = NotoSansTamil, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.12.em),
    /** Body (15, line height 1.75). */
    val body: TextStyle = TextStyle(fontFamily = ui, fontSize = 15.sp, lineHeight = 26.sp, lineHeightStyle = trimmed),
    /** Row title (15–16/600–700). */
    val rowTitle: TextStyle = TextStyle(fontFamily = ui, fontWeight = FontWeight.Bold, fontSize = 15.sp),
    val label: TextStyle = TextStyle(fontFamily = ui, fontWeight = FontWeight.Bold, fontSize = 13.sp),
    val labelSmall: TextStyle = TextStyle(fontFamily = ui, fontWeight = FontWeight.Bold, fontSize = 12.sp),
    val caption: TextStyle = TextStyle(fontFamily = ui, fontSize = 12.sp),
    val captionSmall: TextStyle = TextStyle(fontFamily = ui, fontSize = 11.sp),
    val greek: TextStyle = TextStyle(fontFamily = NotoSans, fontSize = 19.sp),
) {
    /** The face for scripture: the Tamil face for Tamil, Noto Sans for English and other Latin versions. */
    fun scriptureFamily(tamil: Boolean): FontFamily = if (tamil) scripture else NotoSans

    fun scripture(size: TextUnit, lineHeightEm: Float = 1.8f, tamil: Boolean = true): TextStyle =
        TextStyle(fontFamily = scriptureFamily(tamil), fontSize = size, lineHeight = (size.value * lineHeightEm).sp, lineHeightStyle = trimmed)

    /** [scripture] in the face for [text]'s script. */
    fun scripture(size: TextUnit, lineHeightEm: Float, text: CharSequence): TextStyle = scripture(size, lineHeightEm, text.hasTamil())
}

val LocalTsType = staticCompositionLocalOf { TsType() }

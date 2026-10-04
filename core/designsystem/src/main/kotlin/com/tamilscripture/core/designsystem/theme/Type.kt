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

/** The reader's typeface choice (A-2.7). */
enum class ScriptureFace(val family: FontFamily) {
    MuktaMalar(com.tamilscripture.core.designsystem.theme.MuktaMalar),
    NotoSansTamil(com.tamilscripture.core.designsystem.theme.NotoSansTamil),
    NotoSerifTamil(com.tamilscripture.core.designsystem.theme.NotoSerifTamil),
}

private val trimmed = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/** Named text styles from the design. Sizes are in sp so they follow the system font scale. */
@Immutable
data class TsType(
    val scripture: FontFamily = MuktaMalar,
    /** Screen title, "வாசிப்புத் திட்டங்கள்" (26/700). */
    val screenTitle: TextStyle = TextStyle(fontFamily = MuktaMalar, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    /** App bar title, "யோவான் 3" (18/600). */
    val barTitle: TextStyle = TextStyle(fontFamily = MuktaMalar, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    /** Card headline, "யோவான் 3" in continue reading (24/700). */
    val headline: TextStyle = TextStyle(fontFamily = MuktaMalar, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    /** Sheet title (22/700). */
    val sheetTitle: TextStyle = TextStyle(fontFamily = MuktaMalar, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    /** Card title in Mukta (17–18/700). */
    val cardTitle: TextStyle = TextStyle(fontFamily = MuktaMalar, fontWeight = FontWeight.Bold, fontSize = 17.sp),
    /** Section heading inside scripture (15/600, amber). */
    val sectionHeading: TextStyle = TextStyle(fontFamily = MuktaMalar, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    /** Kicker: 11/700, tracking 0.12em, uppercase. */
    val kicker: TextStyle = TextStyle(fontFamily = NotoSansTamil, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.12.em),
    /** Body (15, line height 1.75). */
    val body: TextStyle = TextStyle(fontFamily = NotoSansTamil, fontSize = 15.sp, lineHeight = 26.sp, lineHeightStyle = trimmed),
    /** Row title (15–16/600–700). */
    val rowTitle: TextStyle = TextStyle(fontFamily = NotoSansTamil, fontWeight = FontWeight.Bold, fontSize = 15.sp),
    val label: TextStyle = TextStyle(fontFamily = NotoSansTamil, fontWeight = FontWeight.Bold, fontSize = 13.sp),
    val labelSmall: TextStyle = TextStyle(fontFamily = NotoSansTamil, fontWeight = FontWeight.Bold, fontSize = 12.sp),
    val caption: TextStyle = TextStyle(fontFamily = NotoSansTamil, fontSize = 12.sp),
    val captionSmall: TextStyle = TextStyle(fontFamily = NotoSansTamil, fontSize = 11.sp),
    val greek: TextStyle = TextStyle(fontFamily = NotoSans, fontSize = 19.sp),
) {
    fun scripture(size: TextUnit, lineHeightEm: Float = 1.8f): TextStyle =
        TextStyle(fontFamily = scripture, fontSize = size, lineHeight = (size.value * lineHeightEm).sp, lineHeightStyle = trimmed)
}

val LocalTsType = staticCompositionLocalOf { TsType() }

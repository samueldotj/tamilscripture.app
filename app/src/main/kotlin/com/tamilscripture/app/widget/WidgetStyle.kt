package com.tamilscripture.app.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.color.DayNightColorProvider
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tamilscripture.app.LinkActivity
import com.tamilscripture.app.R
import com.tamilscripture.core.data.settings.Typeface as TamilFace
import com.tamilscripture.core.designsystem.theme.hasTamil
import com.tamilscripture.core.model.ContentManifest
import com.tamilscripture.core.model.Passage

/**
 * The "Classical" widget look (Claude Design handoff, Bible Widgets - Classical): paper
 * ground with a hairline frame and a 7 dp corner, small uppercase gold kickers, serif
 * display type, muted figures. Light by day; by night the same layout on the dark paper
 * of the shared verse images.
 */
internal object W {
    val BG = ColorProvider(day = Color(0xFFF3F2F2), night = Color(0xFF1F1D1C))
    val TEXT = ColorProvider(day = Color(0xFF201F1D), night = Color(0xFFF3F2F2))
    /** neutral-700 */
    val MUTED = ColorProvider(day = Color(0xFF605D5D), night = Color(0xFFBAB6B6))
    /** neutral-600, for readings already done. */
    val DONE = ColorProvider(day = Color(0xFF7D7979), night = Color(0xFF9B9797))
    /** neutral-500, an unread reading's ring. */
    val RING = ColorProvider(day = Color(0xFF9B9797), night = Color(0xFF7D7979))
    val ACCENT = ColorProvider(day = Color(0xFFB68235), night = Color(0xFFE1AD66))
    /** accent-700, gold dark enough for small text. */
    val ACCENT_TEXT = ColorProvider(day = Color(0xFF7D5411), night = Color(0xFFFACB8D))
    /** Ink at 16% over the paper. */
    val DIVIDER = ColorProvider(day = Color(0x29201F1D), night = Color(0x33F3F2F2))

    /** Widths at or above this get the medium layout (the design's 364 dp; a 4×2 cell). */
    val MEDIUM: Dp = 250.dp

    val serif = FontFamily.Serif

    /** 9.5 px uppercase, letter-spaced in the design (Glance has no tracking). */
    fun kicker(color: ColorProvider = ACCENT_TEXT) = TextStyle(color = color, fontSize = 10.sp)
    fun small(color: ColorProvider = MUTED) = TextStyle(color = color, fontSize = 11.sp)

    /** The layout whose text view carries the reader's Tamil face. */
    fun tamilLayout(face: TamilFace): Int = when (face) {
        TamilFace.MuktaMalar -> R.layout.widget_text_mukta
        TamilFace.NotoSansTamil -> R.layout.widget_text_sans
        TamilFace.NotoSerifTamil -> R.layout.widget_text_serif
    }

    /** Opens [p] the way a website link does, so MainActivity has one way in. */
    fun open(context: Context, m: ContentManifest?, p: Passage): Action = link(context, passageUrl(m, p))

    fun link(context: Context, url: String): Action = actionStartActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .setComponent(ComponentName(context, LinkActivity::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    fun passageUrl(m: ContentManifest?, p: Passage): String {
        val slug = m?.book(p.book)?.slug ?: p.book.lowercase()
        return "https://www.tamilscripture.com/${p.version.lowercase()}/$slug/${p.chapter}" + (p.verse?.let { ".$it" } ?: "")
    }
}

/** The layout for Tamil text, from [W.tamilLayout]; provided round each widget's content. */
internal val LocalTamilLayout = staticCompositionLocalOf { R.layout.widget_text_mukta }

/**
 * Widget text. Tamil is set in the reader's Tamil face, as everywhere on the website; Glance
 * can only name system fonts, so Tamil goes through a text view whose layout carries the
 * bundled face. Other text keeps the design's system faces.
 */
@Composable
internal fun WText(text: String, style: TextStyle, modifier: GlanceModifier = GlanceModifier, maxLines: Int = Int.MAX_VALUE) {
    if (!text.hasTamil()) {
        Text(text, style = style, maxLines = maxLines, modifier = modifier)
        return
    }
    val context = LocalContext.current
    val views = RemoteViews(context.packageName, LocalTamilLayout.current)
    val spans = SpannableString(text)
    fun span(what: Any) = spans.setSpan(what, 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    // Tamil has no italic, so a style's italic is left out.
    if ((style.fontWeight ?: FontWeight.Normal) != FontWeight.Normal) span(StyleSpan(Typeface.BOLD))
    if (style.textDecoration?.contains(TextDecoration.Underline) == true) span(UnderlineSpan())
    if (style.textDecoration?.contains(TextDecoration.LineThrough) == true) span(StrikethroughSpan())
    views.setTextViewText(R.id.widget_text, spans)
    style.fontSize?.let { views.setTextViewTextSize(R.id.widget_text, TypedValue.COMPLEX_UNIT_SP, it.value) }
    views.setInt(R.id.widget_text, "setMaxLines", maxLines)
    // The widget's colours are all day and night pairs (see [W]).
    (style.color as? DayNightColorProvider)?.let { color ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) views.setColorInt(R.id.widget_text, "setTextColor", color.day.toArgb(), color.night.toArgb())
        else views.setTextColor(R.id.widget_text, color.getColor(context).toArgb())
    }
    // In a Box of its own: a row drops its other children when one is the remote view itself.
    Box(modifier) { AndroidRemoteViews(views) }
}

/** The widget's card: a 1 dp hairline frame round the paper, then [content] padded inside. */
@Composable
internal fun ClassicalCard(onClick: Action, medium: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Box(GlanceModifier.fillMaxSize().background(W.DIVIDER).cornerRadius(7.dp).padding(1.dp)) {
        Column(
            GlanceModifier.fillMaxSize().background(W.BG).cornerRadius(6.dp).clickable(onClick)
                .then(if (medium) GlanceModifier.padding(horizontal = 18.dp, vertical = 16.dp) else GlanceModifier.padding(14.dp)),
            content = content,
        )
    }
}

/** A 1 dp rule across the width. */
@Composable
internal fun Rule(color: ColorProvider = W.DIVIDER) {
    Box(GlanceModifier.fillMaxWidth().height(1.dp).background(color)) {}
}

/** Text with a 1 dp gold rule under it, as the design's border-bottom. */
@Composable
internal fun Underlined(text: String, style: TextStyle, gap: Dp = 5.dp) {
    Column {
        WText(text, style, maxLines = 1)
        Box(GlanceModifier.height(gap)) {}
        Rule(W.ACCENT)
    }
}

/** The plan's progress: a hairline with a 3 dp gold bar along [fraction] of [width]. */
@Composable
internal fun ProgressRule(fraction: Float, width: Dp) {
    Box(GlanceModifier.fillMaxWidth().height(3.dp), contentAlignment = androidx.glance.layout.Alignment.CenterStart) {
        Box(GlanceModifier.fillMaxWidth().height(1.dp).background(W.DIVIDER)) {}
        val w = width * fraction.coerceIn(0f, 1f)
        if (w > 0.dp) Box(GlanceModifier.width(w).height(3.dp).background(W.ACCENT)) {}
    }
}

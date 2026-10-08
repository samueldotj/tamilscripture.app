package com.tamilscripture.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.TextDecoration
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.tamilscripture.app.R
import com.tamilscripture.core.data.GraphHost
import com.tamilscripture.core.data.settings.Settings
import com.tamilscripture.core.model.DailyVerse
import com.tamilscripture.core.model.Passage
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.model.label
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/**
 * Home-screen widget (roadmap M7-6), the verse of the day in the Classical design: small
 * (2×2) is the verse, its reference and version; medium (4×2) adds the date and "Read the
 * chapter". Text comes from a downloaded Bible or the online cache, so the widget never
 * waits on the network when the reader has used the app before.
 */
class TodayWidget : GlanceAppWidget() {
    // The real size, so the verse is set as large as fits and shows whole lines.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = (context.applicationContext as GraphHost).graph
        val settings = graph.settings.settings.firstOrNull() ?: Settings()
        val manifest = withTimeoutOrNull(5_000) { graph.content.manifest.filterNotNull().first() }
        val lang = settings.uiLang
        val tamil = lang == UiLang.Tamil
        val daily = DailyVerse.forDate()
        val text = withTimeoutOrNull(5_000) {
            graph.content.chapter(settings.version, daily.book, daily.chapter).firstOrNull()?.value?.verseText(daily.verse)
        }
        val version = manifest?.version(settings.version)
        val v = VerseOfDay(
            kicker = if (tamil) "இன்றைய வசனம்" else "VERSE OF THE DAY",
            verse = text ?: if (tamil) "வாசிக்கத் தட்டவும்" else "Tap to read",
            tamilText = (version?.lang ?: "ta") == "ta",
            reference = manifest?.book(daily.book)?.label(lang, daily.chapter, daily.verse) ?: daily.toString(),
            version = version?.short ?: settings.version,
            date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", if (tamil) Locale.forLanguageTag("ta") else Locale.UK)),
            readChapter = if (tamil) "அதிகாரத்தை வாசி" else "Read the chapter",
            tamilUi = tamil,
        )
        val openVerse = W.open(context, manifest, Passage(settings.version, daily.book, daily.chapter, daily.verse))
        val openChapter = W.open(context, manifest, Passage(settings.version, daily.book, daily.chapter))
        provideContent { VerseWidget(v, openVerse, openChapter) }
    }

    companion object {
        /** Refresh after the reader moves on, and when the day turns. */
        suspend fun refresh(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(TodayWidget::class.java).forEach { TodayWidget().update(context, it) }
            manager.getGlanceIds(PlanWidget::class.java).forEach { PlanWidget().update(context, it) }
        }
    }
}

internal class VerseOfDay(
    val kicker: String,
    val verse: String,
    /** Tamil has no italic: the small size's italic is for other scripts only. */
    val tamilText: Boolean,
    val reference: String,
    val version: String,
    val date: String,
    val readChapter: String,
    val tamilUi: Boolean,
)

/**
 * The largest of [sizes] at which [text] is estimated to fit [lines]-high box [width] wide,
 * and the lines it may take. Glance cannot measure text, so this goes by characters.
 */
internal fun fitText(text: String, tamil: Boolean, width: Dp, height: Dp, sizes: List<Int>): Pair<Int, Int> {
    val perChar = if (tamil) 0.75f else 0.52f
    val leading = if (tamil) 1.6f else 1.35f
    for (s in sizes) {
        val perLine = (width.value / (s * perChar)).coerceAtLeast(1f)
        val need = ceil(text.length / perLine).toInt()
        val fit = (height.value / (s * leading)).toInt()
        if (need <= fit) return s to need.coerceAtLeast(1)
    }
    val s = sizes.last()
    return s to (height.value / (s * leading)).toInt().coerceAtLeast(1)
}

@Composable
internal fun VerseWidget(v: VerseOfDay, openVerse: Action, openChapter: Action) {
    val size = LocalSize.current
    val medium = size.width >= W.MEDIUM
    ClassicalCard(openVerse, medium) {
        if (medium) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(v.kicker, style = W.kicker(), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                Text(v.date, style = W.small(), maxLines = 1)
            }
            Box(GlanceModifier.height(6.dp)) {}
            Rule()
        } else {
            Underlined(v.kicker, W.kicker())
        }
        Box(GlanceModifier.height(10.dp)) {}
        // What is left between the header and the footer for the verse.
        val inner = size.width - (if (medium) 38.dp else 30.dp)
        val room = size.height - (if (medium) 34.dp + 21.dp + 10.dp + 18.dp else 30.dp + 22.dp + 10.dp + 16.dp)
        val (fontSize, lines) = fitText(v.verse, v.tamilText, inner, room, if (medium) listOf(23, 21, 19, 17, 15, 14, 13, 12) else listOf(16, 15, 14, 13, 12))
        Text(
            v.verse,
            style = TextStyle(
                color = W.TEXT, fontSize = fontSize.sp, fontFamily = W.serif,
                fontStyle = if (!medium && !v.tamilText) FontStyle.Italic else FontStyle.Normal,
            ),
            maxLines = lines,
            modifier = GlanceModifier.defaultWeight(),
        )
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            if (medium) {
                Text(v.reference, style = TextStyle(color = W.TEXT, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                // The version only when the row has room for it beside a long Tamil reference and the link.
                val perChar = if (v.tamilUi) 12 * 0.62f else 12 * 0.55f
                val used = (v.reference.length + v.version.length + 1 + v.readChapter.length) * perChar + 17
                if (used <= inner.value) Text(" " + v.version, style = W.small().copy(fontSize = 12.sp), maxLines = 1)
                Box(GlanceModifier.defaultWeight()) {}
                Row(GlanceModifier.clickable(openChapter), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        v.readChapter,
                        style = TextStyle(
                            color = W.ACCENT_TEXT, fontSize = 12.sp, textDecoration = TextDecoration.Underline,
                            fontStyle = if (v.tamilUi) FontStyle.Normal else FontStyle.Italic,
                        ),
                        maxLines = 1,
                    )
                    Box(GlanceModifier.width(4.dp)) {}
                    Image(ImageProvider(R.drawable.widget_arrow), null, GlanceModifier.size(13.dp), colorFilter = ColorFilter.tint(W.ACCENT_TEXT))
                }
            } else {
                Text(v.reference, style = TextStyle(color = W.TEXT, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                Text(v.version, style = W.small(), maxLines = 1)
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

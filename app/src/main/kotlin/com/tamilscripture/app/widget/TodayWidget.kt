package com.tamilscripture.app.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.tamilscripture.app.LinkActivity
import com.tamilscripture.core.data.GraphHost
import com.tamilscripture.core.data.settings.Settings
import com.tamilscripture.core.designsystem.theme.DarkTsColors
import com.tamilscripture.core.designsystem.theme.LightTsColors
import com.tamilscripture.core.model.ContentManifest
import com.tamilscripture.core.model.DailyVerse
import com.tamilscripture.core.model.Passage
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.model.label
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Home-screen widget (roadmap M7-6): the verse of the day, and where the reader left
 * off. Both open the app on the passage. Text comes from a downloaded Bible or the
 * online cache, so the widget never waits on the network when the reader has used the
 * app before.
 */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = (context.applicationContext as GraphHost).graph
        val settings = graph.settings.settings.firstOrNull() ?: Settings()
        val manifest = withTimeoutOrNull(5_000) { graph.content.manifest.filterNotNull().first() }
        val tamil = settings.uiLang == UiLang.Tamil
        val daily = DailyVerse.forDate()
        val text = withTimeoutOrNull(5_000) {
            graph.content.chapter(settings.version, daily.book, daily.chapter).firstOrNull()?.value?.verseText(daily.verse)
        }
        val lang = settings.uiLang
        val dailyRef = manifest?.book(daily.book)?.label(lang, daily.chapter, daily.verse) ?: daily.toString()
        val last = settings.lastRead
        val lastRef = last?.let { manifest?.book(it.book)?.label(lang, it.chapter) }

        provideContent {
            Column(
                GlanceModifier.fillMaxSize().background(BG).cornerRadius(20.dp).padding(16.dp)
                    .clickable(open(context, manifest, Passage(settings.version, daily.book, daily.chapter, daily.verse))),
            ) {
                Text(
                    (if (tamil) "இன்றைய வசனம் · " else "Verse of the day · ") + dailyRef,
                    style = TextStyle(color = ACCENT, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text ?: if (tamil) "வாசிக்கத் தட்டவும்" else "Tap to read",
                    style = TextStyle(color = INK, fontSize = 16.sp),
                    maxLines = 6,
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (last != null && lastRef != null) {
                    Row(
                        GlanceModifier.fillMaxWidth().padding(top = 8.dp).clickable(open(context, manifest, last)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            (if (tamil) "தொடர்ந்து வாசிக்க · " else "Continue reading · ") + lastRef + " ›",
                            style = TextStyle(color = MUTED, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                }
            }
        }
    }

    companion object {
        // The app's own palette, light by day and dark at night.
        private val BG = ColorProvider(day = LightTsColors.bg, night = DarkTsColors.bg)
        private val INK = ColorProvider(day = LightTsColors.ink, night = DarkTsColors.ink)
        private val MUTED = ColorProvider(day = LightTsColors.muted, night = DarkTsColors.muted)
        private val ACCENT = ColorProvider(day = LightTsColors.accent, night = DarkTsColors.accent)

        /** Opens the passage the way a website link does, so MainActivity has one way in. */
        private fun open(context: Context, m: ContentManifest?, p: Passage) = actionStartActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(link(m, p)))
                .setComponent(ComponentName(context, LinkActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

        private fun link(m: ContentManifest?, p: Passage): String {
            val slug = m?.book(p.book)?.slug ?: p.book.lowercase()
            return "https://www.tamilscripture.com/${p.version.lowercase()}/$slug/${p.chapter}" + (p.verse?.let { ".$it" } ?: "")
        }

        /** Refresh after the reader moves on, so "Continue reading" stays current. */
        suspend fun refresh(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(TodayWidget::class.java).forEach { TodayWidget().update(context, it) }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

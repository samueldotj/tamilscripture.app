package com.tamilscripture.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.tamilscripture.app.R
import com.tamilscripture.core.data.GraphHost
import com.tamilscripture.core.data.settings.Settings
import com.tamilscripture.core.model.Passage
import com.tamilscripture.core.model.Plans
import com.tamilscripture.core.model.UiLang
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Bible plan widget (Classical design): the active plan's day as a large figure, the
 * progress along it, and today's readings. Small (2×2) counts what is read and the minutes
 * left; medium (4×2) adds the percentage, the streak and each reading, ticked when read.
 */
class PlanWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = (context.applicationContext as GraphHost).graph
        val settings = graph.settings.settings.firstOrNull() ?: Settings()
        val lang = settings.uiLang
        val tamil = lang == UiLang.Tamil
        val manifest = withTimeoutOrNull(5_000) { graph.content.manifest.filterNotNull().first() }
        val progress = graph.plans.progress.firstOrNull().orEmpty()
        val planId = graph.plans.activePlan.firstOrNull()?.takeIf { it in progress } ?: progress.keys.firstOrNull()
        val plan = manifest?.let { m -> Plans.all(m.books, graph.plans.community.value).firstOrNull { it.id == planId } }
        val prog = planId?.let { progress[it] }
        val openPlans = W.link(context, "https://www.tamilscripture.com/plans")

        val data = if (plan == null || prog == null) null else {
            val stats = Plans.stats(plan, prog)
            val day = ChronoUnit.DAYS.between(prog.start, LocalDate.now()).toInt().coerceIn(0, plan.days - 1)
            val readings = Plans.schedule(plan)[day].map { p ->
                val first = p.units.first()
                Reading(
                    Plans.passageLabel(p.units, lang),
                    minutes = p.chapters * 4,
                    done = Plans.key(day, p.trackIndex) in prog.done,
                    open = W.open(context, manifest, Passage(settings.version, first.book.code, first.chapter, first.verses?.first)),
                )
            }
            val read = readings.count { it.done }
            val left = readings.filterNot { it.done }.sumOf { it.minutes }
            PlanDay(
                name = plan.title(lang).let { if (tamil) it else it.uppercase() },
                day = day + 1,
                ofDays = if (tamil) "${plan.days} நாட்களில்" else "of ${plan.days} days",
                percent = stats.percent,
                done = if (tamil) "${readings.size}-இல் $read வாசித்தது" else "$read of ${readings.size} read",
                left = when {
                    left == 0 -> if (tamil) "இன்று முடிந்தது" else "Done for today"
                    tamil -> "$left நிமி மீதம்"
                    else -> "$left min left"
                },
                streak = stats.streak.takeIf { it >= 2 }?.let { if (tamil) "தொடர்ந்து $it நாட்கள்" else "A $it-day streak" },
                todays = if (tamil) "இன்றைய வாசிப்பு" else "TODAY'S READING",
                min = if (tamil) "நிமி" else "min",
                readings = readings,
            )
        }
        val empty = if (tamil) "வாசிப்புத் திட்டம் இல்லை" to "ஒரு திட்டத்தைத் தொடங்குங்கள் ›" else "No reading plan" to "Choose a plan ›"
        provideContent { PlanWidgetContent(data, empty, openPlans) }
    }
}

internal class Reading(val name: String, val minutes: Int, val done: Boolean, val open: Action)

internal class PlanDay(
    val name: String,
    val day: Int,
    val ofDays: String,
    val percent: Int,
    val done: String,
    val left: String,
    val streak: String?,
    val todays: String,
    val min: String,
    val readings: List<Reading>,
)

@Composable
internal fun PlanWidgetContent(d: PlanDay?, empty: Pair<String, String>, openPlans: Action) {
    val size = LocalSize.current
    val medium = size.width >= W.MEDIUM
    ClassicalCard(openPlans, medium) {
        if (d == null) {
            Text(empty.first, style = TextStyle(color = W.TEXT, fontSize = 20.sp, fontFamily = W.serif), modifier = GlanceModifier.defaultWeight())
            Text(empty.second, style = TextStyle(color = W.ACCENT_TEXT, fontSize = 12.sp, fontStyle = FontStyle.Italic))
            return@ClassicalCard
        }
        if (!medium) {
            DayFigure(d, withPercent = false)
            ProgressRule(d.percent / 100f, size.width - 30.dp)
            Box(GlanceModifier.height(8.dp)) {}
            Row(GlanceModifier.fillMaxWidth()) {
                Text(d.done, style = TextStyle(color = W.TEXT, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                Text(d.left, style = W.small(), maxLines = 1)
            }
            return@ClassicalCard
        }
        // Medium: the day on the left (5 parts), a hairline, today's readings on the right (7 parts).
        val inner = size.width - 38.dp
        val leftW = (inner - 18.dp) * (5f / 12f)
        Row(GlanceModifier.fillMaxSize()) {
            Column(GlanceModifier.width(leftW).fillMaxHeight().padding(end = 18.dp)) {
                DayFigure(d, withPercent = true)
                ProgressRule(d.percent / 100f, leftW - 18.dp)
                d.streak?.let {
                    Box(GlanceModifier.height(8.dp)) {}
                    Text(it, style = W.small().copy(fontStyle = FontStyle.Italic), maxLines = 1)
                }
            }
            Box(GlanceModifier.width(1.dp).fillMaxHeight().background(W.DIVIDER)) {}
            Column(GlanceModifier.defaultWeight().fillMaxHeight().padding(start = 18.dp)) {
                Row(GlanceModifier.fillMaxWidth()) {
                    Text(d.todays, style = W.kicker(W.MUTED), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    Text(d.done, style = W.kicker(W.MUTED), maxLines = 1)
                }
                Box(GlanceModifier.height(6.dp)) {}
                d.readings.take(3).forEach { r -> ReadingRow(r, d.min) }
            }
        }
    }
}

@Composable
private fun androidx.glance.layout.ColumnScope.DayFigure(d: PlanDay, withPercent: Boolean) {
    Text(d.name, style = W.kicker(), maxLines = 1)
    Column(GlanceModifier.defaultWeight().fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${d.day}", style = TextStyle(color = W.TEXT, fontSize = 44.sp, fontFamily = W.serif), maxLines = 1)
        Text(d.ofDays + if (withPercent) " · ${d.percent}%" else "", style = W.small(), maxLines = 1)
    }
}

@Composable
private fun ReadingRow(r: Reading, min: String) {
    Column(GlanceModifier.fillMaxWidth().clickable(r.open)) {
        Row(GlanceModifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                ImageProvider(if (r.done) R.drawable.widget_check_done else R.drawable.widget_check_todo), null,
                GlanceModifier.size(15.dp), colorFilter = ColorFilter.tint(if (r.done) W.ACCENT else W.RING),
            )
            Box(GlanceModifier.width(10.dp)) {}
            Text(
                r.name,
                style = TextStyle(
                    color = if (r.done) W.DONE else W.TEXT, fontSize = 15.sp, fontFamily = W.serif, fontWeight = FontWeight.Bold,
                    textDecoration = if (r.done) TextDecoration.LineThrough else TextDecoration.None,
                ),
                maxLines = 1, modifier = GlanceModifier.defaultWeight(),
            )
            Text("${r.minutes} $min", style = W.small(), maxLines = 1)
        }
        Rule()
    }
}

class PlanWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlanWidget()
}

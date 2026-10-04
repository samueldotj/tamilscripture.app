package com.tamilscripture.feature.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamilscripture.core.data.settings.Settings
import com.tamilscripture.core.designsystem.component.Kicker
import com.tamilscripture.core.designsystem.component.PillStyle
import com.tamilscripture.core.designsystem.component.TsCard
import com.tamilscripture.core.designsystem.component.TsPillButton
import com.tamilscripture.core.designsystem.component.TsProgress
import com.tamilscripture.core.designsystem.component.TsSegmented
import com.tamilscripture.core.designsystem.icon.TsIcons
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.Passage
import com.tamilscripture.core.model.PlanProgress
import com.tamilscripture.core.model.Plans
import com.tamilscripture.core.model.ReadingPlan
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val MONTHS_TA = listOf("ஜனவரி", "பிப்ரவரி", "மார்ச்", "ஏப்ரல்", "மே", "ஜூன்", "ஜூலை", "ஆகஸ்ட்", "செப்டம்பர்", "அக்டோபர்", "நவம்பர்", "டிசம்பர்")
private val MONTHS_TA_SHORT = listOf("ஜன", "பிப்", "மார்", "ஏப்", "மே", "ஜூன்", "ஜூலை", "ஆக", "செப்", "அக்", "நவ", "டிச")
private val WEEK_TA = listOf("ஞா", "தி", "செ", "பு", "வி", "வெ", "ச")
private val WEEK_EN = listOf("S", "M", "T", "W", "T", "F", "S")

/** திட்டங்கள் · Reading plans (design 1F; website feature_reading_plans.md). */
@Composable
fun PlansScreen(onRead: (Passage) -> Unit) {
    val services = LocalAppServices.current
    val graph = services.graph
    val c = Ts.colors
    val lang = LocalUiLang.current
    val scope = rememberCoroutineScope()
    val manifest by graph.content.manifest.collectAsStateWithLifecycle()
    val progress by graph.plans.progress.collectAsStateWithLifecycle(emptyMap())
    val active by graph.plans.activePlan.collectAsStateWithLifecycle(null)
    val settings by graph.settings.settings.collectAsStateWithLifecycle(Settings())
    val community by graph.plans.community.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { graph.plans.refreshCommunity() }
    val plans = remember(manifest, community) { manifest?.let { Plans.all(it.books, community) }.orEmpty() }
    val planId = active ?: progress.keys.firstOrNull()
    val plan = plans.firstOrNull { it.id == planId }
    val prog = planId?.let { progress[it] }
    var tab by rememberSaveable { mutableIntStateOf(if (plan == null) 1 else 0) }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("வாசிப்புத் திட்டங்கள்", "Reading plans"), style = Ts.type.screenTitle, color = c.ink)
            TsSegmented(
                listOf(tr("இன்று", "Today"), tr("திட்டங்கள்", "Plans"), tr("புள்ளிவிவரம்", "Stats")), tab, { tab = it },
                Modifier.widthIn(max = 560.dp).fillMaxWidth(), height = 42.dp, fill = true,
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val mod = Modifier.widthIn(max = 720.dp).fillMaxWidth()
            when (tab) {
                0 -> if (plan != null && prog != null) {
                    TodayTab(plan, prog, settings.version, mod, onRead) { key, read ->
                        scope.launch {
                            graph.plans.toggle(plan.id, key, read)
                            if (read) graph.stats.record("plan", action = "${plan.id}:$key")
                        }
                    }
                } else {
                    EmptyPlan(mod) { tab = 1 }
                }
                1 -> plans.forEachIndexed { i, p ->
                    // M7-3: the community's plans follow the built-in ones under their own heading.
                    if (p.community && plans.getOrNull(i - 1)?.community != true) {
                        Kicker(tr("சமூகத் திட்டங்கள்", "Community plans"), mod.padding(top = 8.dp))
                    }
                    PlanCard(p, progress[p.id], p.id == planId, mod,
                        onStart = { scope.launch { graph.plans.join(p.id); tab = 0 } },
                        onContinue = { scope.launch { graph.plans.setActive(p.id); tab = 0 } },
                        onLeave = { scope.launch { graph.plans.leave(p.id) } })
                }
                else -> if (plan != null && prog != null) StatsTab(plan, prog, lang, mod) else EmptyPlan(mod) { tab = 1 }
            }
        }
    }
}

@Composable
private fun EmptyPlan(mod: Modifier, onBrowse: () -> Unit) {
    TsCard(mod) {
        Text(tr("இன்னும் திட்டம் இல்லை", "No plan yet"), style = Ts.type.cardTitle, color = Ts.colors.ink)
        Text(tr("ஒரு திட்டத்தைத் தேர்ந்தெடுத்து நாள்தோறும் வாசியுங்கள்.", "Pick a plan and read a little every day."),
            style = Ts.type.body, color = Ts.colors.muted, modifier = Modifier.padding(vertical = 8.dp))
        TsPillButton(tr("திட்டங்களைப் பார்", "Browse plans"), onBrowse, style = PillStyle.Filled, height = 40.dp)
    }
}

@Composable
private fun TodayTab(plan: ReadingPlan, prog: PlanProgress, version: String, mod: Modifier, onRead: (Passage) -> Unit, onToggle: (String, Boolean) -> Unit) {
    val c = Ts.colors
    val lang = LocalUiLang.current
    val schedule = remember(plan) { Plans.schedule(plan) }
    val today = ChronoUnit.DAYS.between(prog.start, LocalDate.now()).toInt().coerceIn(0, plan.days - 1)
    var day by rememberSaveable(plan.id) { mutableIntStateOf(today) }
    val stats = Plans.stats(plan, prog)
    TsCard(mod, raised = true) {
        Text(plan.title(lang), style = Ts.type.cardTitle, color = c.ink)
        TsProgress(stats.percent / 100f, Modifier.padding(vertical = 10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("நாள் ${day + 1} / ${plan.days}", "Day ${day + 1} of ${plan.days}"), style = Ts.type.label, color = c.ink, modifier = Modifier.weight(1f))
            Text(
                if (stats.behind == 0) tr("சரியான பாதை", "On track") else tr("${stats.behind} நாள் பின்னால்", "${stats.behind} days behind"),
                style = Ts.type.labelSmall, color = if (stats.behind == 0) c.accent else c.amber,
            )
        }
    }
    TsCard(mod) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TsPillButton("‹", { day = (day - 1).coerceAtLeast(0) }, height = 36.dp)
            Text(
                prog.start.plusDays(day.toLong()).let { d -> if (lang == UiLang.Tamil) "${MONTHS_TA_SHORT[d.monthValue - 1]} ${d.dayOfMonth}" else "${d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${d.dayOfMonth}" },
                style = Ts.type.label, color = c.ink, modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            TsPillButton("›", { day = (day + 1).coerceAtMost(plan.days - 1) }, height = 36.dp)
        }
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val passages = schedule[day]
            if (passages.isEmpty()) Text(tr("ஓய்வு நாள்", "Rest day"), style = Ts.type.body, color = c.muted)
            passages.forEach { p ->
                val key = Plans.key(day, p.trackIndex)
                val done = key in prog.done
                val first = p.units.first()
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface)
                        .clickable { onRead(Passage(version, first.book.code, first.chapter, first.verses?.first)) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Check(done) { onToggle(key, !done) }
                    Column(Modifier.weight(1f)) {
                        Text(
                            Plans.passageLabel(p.units, lang),
                            style = Ts.type.cardTitle.copy(fontWeight = FontWeight.SemiBold, textDecoration = if (done) TextDecoration.LineThrough else null),
                            color = if (done) c.muted else c.ink,
                        )
                        Text(if (lang == UiLang.Tamil) p.track.nameTa else p.track.nameEn, style = Ts.type.caption, color = c.muted)
                    }
                    Text(tr("வாசி ›", "Read ›"), style = Ts.type.labelSmall, color = c.accent)
                }
            }
        }
        if (schedule[day].isNotEmpty() && !Plans.dayDone(schedule[day], day, prog.done)) {
            TsPillButton(tr("நாள் முடிந்தது", "Mark day read"), {
                schedule[day].forEach { onToggle(Plans.key(day, it.trackIndex), true) }
            }, style = PillStyle.Filled, height = 40.dp, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun Check(done: Boolean, onClick: () -> Unit) {
    val c = Ts.colors
    Box(
        Modifier.size(22.dp).clip(CircleShape)
            .background(if (done) c.accent else Color.Transparent)
            .then(if (done) Modifier else Modifier.border(2.dp, c.line2, CircleShape))
            .clickable(role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { if (done) Icon(TsIcons.Check, null, Modifier.size(14.dp), tint = c.onAccent) }
}

@Composable
private fun PlanCard(p: ReadingPlan, prog: PlanProgress?, isActive: Boolean, mod: Modifier, onStart: () -> Unit, onContinue: () -> Unit, onLeave: () -> Unit) {
    val c = Ts.colors
    val lang = LocalUiLang.current
    var confirmLeave by remember { mutableStateOf(false) }
    TsCard(mod, raised = isActive) {
        Kicker(
            (if (p.days >= 90) tr("${(p.days / 30.4).roundToInt()} மாதம்", "${(p.days / 30.4).roundToInt()} months") else tr("${p.days} நாள்", "${p.days} days")) +
                " · " + tr("நாளுக்கு ≈ ${"%.1f".format(Plans.schedule(p).sumOf { d -> d.sumOf { it.chapters } }.toDouble() / p.days)} அதி.",
                    "≈ ${"%.1f".format(Plans.schedule(p).sumOf { d -> d.sumOf { it.chapters } }.toDouble() / p.days)} ch/day"),
        )
        Text(p.title(lang), style = Ts.type.cardTitle, color = c.ink, modifier = Modifier.padding(top = 6.dp))
        Text(if (lang == UiLang.Tamil) p.blurbTa else p.blurbEn, style = Ts.type.body, color = c.muted, modifier = Modifier.padding(vertical = 6.dp))
        if (prog != null) {
            val st = Plans.stats(p, prog)
            TsProgress(st.percent / 100f, Modifier.padding(vertical = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TsPillButton(tr("தொடர்", "Continue"), onContinue, style = PillStyle.Filled, height = 40.dp)
                TsPillButton(if (confirmLeave) tr("உறுதி — விலகு", "Confirm leave") else tr("விலகு", "Leave"),
                    { if (confirmLeave) onLeave() else confirmLeave = true }, height = 40.dp)
            }
        } else {
            TsPillButton(tr("இன்று தொடங்கு", "Start today"), onStart, style = PillStyle.Filled, height = 40.dp)
        }
    }
}

@Composable
private fun StatsTab(plan: ReadingPlan, prog: PlanProgress, lang: UiLang, mod: Modifier) {
    val c = Ts.colors
    val stats = Plans.stats(plan, prog)
    val schedule = remember(plan) { Plans.schedule(plan) }
    Row(mod, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Metric(stats.streak.toString(), tr("நாள் தொடர்", "day streak"), c.amber, Modifier.weight(1f))
        Metric("${stats.percent}%", tr("முடிந்தது", "complete"), c.ink, Modifier.weight(1f))
        val est = stats.estimatedFinish ?: stats.end
        Metric(
            if (lang == UiLang.Tamil) "${MONTHS_TA_SHORT[est.monthValue - 1]} ${est.dayOfMonth}" else "${est.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${est.dayOfMonth}",
            tr("எதிர்பார்ப்பு முடிவு", "expected finish"), c.ink, Modifier.weight(1f), small = true,
        )
    }
    var month by remember { mutableStateOf(YearMonth.now()) }
    TsCard(mod) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TsPillButton("‹", { month = month.minusMonths(1) }, height = 32.dp)
            Text(
                if (lang == UiLang.Tamil) "${MONTHS_TA[month.monthValue - 1]} ${month.year}" else "${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}",
                style = Ts.type.cardTitle, color = c.ink, modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            TsPillButton("›", { month = month.plusMonths(1) }, height = 32.dp)
        }
        val week = if (lang == UiLang.Tamil) WEEK_TA else WEEK_EN
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            week.forEach { Text(it, style = Ts.type.captionSmall, color = c.muted, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value % 7
        val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        val today = LocalDate.now()
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            cells.chunked(7).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { d ->
                        Box(Modifier.weight(1f).height(40.dp)) {
                            if (d != null) {
                                val idx = ChronoUnit.DAYS.between(prog.start, d).toInt()
                                val inPlan = idx in 0 until plan.days
                                val done = inPlan && schedule[idx].isNotEmpty() && Plans.dayDone(schedule[idx], idx, prog.done)
                                val missed = inPlan && d.isBefore(today) && schedule[idx].isNotEmpty() && !done
                                val isToday = d == today
                                val shape = RoundedCornerShape(10.dp)
                                Box(
                                    Modifier.matchParentSize().clip(shape)
                                        .background(
                                            when {
                                                done -> c.accent
                                                missed -> c.amber
                                                isToday -> Color.Transparent
                                                else -> c.surface
                                            },
                                        )
                                        .then(if (isToday && !done) Modifier.border(2.dp, c.amber, shape) else Modifier),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        d.dayOfMonth.toString(),
                                        style = Ts.type.label.copy(fontWeight = if (done || isToday) FontWeight.Bold else FontWeight.Normal),
                                        color = when {
                                            done || missed -> c.onAccent
                                            isToday -> c.ink
                                            else -> c.faint
                                        },
                                    )
                                }
                            }
                        }
                    }
                    repeat(7 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(c.accent, tr("முடிந்தது", "Done"))
            Legend(null, tr("இன்று", "Today"))
            Legend(c.amber, tr("தவறியது", "Missed"))
        }
    }
    TsCard(mod) {
        Kicker(tr("நடப்புத் திட்டம்", "Current plan"))
        Text(plan.title(lang), style = Ts.type.cardTitle, color = c.ink, modifier = Modifier.padding(top = 2.dp))
        Text(
            tr("நாள் ${(stats.todayIndex + 1).coerceIn(1, plan.days)} / ${plan.days} · ${stats.chapters}/${stats.chaptersTotal} அதிகாரங்கள்",
                "Day ${(stats.todayIndex + 1).coerceIn(1, plan.days)} of ${plan.days} · ${stats.chapters}/${stats.chaptersTotal} chapters"),
            style = Ts.type.caption, color = c.muted,
        )
    }
}

@Composable
private fun Metric(value: String, label: String, color: Color, modifier: Modifier, small: Boolean = false) {
    val c = Ts.colors
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.clip(shape).background(c.surface).border(1.5.dp, c.line2, shape).padding(horizontal = 12.dp, vertical = 14.dp)) {
        Text(value, style = Ts.type.headline.copy(fontSize = if (small) 18.sp else 28.sp, lineHeight = if (small) 23.sp else 28.sp), color = color)
        Text(label, style = Ts.type.caption, color = c.muted)
    }
}

@Composable
private fun Legend(fill: Color?, label: String) {
    val c = Ts.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(fill ?: Color.Transparent)
                .then(if (fill == null) Modifier.border(2.dp, c.amber, RoundedCornerShape(3.dp)) else Modifier),
        )
        Text(label, style = Ts.type.captionSmall, color = c.muted)
    }
}

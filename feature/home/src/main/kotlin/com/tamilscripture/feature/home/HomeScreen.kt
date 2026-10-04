package com.tamilscripture.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamilscripture.core.data.settings.Settings
import com.tamilscripture.core.designsystem.component.Avatar
import com.tamilscripture.core.designsystem.component.CrossMark
import com.tamilscripture.core.designsystem.component.Kicker
import com.tamilscripture.core.designsystem.component.PillStyle
import com.tamilscripture.core.designsystem.component.TsCard
import com.tamilscripture.core.designsystem.component.TsPillButton
import com.tamilscripture.core.designsystem.component.TsProgress
import com.tamilscripture.core.designsystem.component.TsSegmented
import com.tamilscripture.core.designsystem.icon.TsIcons
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.DailyVerse
import com.tamilscripture.core.model.Passage
import com.tamilscripture.core.model.Plans
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.model.label
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class HomeNav(
    val read: (Passage) -> Unit,
    val plans: () -> Unit,
    val study: () -> Unit,
    val profile: () -> Unit,
    val downloads: () -> Unit,
)

/** முகப்பு · Home (design 1A). */
@Composable
fun HomeScreen(nav: HomeNav) {
    val services = LocalAppServices.current
    val graph = services.graph
    val c = Ts.colors
    val lang = LocalUiLang.current
    val scope = rememberCoroutineScope()
    val manifest by graph.content.manifest.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle(Settings())
    val progress by graph.plans.progress.collectAsStateWithLifecycle(emptyMap())
    val active by graph.plans.activePlan.collectAsStateWithLifecycle(null)
    val community by graph.plans.community.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CrossMark()
                Text(tr("தமிழ் வேதாகமம்", "Tamil Scripture"), style = Ts.type.cardTitle.copy(fontSize = 22.sp), color = c.ink)
            }
            TsSegmented(
                listOf("த", "EN"), if (lang == UiLang.Tamil) 0 else 1,
                { i -> scope.launch { graph.settings.update { it.copy(uiLang = if (i == 0) UiLang.Tamil else UiLang.English) } } },
                height = 32.dp, textStyle = Ts.type.labelSmall,
            )
            Box(Modifier.size(8.dp))
            Avatar(tr("அ", "A"), onClick = nav.profile)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val m = manifest
            val cardMod = Modifier.widthIn(max = 720.dp).fillMaxWidth()
            // Continue reading
            val last = settings.lastRead ?: Passage(settings.version, "JHN", 1)
            val lastBook = m?.book(last.book)
            val verses by produceState(0, last.chapterKey()) {
                value = graph.content.chapter(last.version, last.book, last.chapter).firstOrNull()?.value?.verseNumbers?.size ?: 0
            }
            TsCard(cardMod, raised = true, onClick = { nav.read(last) }) {
                Kicker(tr("தொடர்ந்து வாசிக்க", "Continue reading"))
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(lastBook?.label(lang, last.chapter) ?: "…", style = Ts.type.headline, color = c.ink)
                        val vs = m?.version(last.version)?.short ?: last.version
                        Text(
                            if (last.verse != null) "$vs · " + tr("வசனம் ${last.verse} இல் நிறுத்தினீர்கள்", "You stopped at verse ${last.verse}") else vs,
                            style = Ts.type.caption.copy(fontSize = 13.sp), color = c.muted,
                        )
                    }
                    Box(Modifier.size(48.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                        Icon(TsIcons.ChevronRight, tr("திற", "Open"), Modifier.size(22.dp), tint = c.onAccent)
                    }
                }
                if (verses > 0) TsProgress((last.verse ?: 1).toFloat() / verses, Modifier.padding(top = 10.dp))
            }
            // Today's reading
            val planId = active ?: progress.keys.firstOrNull()
            val plan = m?.let { Plans.all(it.books, community) }?.firstOrNull { it.id == planId }
            val prog = planId?.let { progress[it] }
            if (plan != null && prog != null) {
                val schedule = Plans.schedule(plan)
                val day = ChronoUnit.DAYS.between(prog.start, LocalDate.now()).toInt().coerceIn(0, plan.days - 1)
                val stats = Plans.stats(plan, prog)
                TsCard(cardMod) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Kicker(tr("இன்றைய வாசிப்பு · நாள் ${day + 1}", "Today's reading · Day ${day + 1}"), Modifier.weight(1f))
                        if (stats.streak > 0) Text(tr("${stats.streak} நாள் தொடர்", "${stats.streak}-day streak"), style = Ts.type.labelSmall, color = c.amber)
                    }
                    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        schedule[day].forEach { p ->
                            val key = Plans.key(day, p.trackIndex)
                            val done = key in prog.done
                            val first = p.units.first()
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface)
                                    .clickable { nav.read(Passage(settings.version, first.book.code, first.chapter, first.verses?.first)) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    Modifier.size(22.dp).clip(CircleShape)
                                        .background(if (done) c.accent else androidx.compose.ui.graphics.Color.Transparent)
                                        .then(if (done) Modifier else Modifier.border(2.dp, c.line2, CircleShape))
                                        .clickable(role = Role.Checkbox) {
                                            scope.launch {
                                                graph.plans.toggle(plan.id, key, !done)
                                                if (!done) graph.stats.record("plan", action = "${plan.id}:$key")
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) { if (done) Icon(TsIcons.Check, null, Modifier.size(14.dp), tint = c.onAccent) }
                                Text(
                                    Plans.passageLabel(p.units, lang), modifier = Modifier.weight(1f),
                                    style = Ts.type.cardTitle.copy(fontWeight = FontWeight.SemiBold, textDecoration = if (done) TextDecoration.LineThrough else null),
                                    color = if (done) c.muted else c.ink,
                                )
                                if (!done) Text("≈ ${p.chapters * 4} ${tr("நிமி", "min")}", style = Ts.type.caption, color = c.muted)
                            }
                        }
                    }
                    Text("${plan.title(lang)} — ${stats.percent}% ${tr("முடிந்தது", "done")}", style = Ts.type.caption, color = c.muted)
                }
            } else {
                TsCard(cardMod, onClick = nav.plans) {
                    Kicker(tr("வாசிப்புத் திட்டம்", "Reading plan"))
                    Text(tr("ஒரு வருடத்தில் வேதாகமம் — இன்றே தொடங்குங்கள்", "Whole Bible in a year — start today"),
                        style = Ts.type.cardTitle, color = c.ink, modifier = Modifier.padding(top = 8.dp))
                }
            }
            // DL-1: offer the starter set (IRV, BSB, cross-references) until something is installed.
            val installed by graph.packs.store.installed.collectAsStateWithLifecycle()
            val catalogue by graph.packs.catalogue.collectAsStateWithLifecycle()
            val starter = catalogue?.packs?.filter { it.starter }.orEmpty()
            if (installed.isEmpty() && starter.isNotEmpty()) {
                TsCard(cardMod, raised = true) {
                    Kicker(tr("இணைப்பின்றி வாசிக்க", "Read offline"), color = c.accent)
                    Text(
                        tr("IRV, BSB, தொடர்புள்ள வசனங்கள்", "IRV, BSB and cross-references") + " · " +
                            "%.0f MB".format(starter.sumOf { it.size } / 1_048_576.0),
                        style = Ts.type.cardTitle, color = c.ink, modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TsPillButton(tr("பதிவிறக்கு", "Download"), {
                            starter.forEach { graph.packs.download(it.id, wifiOnly = false) }
                            nav.downloads()
                        }, style = PillStyle.Filled, height = 40.dp)
                        TsPillButton(tr("மற்றவை", "More"), nav.downloads, height = 40.dp)
                    }
                }
            }
            // Verse of the day
            val vid = DailyVerse.forDate().toString()
            val parts = vid.split('.')
            val vBook = m?.book(parts[0])
            val vText by produceState<String?>(null, vid, settings.version) {
                value = graph.content.chapter(settings.version, parts[0], parts[1].toInt()).firstOrNull()?.value?.verseText(parts[2].toInt())
            }
            TsCard(cardMod) {
                Kicker(tr("இன்றைய வசனம்", "Verse of the day") + " · " + (vBook?.label(lang, parts[1].toInt(), parts[2].toInt()) ?: ""), color = c.amber)
                Text(vText ?: "…", style = Ts.type.scripture(19.sp, 1.7f), color = c.ink, modifier = Modifier.padding(vertical = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TsPillButton("▶ " + tr("கேள்", "Listen"), {
                        vBook?.let { services.audio.play(settings.version, it, parts[1].toInt(), parts[2].toInt()) }
                    }, height = 36.dp, textStyle = Ts.type.labelSmall)
                    TsPillButton(tr("வாசி", "Read"), { nav.read(Passage(settings.version, parts[0], parts[1].toInt(), parts[2].toInt())) },
                        height = 36.dp, textStyle = Ts.type.labelSmall, style = PillStyle.Outlined)
                }
            }
            Row(cardMod, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShortcutCard(tr("விளக்கவுரை", "Commentary"), "M. Henry · Calvin · Geneva", Modifier.weight(1f), nav.study)
                ShortcutCard(tr("ஆய்வு", "Study"), tr("வரைபடம் · அகராதி · சங்கீதம்", "Atlas · dictionary · psalms"), Modifier.weight(1f), nav.study)
            }
        }
    }
}

@Composable
private fun ShortcutCard(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Ts.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).background(c.surface).border(1.5.dp, c.line, shape).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = Ts.type.cardTitle, color = c.ink)
        Text(subtitle, style = Ts.type.caption, color = c.muted, maxLines = 1)
    }
}

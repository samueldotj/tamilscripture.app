package com.tamilscripture.feature.study

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamilscripture.core.designsystem.theme.NotoSans
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.PsalmCardData
import com.tamilscripture.core.model.PsalmTune
import com.tamilscripture.core.services.tr

/**
 * பாடுங்கள் · Sing this psalm (website docs/feature_psalms_explorer.md §9): the psalm in the
 * 1650 Scottish Psalter. Chips pick the version (C.M. or L.M., or a part of Psalm 119) and
 * its tune; [tuneBlock] plays it for every stanza and reports where it is, and the line being
 * sung is lit; the first stanza opens out into all of them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PsalmSing(card: PsalmCardData, tuneBlock: @Composable (tune: PsalmTune, stanzas: Int, lyrics: List<List<List<String>>>, onPlace: (TunePlace?) -> Unit) -> Unit) {
    val c = Ts.colors
    var vi by rememberSaveable(card.n) { mutableIntStateOf(0) }
    var ti by rememberSaveable(card.n, vi) { mutableIntStateOf(0) }
    var all by rememberSaveable(card.n, vi) { mutableStateOf(false) }
    var place by remember(card.n, vi) { mutableStateOf<TunePlace?>(null) }
    val v = card.metrical[vi]
    val tunes = v.tunes.mapNotNull(card::tune)
    val tune = tunes.getOrNull(ti.coerceAtMost(tunes.lastIndex))
    val parts = card.metrical.any { it.part != null }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("பாடுங்கள்", "Sing this psalm"), style = Ts.type.sectionHeading.copy(fontSize = 16.sp), color = c.amber)
            Text("Scottish Psalter, 1650", style = Ts.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.muted)
        }
        if (card.metrical.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                if (parts) Text(tr("பகுதி", "Part"), style = Ts.type.caption, color = c.muted, modifier = Modifier.padding(end = 2.dp))
                card.metrical.forEachIndexed { i, x ->
                    Pill(x.part?.toString() ?: x.label, i == vi) { vi = i }
                }
            }
        }
        if (tune != null) {
            if (tunes.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tunes.forEachIndexed { i, t -> Pill(t.name, t == tune) { ti = i } }
                }
            }
            key(v.key, tune.id) {
                tuneBlock(tune, v.stanzas.size, v.lyrics) { p ->
                    place = p
                    if (p != null && p.stanza > 0) all = true
                }
            }
        }
        Text(
            when {
                tune == null -> tr("இந்த அளவுக்கு (${v.label}) பண் இன்னும் சேர்க்கப்படவில்லை.", "No tune in this metre (${v.label}) has been added yet.")
                v.fallback -> tr("இந்தச் சங்கீதத்திற்கு மரபுப் பண் இன்னும் இல்லை; இவை ${v.label} அளவுக்குப் பொருந்தும் பண்கள்.", "No traditional tune is set for this version yet; these fit its metre (${v.label}).")
                else -> tr("ஸ்காட்லாந்து சபைகளில் இந்தச் சங்கீதத்துக்குப் பாடப்படும் பண்.", "Tunes this psalm is traditionally sung to in Scotland.")
            },
            style = Ts.type.captionSmall, color = c.muted,
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            v.argument?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = Ts.type.caption.copy(fontFamily = NotoSans, fontStyle = FontStyle.Italic), color = c.muted)
            }
            val english = Ts.type.body.copy(fontFamily = NotoSans, fontSize = 15.sp, lineHeight = 24.sp)
            (if (all) v.stanzas else v.stanzas.take(1)).forEachIndexed { si, stanza ->
                Column {
                    stanza.forEachIndexed { li, line ->
                        val sung = place?.stanza == si && place?.line == li
                        Text(
                            line.trim(), style = english, color = c.ink,
                            modifier = Modifier.padding(start = if (line.startsWith("  ")) 24.dp else 0.dp)
                                .background(if (sung) c.verseSelected else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(4.dp)),
                        )
                    }
                }
            }
            if (v.stanzas.size > 1) {
                Text(
                    if (all) tr("முதல் பத்தி மட்டும்", "First stanza only") else tr("எல்லா ${v.stanzas.size} பத்திகளும்", "All ${v.stanzas.size} stanzas"),
                    style = Ts.type.label, color = c.accent, modifier = Modifier.clickable { all = !all }.padding(vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    val c = Ts.colors
    val shape = RoundedCornerShape(50)
    Text(
        text, style = Ts.type.labelSmall, color = if (on) c.onAccent else c.ink,
        modifier = Modifier.clip(shape).background(if (on) c.accent else c.surface).border(1.5.dp, if (on) c.accent else c.line2, shape)
            .clickable(role = Role.Tab, onClick = onClick).semantics { selected = on }
            .defaultMinSize(minWidth = 36.dp).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

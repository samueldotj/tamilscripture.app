package com.tamilscripture.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamilscripture.core.designsystem.component.HDivider
import com.tamilscripture.core.designsystem.component.IconBox
import com.tamilscripture.core.designsystem.component.TsPillButton
import com.tamilscripture.core.designsystem.component.TsSegmented
import com.tamilscripture.core.designsystem.icon.TsIcons
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.Passage
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr

/** புத்தகம் · Book & chapter picker (design 1I). */
@Composable
fun BookPickerScreen(current: Passage, onPick: (Passage) -> Unit, onClose: () -> Unit) {
    val services = LocalAppServices.current
    val manifest by services.graph.content.manifest.collectAsStateWithLifecycle()
    val lang = LocalUiLang.current
    val c = Ts.colors
    val m = manifest ?: return
    val currentBook = m.book(current.book)
    var nt by rememberSaveable { mutableStateOf(currentBook?.isNewTestament ?: true) }
    var expanded by rememberSaveable { mutableStateOf(current.book) }
    var version by rememberSaveable { mutableStateOf(current.version) }
    var showVersions by rememberSaveable { mutableStateOf(false) }
    val books = m.books.filter { it.isNewTestament == nt }
    val listState = rememberLazyListState()
    // M8-7: the book heatmap, when community heat is on (chapters many readers highlighted).
    val heatOn by remember { services.graph.settings.settings.map { it.heat } }.collectAsStateWithLifecycle(false)
    val heat by produceState<Map<String, Map<Int, Int>>>(emptyMap(), heatOn) {
        value = if (heatOn) services.graph.heat.all().orEmpty() else emptyMap()
    }
    LaunchedEffect(nt) {
        val i = books.indexOfFirst { it.code == expanded }
        if (i > 1) listState.scrollToItem(i - 1)
    }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconBox(TsIcons.Close, tr("மூடு", "Close"), onClose)
            TsSegmented(
                listOf(tr("பழைய", "Old") + " OT", tr("புதிய", "New") + " NT"), if (nt) 1 else 0, { nt = it == 1 },
                Modifier.weight(1f), height = 44.dp, fill = true,
            )
            TsPillButton("${m.version(version)?.short ?: version} ▾", { showVersions = true }, textStyle = Ts.type.labelSmall)
        }
        HDivider(thickness = 1.5.dp)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
            items(books, key = { it.code }) { b ->
                val open = b.code == expanded
                Column(Modifier.fillMaxWidth().background(if (open) c.surface2 else androidx.compose.ui.graphics.Color.Transparent)) {
                    HDivider()
                    Row(
                        Modifier.fillMaxWidth().clickable { expanded = if (open) "" else b.code }.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            b.name(lang), modifier = Modifier.weight(1f),
                            style = Ts.type.cardTitle.copy(fontSize = if (open) 19.sp else 17.sp, fontWeight = if (open) FontWeight.Bold else FontWeight.SemiBold),
                            color = if (open) c.ink else c.ink2,
                        )
                        Text(
                            "${b.chapters} ${tr("அதி", "ch")}" + if (open) " · ⌃" else "",
                            style = Ts.type.caption.copy(fontWeight = if (open) FontWeight.Bold else FontWeight.Normal),
                            color = if (open) c.accent else c.muted,
                        )
                    }
                    if (open) {
                        val counts = heat[b.code].orEmpty()
                        val buckets = counts.mapValues { (_, n) -> com.tamilscripture.core.data.content.HeatRepository.bucket(n, counts.values.toList()) }
                        ChapterGrid(b.chapters, if (b.code == current.book) current.chapter else null, buckets) { ch ->
                            onPick(Passage(version, b.code, ch))
                        }
                    }
                }
            }
        }
        Box(Modifier.navigationBarsPadding())
    }
    if (showVersions) {
        VersionSheet(m.versions, version, { version = it; showVersions = false }) { showVersions = false }
    }
}

@Composable
private fun ChapterGrid(count: Int, current: Int?, heat: Map<Int, Int> = emptyMap(), onPick: (Int) -> Unit) {
    val c = Ts.colors
    val cols = 6
    Column(Modifier.widthIn(max = 560.dp).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..count).chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { ch ->
                    val on = ch == current
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        Modifier.weight(1f).height(48.dp).clip(shape)
                            .background(
                                when {
                                    on -> c.accent
                                    (heat[ch] ?: 0) > 0 -> c.accent.copy(alpha = floatArrayOf(0f, 0.10f, 0.18f, 0.28f, 0.40f)[heat.getValue(ch)])
                                    else -> c.surface
                                },
                            )
                            .then(if (on) Modifier else Modifier.border(1.dp, c.line, shape))
                            .clickable(role = Role.Button) { onPick(ch) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            ch.toString(),
                            style = Ts.type.label.copy(fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold),
                            color = if (on) c.onAccent else c.ink2,
                        )
                    }
                }
                repeat(cols - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

package com.tamilscripture.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamilscripture.core.data.settings.Appearance
import com.tamilscripture.core.data.settings.Settings
import com.tamilscripture.core.designsystem.component.HDivider
import com.tamilscripture.core.designsystem.component.IconBox
import com.tamilscripture.core.designsystem.component.Kicker
import com.tamilscripture.core.designsystem.component.PillStyle
import com.tamilscripture.core.designsystem.component.TsListRow
import com.tamilscripture.core.designsystem.component.TsPillButton
import com.tamilscripture.core.designsystem.component.TsSegmented
import com.tamilscripture.core.designsystem.component.TsToggle
import com.tamilscripture.core.designsystem.icon.TsIcons
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr
import kotlinx.coroutines.launch

/** Profile / settings / about: language, appearance, stats, cache, licences (A-1.4, ST-8, DL-7). */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val services = LocalAppServices.current
    val graph = services.graph
    val c = Ts.colors
    val lang = LocalUiLang.current
    val scope = rememberCoroutineScope()
    val settings by graph.settings.settings.collectAsStateWithLifecycle(Settings())
    val manifest by graph.content.manifest.collectAsStateWithLifecycle()
    val cacheBytes by produceState(0L) { value = graph.onlineCache.sizeBytes() }
    val pending by produceState(0) { value = graph.stats.pendingCount() }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBox(TsIcons.ChevronLeft, tr("பின்", "Back"), onBack)
            Text(tr("அமைப்புகள்", "Settings"), style = Ts.type.barTitle, color = c.ink)
        }
        HDivider(thickness = 1.5.dp)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                TsListRow(tr("மொழி", "Language"), trailing = {
                    TsSegmented(listOf("தமிழ்", "English"), if (lang == UiLang.Tamil) 0 else 1,
                        { i -> scope.launch { graph.settings.update { it.copy(uiLang = if (i == 0) UiLang.Tamil else UiLang.English) } } },
                        height = 36.dp, textStyle = Ts.type.labelSmall)
                })
                HDivider()
                TsListRow(tr("தோற்றம்", "Appearance"), subtitle = tr("தானியங்கி: தொலைபேசியின் அமைப்பைப் பின்பற்றும்", "Auto follows the phone's setting"), trailing = {
                    TsSegmented(
                        listOf(tr("இருள்", "Dark"), tr("ஒளி", "Light"), tr("தானியங்கி", "Auto")),
                        when (settings.appearance) { Appearance.Dark -> 0; Appearance.Light -> 1; Appearance.System -> 2 },
                        { i -> scope.launch { graph.settings.update { it.copy(appearance = listOf(Appearance.Dark, Appearance.Light, Appearance.System)[i]) } } },
                        height = 36.dp, textStyle = Ts.type.labelSmall,
                    )
                })
                HDivider()
                TsListRow(
                    tr("அநாமதேயப் பயன்பாட்டுப் புள்ளிவிவரம்", "Share anonymous usage stats"),
                    subtitle = tr("வாசித்த வசனங்கள், கேட்ட நேரம் — பெயர் இல்லாமல். அனுப்பக் காத்திருப்பவை: $pending", "Verses read and time listened, never your name. Waiting to send: $pending"),
                    trailing = { TsToggle(settings.shareStats, { v -> scope.launch { graph.settings.update { it.copy(shareStats = v) } } }) },
                )
                HDivider()
                TsListRow(
                    tr("இணைய வாசிப்புச் சேமிப்பு", "Online reading cache"),
                    subtitle = "%.1f MB".format(cacheBytes / 1_048_576.0),
                    trailing = { TsPillButton(tr("அழி", "Clear"), { scope.launch { graph.onlineCache.clear() } }, height = 36.dp, style = PillStyle.Outlined) },
                )
                HDivider()
                Kicker(tr("உரிமங்கள்", "Licences"), Modifier.padding(start = 22.dp, top = 18.dp, bottom = 6.dp))
                manifest?.versions?.sortedBy { it.order }?.forEach { v ->
                    Text("${v.short} — ${v.attribution.ifBlank { v.licence }}", style = Ts.type.caption, color = c.muted,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
                    v.audio?.let { a ->
                        Text("${v.short} audio — ${a.attribution.ifBlank { a.licence }}", style = Ts.type.caption, color = c.muted,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
                    }
                }
                Text(
                    tr("விளக்கவுரைகள்: மத்தியூ ஹென்றி, கால்வின், ஜெனீவா, பூல், டிராப் — பொது உரிமை; தமிழ் வரைவுகள் சமூகத் திருத்தத்துக்குரியவை.",
                        "Commentaries: Matthew Henry, Calvin, Geneva, Poole, Trapp — public domain; Tamil drafts are open to community correction."),
                    style = Ts.type.caption, color = c.muted, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
                )
                Text(
                    tr("எழுத்துருக்கள்: Mukta Malar, Noto Sans Tamil, Noto Serif Tamil, Noto Sans — SIL Open Font License 1.1.",
                        "Fonts: Mukta Malar, Noto Sans Tamil, Noto Serif Tamil, Noto Sans — SIL Open Font License 1.1."),
                    style = Ts.type.caption, color = c.muted, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
                )
                Text(
                    "tamilscripture.com · ${BuildConfig.VERSION_NAME}", style = Ts.type.captionSmall, color = c.faint,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                )
            }
        }
    }
}

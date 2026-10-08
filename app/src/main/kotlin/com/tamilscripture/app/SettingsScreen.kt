package com.tamilscripture.app

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamilscripture.core.data.plans.ReminderWorker
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** Profile / settings / about: language, appearance, stats, cache, licences (A-1.4, ST-8, DL-7). */
@Composable
fun SettingsScreen(onBack: () -> Unit, onDownloads: () -> Unit, onAccount: () -> Unit, onMine: () -> Unit) {
    val services = LocalAppServices.current
    val graph = services.graph
    val c = Ts.colors
    val lang = LocalUiLang.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by graph.settings.settings.collectAsStateWithLifecycle(Settings())
    val manifest by graph.content.manifest.collectAsStateWithLifecycle()
    val cacheBytes by produceState(0L) { value = graph.onlineCache.sizeBytes() }
    val pending by produceState(0) { value = graph.stats.pendingCount() }
    val session by graph.account.session.collectAsStateWithLifecycle()

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
                TsListRow(
                    tr("கணக்கு", "Account"),
                    subtitle = session?.email ?: tr("உள்நுழைந்து இணையதளத்துடன் ஒத்திசையுங்கள்", "Sign in to sync with the website"),
                    onClick = onAccount,
                    trailing = { androidx.compose.material3.Icon(TsIcons.ChevronRight, null, tint = c.muted) },
                )
                HDivider()
                TsListRow(
                    tr("என் முனைப்புகள், குறிப்புகள், குறிகள்", "My highlights, notes, bookmarks"),
                    subtitle = tr("வாசிப்பு வரலாறும்", "And reading history"),
                    onClick = onMine,
                    trailing = { androidx.compose.material3.Icon(TsIcons.ChevronRight, null, tint = c.muted) },
                )
                HDivider()
                TsListRow(tr("மொழி", "Language"), trailing = {
                    TsSegmented(listOf("தமிழ்", "English"), if (lang == UiLang.Tamil) 0 else 1,
                        { i -> scope.launch { graph.settings.update { it.copy(uiLang = if (i == 0) UiLang.Tamil else UiLang.English) } } },
                        height = 36.dp, textStyle = Ts.type.labelSmall)
                })
                HDivider()
                // The three-way control sits under its title: beside it, Tamil titles had no room.
                TsListRow(tr("தோற்றம்", "Appearance"), subtitle = tr("தானியங்கி: தொலைபேசியின் அமைப்பைப் பின்பற்றும்", "Auto follows the phone's setting"))
                TsSegmented(
                    listOf(tr("இருள்", "Dark"), tr("ஒளி", "Light"), tr("தானியங்கி", "Auto")),
                    when (settings.appearance) { Appearance.Dark -> 0; Appearance.Light -> 1; Appearance.System -> 2 },
                    { i -> scope.launch { graph.settings.update { it.copy(appearance = listOf(Appearance.Dark, Appearance.Light, Appearance.System)[i]) } } },
                    height = 40.dp, fill = true, textStyle = Ts.type.labelSmall,
                    modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 16.dp).fillMaxWidth(),
                )
                HDivider()
                ReminderRow(settings.reminderMinutes) { minutes ->
                    scope.launch { graph.settings.update { it.copy(reminderMinutes = minutes) } }
                    ReminderWorker.schedule(context, minutes)
                }
                HDivider()
                TsListRow(
                    tr("அநாமதேயப் பயன்பாட்டுப் புள்ளிவிவரம்", "Share anonymous usage stats"),
                    subtitle = tr("வாசித்த வசனங்கள், கேட்ட நேரம் — பெயர் இல்லாமல். அனுப்பக் காத்திருப்பவை: $pending", "Verses read and time listened, never your name. Waiting to send: $pending"),
                    trailing = { TsToggle(settings.shareStats, { v -> scope.launch { graph.settings.update { it.copy(shareStats = v) } } }) },
                )
                HDivider()
                TsListRow(
                    tr("பதிவிறக்கங்கள்", "Downloads"),
                    subtitle = tr("இணைப்பின்றி வாசிக்க வேதாகமங்களும் ஆய்வுப் பொதிகளும்", "Bibles and study packs for reading offline"),
                    onClick = onDownloads,
                    trailing = { androidx.compose.material3.Icon(TsIcons.ChevronRight, null, tint = c.muted) },
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
                    tr("எழுத்துருக்கள்: Mukta Malar, Noto Sans Tamil, Noto Serif Tamil, Noto Sans, Lora, Cormorant Garamond — SIL Open Font License 1.1.",
                        "Fonts: Mukta Malar, Noto Sans Tamil, Noto Serif Tamil, Noto Sans, Lora, Cormorant Garamond — SIL Open Font License 1.1."),
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

/** M7-5: a daily reminder at a chosen time with today's reading. */
@Composable
private fun ReminderRow(minutes: Int?, onChange: (Int?) -> Unit) {
    val context = LocalContext.current
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun pick(start: Int) {
        TimePickerDialog(context, { _, h, m -> onChange(h * 60 + m) }, start / 60, start % 60, false).show()
    }
    TsListRow(
        tr("தினசரி நினைவூட்டல்", "Daily reminder"),
        subtitle = tr("இன்றைய வாசிப்புடன் ஒரு அறிவிப்பு", "A notification with today's reading"),
        trailing = {
            if (minutes != null) {
                val time = LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofPattern("h:mm a"))
                TsPillButton(time, { pick(minutes) }, height = 36.dp, textStyle = Ts.type.labelSmall)
            }
            TsToggle(minutes != null, { on ->
                if (on && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
                onChange(if (on) 7 * 60 else null)
            })
        },
    )
}

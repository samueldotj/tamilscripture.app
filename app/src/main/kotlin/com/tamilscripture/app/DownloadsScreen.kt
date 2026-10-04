package com.tamilscripture.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamilscripture.core.data.packs.CatalogueEntry
import com.tamilscripture.core.data.packs.PackState
import com.tamilscripture.core.designsystem.component.HDivider
import com.tamilscripture.core.designsystem.component.IconBox
import com.tamilscripture.core.designsystem.component.Kicker
import com.tamilscripture.core.designsystem.component.PillStyle
import com.tamilscripture.core.designsystem.component.TsListRow
import com.tamilscripture.core.designsystem.component.TsPillButton
import com.tamilscripture.core.designsystem.component.TsProgress
import com.tamilscripture.core.designsystem.component.TsToggle
import com.tamilscripture.core.designsystem.icon.TsIcons
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.data.settings.Settings
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr
import kotlinx.coroutines.launch

private fun mb(bytes: Long) = "%.1f MB".format(bytes / 1_048_576.0)

/** பதிவிறக்கங்கள் · Downloads (DL-2, DL-4, DL-6, DL-7). */
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val graph = LocalAppServices.current.graph
    val packs = graph.packs
    val c = Ts.colors
    val lang = LocalUiLang.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val catalogue by packs.catalogue.collectAsStateWithLifecycle()
    val states by packs.states.collectAsStateWithLifecycle(emptyMap())
    val installed by packs.store.installed.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle(Settings())
    val wifiOnly = settings.downloadWifiOnly
    var refreshing by remember { mutableStateOf(catalogue == null) }
    LaunchedEffect(Unit) {
        packs.refreshCatalogue()
        refreshing = false
    }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun download(id: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
        packs.download(id, wifiOnly)
    }

    val biblesTitle = tr("வேதாகமங்கள்", "Bibles")
    val studyTitle = tr("ஆய்வு", "Study")
    val commentaryTitle = tr("விளக்கவுரைகள்", "Commentaries")
    val loadingText = tr("பட்டியலைப் பெறுகிறது…", "Fetching the list…")
    val failedText = tr("பதிவிறக்கப் பட்டியலைப் பெற முடியவில்லை. இணைப்பைச் சரிபார்த்து மீண்டும் முயலுங்கள்.",
        "Couldn't fetch the download list. Check the connection and try again.")
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBox(TsIcons.ChevronLeft, tr("பின்", "Back"), onBack)
            Column(Modifier.weight(1f)) {
                Text(tr("பதிவிறக்கங்கள்", "Downloads"), style = Ts.type.barTitle, color = c.ink)
                Text(
                    tr("சாதனத்தில்: ", "On this device: ") + mb(installed.values.sumOf { it.size }),
                    style = Ts.type.caption, color = c.muted,
                )
            }
        }
        HDivider(thickness = 1.5.dp)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                TsListRow(
                    tr("Wi-Fi இல் மட்டும்", "Wi-Fi only"),
                    subtitle = tr("பெரிய பதிவிறக்கங்கள் கைப்பேசித் தரவைப் பயன்படுத்தாது", "Large downloads won't use mobile data"),
                    trailing = { TsToggle(wifiOnly, { v -> scope.launch { graph.settings.update { it.copy(downloadWifiOnly = v) } } }) },
                    modifier = Modifier.widthIn(max = 720.dp),
                )
                HDivider()
            }
            val cat = catalogue
            if (cat == null) {
                item {
                    Text(
                        if (refreshing) loadingText else failedText,
                        style = Ts.type.body, color = c.muted, modifier = Modifier.padding(22.dp),
                    )
                }
            } else {
                val groups = listOf(
                    biblesTitle to cat.packs.filter { it.type == "bible" },
                    commentaryTitle to cat.packs.filter { it.type == "commentary" },
                    studyTitle to cat.packs.filter { it.type != "bible" && it.type != "commentary" },
                )
                groups.forEach { (title, list) ->
                    if (list.isNotEmpty()) {
                        item { Kicker(title, Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 22.dp, top = 18.dp, bottom = 6.dp)) }
                        items(list, key = { it.id }) { e ->
                            PackRow(
                                e, states[e.id] ?: PackState.NotInstalled, lang,
                                onDownload = { download(e.id) },
                                onCancel = { packs.cancel(e.id) },
                                onDelete = { scope.launch { packs.delete(e.id) } },
                                modifier = Modifier.widthIn(max = 720.dp),
                            )
                            HDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackRow(
    e: CatalogueEntry,
    state: PackState,
    lang: UiLang,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
) {
    val c = Ts.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(if (lang == UiLang.Tamil) e.title.ta.ifBlank { e.title.en } else e.title.en, style = Ts.type.rowTitle, color = c.ink)
                val status = when (state) {
                    is PackState.Installed -> tr("சாதனத்தில்", "On device") + if (state.updateAvailable) tr(" · புதுப்பிப்பு உள்ளது", " · update available") else ""
                    is PackState.Downloading -> tr("பதிவிறக்குகிறது", "Downloading") + " · ${(state.fraction * 100).toInt()}%"
                    PackState.Installing -> tr("நிறுவுகிறது…", "Installing…")
                    is PackState.Queued -> tr("காத்திருக்கிறது (இணைப்பு)", "Waiting for a connection")
                    is PackState.Failed -> tr("தோல்வி: ", "Failed: ") + state.reason
                    PackState.NotInstalled -> mb(e.size) + " · " + e.licence
                }
                Text(status, style = Ts.type.caption, color = if (state is PackState.Failed) c.amber else c.muted)
            }
            when (state) {
                is PackState.Installed -> if (state.updateAvailable) {
                    TsPillButton(tr("புதுப்பி", "Update"), onDownload, style = PillStyle.Filled, height = 36.dp)
                } else {
                    TsPillButton(tr("நீக்கு", "Delete"), onDelete, height = 36.dp)
                }
                is PackState.Downloading, is PackState.Queued, PackState.Installing ->
                    TsPillButton(tr("நிறுத்து", "Cancel"), onCancel, height = 36.dp)
                else -> TsPillButton(tr("பதிவிறக்கு", "Download"), onDownload, style = PillStyle.Filled, height = 36.dp)
            }
        }
        if (state is PackState.Downloading) TsProgress(state.fraction)
    }
}

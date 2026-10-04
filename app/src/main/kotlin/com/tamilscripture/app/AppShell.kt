package com.tamilscripture.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.tamilscripture.core.data.settings.Settings
import com.tamilscripture.core.designsystem.component.HDivider
import com.tamilscripture.core.designsystem.component.Pill
import com.tamilscripture.core.designsystem.component.CrossMark
import com.tamilscripture.core.designsystem.component.VDivider
import com.tamilscripture.core.designsystem.icon.TsIcons
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.Passage
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr
import com.tamilscripture.feature.home.HomeNav
import com.tamilscripture.feature.home.HomeScreen
import com.tamilscripture.feature.plans.PlansScreen
import com.tamilscripture.feature.reader.BookPickerScreen
import com.tamilscripture.feature.reader.CommentaryScreen
import com.tamilscripture.feature.reader.ReaderNav
import com.tamilscripture.feature.reader.ReaderScreen
import com.tamilscripture.feature.search.SearchScreen
import com.tamilscripture.feature.study.StudyHubScreen
import com.tamilscripture.feature.study.StudyNav

/**
 * The app frame. Width decides the navigation (design §4.1): a bottom bar under 600 dp, a
 * rail from 600 dp; the reader opens over the tabs and takes the 14″ layout from 840 dp.
 */
@Composable
fun AppShell(start: List<NavKey>, pending: NavKey?, onPendingHandled: () -> Unit) {
    val services = LocalAppServices.current
    val backStack = rememberNavBackStack(*start.toTypedArray())
    val settings by services.graph.settings.settings.collectAsStateWithLifecycle(Settings())
    val context = LocalContext.current
    val lang = LocalUiLang.current

    LaunchedEffect(pending) {
        if (pending != null) {
            if (pending is SearchRoute) {
                backStack.clear()
            }
            backStack.add(pending)
            onPendingHandled()
        }
    }

    fun read(p: Passage) {
        val top = backStack.lastOrNull()
        if (top is ReaderRoute || top is PickerRoute || top is CommentaryRoute) {
            while (backStack.size > 1 && backStack.last().tab() == null) backStack.removeAt(backStack.lastIndex)
        }
        backStack.add(ReaderRoute(p))
    }

    fun selectTab(t: Tab) {
        val key: NavKey = when (t) {
            Tab.Home -> HomeRoute
            Tab.Plans -> PlansRoute
            Tab.Study -> StudyRoute
            Tab.Search -> SearchRoute()
        }
        backStack.clear()
        backStack.add(key)
    }

    val soon: (String) -> Unit = { name ->
        Toast.makeText(context, if (lang == UiLang.Tamil) "$name — விரைவில் வருகிறது" else "$name — coming soon", Toast.LENGTH_SHORT).show()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Ts.colors.bg)) {
        val wide = maxWidth >= 840.dp
        val rail = maxWidth >= 600.dp
        val top = backStack.lastOrNull()
        val currentTab = top?.tab()
        val content: @Composable () -> Unit = {
            NavDisplay(
                backStack = backStack,
                onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
                entryProvider = entryProvider {
                    entry<HomeRoute> {
                        HomeScreen(HomeNav(read = ::read, plans = { selectTab(Tab.Plans) }, study = { selectTab(Tab.Study) }, profile = { backStack.add(SettingsRoute) }, downloads = { backStack.add(DownloadsRoute) }))
                    }
                    entry<PlansRoute> { PlansScreen(onRead = ::read) }
                    entry<StudyRoute> {
                        StudyHubScreen(StudyNav(commentary = {
                            val last = settings.lastRead ?: Passage(settings.version, "JHN", 3)
                            backStack.add(CommentaryRoute(last))
                        }, soon = soon))
                    }
                    entry<SearchRoute> { r -> SearchScreen(onOpen = ::read, autoFocus = r.focus, initialQuery = r.query) }
                    entry<ReaderRoute> { r ->
                        ReaderScreen(
                            r.passage, wide,
                            ReaderNav(
                                back = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else selectTab(Tab.Home) },
                                picker = { p -> backStack.add(PickerRoute(p)) },
                                commentary = { p -> backStack.add(CommentaryRoute(p)) },
                                search = { backStack.add(SearchRoute(focus = true)) },
                                home = { selectTab(Tab.Home) },
                            ),
                        )
                    }
                    entry<PickerRoute> { r ->
                        BookPickerScreen(r.passage, onPick = { p ->
                            backStack.removeAt(backStack.lastIndex)
                            if (backStack.lastOrNull() is ReaderRoute) backStack.removeAt(backStack.lastIndex)
                            backStack.add(ReaderRoute(p))
                        }, onClose = { backStack.removeAt(backStack.lastIndex) })
                    }
                    entry<CommentaryRoute> { r ->
                        CommentaryScreen(r.passage, onBack = { backStack.removeAt(backStack.lastIndex) }, onReadWithVerses = { p ->
                            backStack.removeAt(backStack.lastIndex)
                            if (backStack.lastOrNull() !is ReaderRoute) backStack.add(ReaderRoute(p))
                        })
                    }
                    entry<SettingsRoute> {
                        SettingsScreen(onBack = { backStack.removeAt(backStack.lastIndex) }, onDownloads = { backStack.add(DownloadsRoute) })
                    }
                    entry<DownloadsRoute> { DownloadsScreen(onBack = { backStack.removeAt(backStack.lastIndex) }) }
                },
            )
        }

        if (rail) {
            Row(Modifier.fillMaxSize()) {
                NavRail(currentTab, ::selectTab, onReader = { read(settings.lastRead ?: Passage(settings.version, "JHN", 1)) })
                VDivider()
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                if (currentTab != null) BottomBar(currentTab, ::selectTab)
            }
        }
    }
}

private data class TabSpec(val tab: Tab, val icon: ImageVector, val ta: String, val en: String)

private val TABS = listOf(
    TabSpec(Tab.Home, TsIcons.Book, "வேதம்", "Bible"),
    TabSpec(Tab.Plans, TsIcons.Calendar, "திட்டங்கள்", "Plans"),
    TabSpec(Tab.Study, TsIcons.Compass, "ஆய்வு", "Study"),
    TabSpec(Tab.Search, TsIcons.Search, "தேடல்", "Search"),
)

/** Material 3 bottom navigation in the design's style: 60×30 pill indicator, 12 sp labels (1A). */
@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val c = Ts.colors
    Column(Modifier.background(c.surface2)) {
        HDivider(thickness = 1.5.dp)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 4.dp)) {
            TABS.forEach { t -> NavItem(t, t.tab == current, Modifier.weight(1f)) { onSelect(t.tab) } }
        }
    }
}

@Composable
private fun NavItem(t: TabSpec, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ts.colors
    Column(
        modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable(role = Role.Tab, onClick = onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(60.dp, 30.dp).clip(Pill).background(if (selected) c.navIndicator else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(t.icon, null, Modifier.size(22.dp), tint = if (selected) c.accent else c.muted)
        }
        Text(
            tr(t.ta, t.en),
            style = Ts.type.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
            color = if (selected) c.ink else c.muted,
        )
    }
}

/** Navigation rail for medium and wider windows. The mark returns to the last chapter read. */
@Composable
private fun NavRail(current: Tab?, onSelect: (Tab) -> Unit, onReader: () -> Unit) {
    val c = Ts.colors
    Column(
        Modifier.width(88.dp).fillMaxHeight().background(c.surface2).statusBarsPadding().navigationBarsPadding().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(48.dp).clip(Pill).clickable(onClick = onReader), contentAlignment = Alignment.Center) { CrossMark() }
        TABS.forEach { t -> NavItem(t, t.tab == current, Modifier.fillMaxWidth()) { onSelect(t.tab) } }
    }
}

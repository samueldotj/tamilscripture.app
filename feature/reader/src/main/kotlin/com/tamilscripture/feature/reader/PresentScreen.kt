package com.tamilscripture.feature.reader

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tamilscripture.core.designsystem.component.PillStyle
import com.tamilscripture.core.designsystem.component.TsPillButton
import com.tamilscripture.core.designsystem.theme.ThemeMode
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.designsystem.theme.TsTheme
import com.tamilscripture.core.model.Chapter
import com.tamilscripture.core.model.Passage
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.model.label
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr
import kotlinx.coroutines.flow.firstOrNull

/** One slide: a verse and its reference. */
internal data class Slide(val text: String, val reference: String)

/**
 * Present mode (roadmap M8-8): a passage verse by verse, full screen, for a room. The keys
 * that drive a slide clicker (→, Space, Page Down; ←, Page Up) and taps on either side move
 * on and back. With a second display attached (HDMI, cast, an ALOS monitor) the verses go
 * there through [Presentation] and this screen becomes the presenter's view.
 */
@Composable
fun PresentScreen(passage: Passage, onExit: () -> Unit) {
    val graph = LocalAppServices.current.graph
    val lang = LocalUiLang.current
    val manifest by graph.content.manifest.collectAsStateWithLifecycle()
    val chapter by produceState<Chapter?>(null, passage) { value = graph.content.chapter(passage.version, passage.book, passage.chapter).firstOrNull()?.value }
    val slides = remember(chapter, manifest, lang) {
        val ch = chapter ?: return@remember emptyList()
        val book = manifest?.book(passage.book)
        ch.verseNumbers.map { v -> Slide(ch.verseText(v), book?.label(lang, passage.chapter, v) ?: "${passage.book} ${passage.chapter}:$v") }
    }
    var index by rememberSaveable(passage) { mutableIntStateOf(0) }
    LaunchedEffect(slides) {
        val start = slides.indexOfFirst { it.reference.endsWith(":${passage.verse}") }
        if (passage.verse != null && start >= 0) index = start
    }
    val next = { if (index < slides.lastIndex) index++ }
    val prev = { if (index > 0) index-- }

    ImmersiveMode()
    BackHandler(onBack = onExit)
    val external = rememberPresentationDisplay()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(PRESENT_BG)
            .focusRequester(focus).focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionRight, Key.Spacebar, Key.PageDown, Key.DirectionDown -> { next(); true }
                    Key.DirectionLeft, Key.PageUp, Key.DirectionUp -> { prev(); true }
                    Key.Escape -> { onExit(); true }
                    else -> false
                }
            }
            .pointerInput(slides) {
                detectTapGestures { p -> if (p.x > size.width / 2f) next() else prev() }
            },
    ) {
        val slide = slides.getOrNull(index)
        if (slide == null) {
            CircularProgressIndicator(color = Ts.colors.accent, modifier = Modifier.align(Alignment.Center))
        } else if (external != null) {
            ExternalSlide(external, slide)
            PresenterView(slide, slides.getOrNull(index + 1), index, slides.size, lang, onExit)
        } else {
            SlideView(slide)
            Text("${index + 1} / ${slides.size}", color = PRESENT_MUTED, fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp))
        }
    }
}

private val PRESENT_BG = androidx.compose.ui.graphics.Color(0xFF111316)
private val PRESENT_INK = androidx.compose.ui.graphics.Color(0xFFF4EFE6)
private val PRESENT_MUTED = androidx.compose.ui.graphics.Color(0xFF9A948A)
private val PRESENT_GOLD = androidx.compose.ui.graphics.Color(0xFFD9B25C)

/** A verse sized to fill the screen: larger when short, smaller when long. */
@Composable
internal fun SlideView(slide: Slide) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val type = Ts.type
    BoxWithConstraints(Modifier.fillMaxSize().background(PRESENT_BG).padding(horizontal = 32.dp, vertical = 40.dp), contentAlignment = Alignment.Center) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        // The largest size, from 76 sp down, at which the verse fits with room for its reference
        // and no word is broken across lines (Tamil words are long; a narrow phone splits them).
        val size = remember(slide.text, widthPx, heightPx) {
            val longest = slide.text.split(' ').maxByOrNull { it.length }.orEmpty()
            var sp = 76f
            while (sp > 18f) {
                val style = type.scripture(sp.sp, 1.45f)
                val word = measurer.measure(longest, style, maxLines = 1, softWrap = false)
                val all = measurer.measure(slide.text, style, constraints = Constraints(maxWidth = widthPx))
                val reserve = with(density) { (sp * 0.45f * 2.2f + 28f).dp.roundToPx() }
                if (word.size.width <= widthPx && all.size.height + reserve <= heightPx) break
                sp -= 2f
            }
            sp
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Text(
                slide.text, color = PRESENT_INK, textAlign = TextAlign.Center,
                style = Ts.type.scripture(size.sp, 1.45f),
                modifier = Modifier.widthIn(max = 1400.dp),
            )
            Text(slide.reference, color = PRESENT_GOLD, style = Ts.type.label.copy(fontSize = (size * 0.45f).coerceAtLeast(16f).sp))
        }
    }
}

/** On the device while the room sees the second display: now, next, and the way out. */
@Composable
private fun PresenterView(now: Slide, next: Slide?, index: Int, count: Int, lang: UiLang, onExit: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (lang == UiLang.Tamil) "இரண்டாவது திரையில் காட்டப்படுகிறது" else "Showing on the second display") + " · ${index + 1} / $count",
                color = PRESENT_MUTED, fontSize = 14.sp, modifier = Modifier.weight(1f),
            )
            TsPillButton(tr("முடி", "End"), onExit, style = PillStyle.Filled, height = 36.dp)
        }
        Text(now.reference, color = PRESENT_GOLD, fontSize = 16.sp)
        Text(now.text, color = PRESENT_INK, style = Ts.type.scripture(24.sp, 1.5f))
        if (next != null) {
            Text((if (lang == UiLang.Tamil) "அடுத்து · " else "Next · ") + next.reference, color = PRESENT_MUTED, fontSize = 14.sp, modifier = Modifier.padding(top = 18.dp))
            Text(next.text, color = PRESENT_MUTED, style = Ts.type.scripture(18.sp, 1.5f), maxLines = 3)
        }
    }
}

/** Full screen while presenting: system bars hidden until swiped in, restored on leaving. */
@Composable
private fun ImmersiveMode() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/** The first display meant for presentations, tracking displays being attached and removed. */
@Composable
private fun rememberPresentationDisplay(): Display? {
    val context = LocalContext.current
    val dm = remember { context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }
    var display by remember { mutableStateOf(dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()) }
    DisposableEffect(dm) {
        val listener = object : DisplayManager.DisplayListener {
            fun refresh() { display = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull() }
            override fun onDisplayAdded(displayId: Int) = refresh()
            override fun onDisplayRemoved(displayId: Int) = refresh()
            override fun onDisplayChanged(displayId: Int) = refresh()
        }
        dm.registerDisplayListener(listener, null)
        onDispose { dm.unregisterDisplayListener(listener) }
    }
    return display
}

/** Shows [slide] on the second display, kept in step with the presenter's screen. */
@Composable
private fun ExternalSlide(display: Display, slide: Slide) {
    val context = LocalContext.current
    val view = LocalView.current
    val services = LocalAppServices.current
    val lang = LocalUiLang.current
    val state = remember { mutableStateOf(slide) }
    state.value = slide
    DisposableEffect(display) {
        val presentation = object : Presentation(context, display) {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContentView(
                    ComposeView(context).apply {
                        // A Presentation is a dialog: lend it the activity's lifecycle and state owners.
                        setViewTreeLifecycleOwner(view.findViewTreeLifecycleOwner())
                        setViewTreeViewModelStoreOwner(view.findViewTreeViewModelStoreOwner())
                        setViewTreeSavedStateRegistryOwner(view.findViewTreeSavedStateRegistryOwner())
                        setContent {
                            TsTheme(ThemeMode.Dark) {
                                CompositionLocalProvider(LocalAppServices provides services, LocalUiLang provides lang) {
                                    SlideView(state.value)
                                }
                            }
                        }
                    },
                )
            }
        }
        runCatching { presentation.show() }
        onDispose { presentation.dismiss() }
    }
}

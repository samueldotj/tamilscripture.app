package com.tamilscripture.feature.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.tamilscripture.core.designsystem.component.Kicker
import com.tamilscripture.core.designsystem.component.VerseImage
import com.tamilscripture.core.designsystem.icon.TsIcons
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.BibleVersion
import com.tamilscripture.core.model.Chapter
import com.tamilscripture.core.model.ContentManifest
import com.tamilscripture.core.model.Passage
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.model.label
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The version shown beside [v] in a shared image, as on the website's single-verse page
 * (companionOf): the site's default version, or, for a version in its language, the
 * default of the other language. Null when there is none.
 */
internal fun companionOf(m: ContentManifest, v: BibleVersion): BibleVersion? {
    val site = m.version(m.defaultVersion) ?: return null
    if (v.lang != site.lang) return site
    return m.versions.firstOrNull { it.lang != v.lang && it.default } ?: m.versions.firstOrNull { it.lang != v.lang }
}

/** The selected verses of [ch] as the renderer takes them, with their reference line ("யோவான் 3:16 · IRV-TA"). */
private fun passageOf(ch: Chapter, version: BibleVersion?, manifest: ContentManifest?, verses: List<Int>): VerseImage.Passage? {
    val lang = version?.lang ?: "ta"
    val list = verses.map { VerseImage.Verse(it.toString(), ch.verseText(it)) }.filter { it.text.isNotBlank() }
    if (list.isEmpty()) return null
    val book = manifest?.book(ch.book)
    val label = book?.label(if (lang == "ta") UiLang.Tamil else UiLang.English, ch.chapter, verses.first(), verses.last())
        ?: "${ch.book} ${ch.chapter}:${verses.first()}"
    return VerseImage.Passage(lang, list, "$label · ${version?.short ?: ch.version}")
}

/**
 * படமாகப் பகிர் · Share as image (design 3b, the website's ShareImage picker in a sheet):
 * a live preview, then template, icon, size, the versions and a light or dark ground;
 * Download, Share and Copy stay pinned under the scrolling choices.
 */
@Composable
internal fun ShareImageSheet(
    passage: Passage,
    verses: List<Int>,
    chapter: Chapter,
    manifest: ContentManifest?,
    /** Link sent with the image ("…/irvtam/john/3.16"). */
    url: String,
    /** "download", "sheet" or "copy", with the choices, for the traffic numbers. */
    onShared: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val lang = LocalUiLang.current
    val graph = LocalAppServices.current.graph
    val version = manifest?.version(passage.version)
    val book = manifest?.book(passage.book)
    val label = book?.label(lang, passage.chapter, verses.first(), verses.last()) ?: ""

    // The other language's copy of the chapter; offline or missing, the image has one version.
    val companion = remember(manifest, version) { if (manifest != null && version != null) companionOf(manifest, version) else null }
    val second by produceState<Chapter?>(null, companion, passage.book, passage.chapter) {
        val code = companion?.code ?: return@produceState
        value = graph.content.chapter(code, passage.book, passage.chapter).catch { }.firstOrNull()?.value
    }
    val passages = remember(chapter, second, verses) {
        listOfNotNull(passageOf(chapter, version, manifest, verses), second?.let { passageOf(it, companion, manifest, verses) })
    }
    val many = verses.size > 1
    val range = "${verses.first()}" + (if (many) "–${verses.last()}" else "")
    val fileName = "${book?.slug ?: passage.book.lowercase()}-${passage.chapter}-${range.replace('–', '-')}.png"
    TsSheet(onDismiss) {
        ShareImagePanel(label, passage.book, passages, "${passage.chapter}:$range", many, fileName, url, onShared, onDismiss)
    }
}

/** The sheet's contents: header, scrolling choices, pinned actions. */
@Composable
internal fun ShareImagePanel(
    label: String,
    /** The book's code, for the auto icon. */
    book: String,
    passages: List<VerseImage.Passage>,
    chapterVerse: String,
    many: Boolean,
    fileName: String,
    url: String,
    onShared: (String) -> Unit,
    onClose: () -> Unit,
) {
    val c = Ts.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var template by rememberSaveable { mutableStateOf(VerseImage.Template.Plate) }
    var size by rememberSaveable { mutableStateOf(VerseImage.Size.Square) }
    /** Null: the book's own icon. */
    var icon by rememberSaveable { mutableStateOf<VerseImage.Icon?>(null) }
    var theme by rememberSaveable { mutableStateOf(VerseImage.Theme.Light) }
    /** Which versions go in: -1 both, or the index of one. */
    var pick by rememberSaveable { mutableStateOf(-1) }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) { if (toast != null) { delay(1800); toast = null } }

    val options = if (passages.isEmpty()) null else VerseImage.Options(
        size.w, size.h, template, icon ?: VerseImage.autoIcon(book), theme,
        if (pick < 0 || passages.size < 2) passages.take(2) else listOf(passages[pick]),
        chapterVerse, many,
    )
    // Drawn off the main thread: the preview at half size, the thumbnails at a quarter.
    val preview by produceState<ImageBitmap?>(null, options) {
        val o = options ?: return@produceState
        value = withContext(Dispatchers.Default) { VerseImage.render(context, o.copy(w = o.w / 2, h = o.h / 2)).asImageBitmap() }
    }
    val thumbs by produceState<Map<VerseImage.Template, ImageBitmap>>(emptyMap(), options?.copy(template = VerseImage.Template.Plate)) {
        val o = options ?: return@produceState
        value = withContext(Dispatchers.Default) {
            VerseImage.Template.entries.associateWith { VerseImage.render(context, o.copy(template = it, w = o.w / 4, h = o.h / 4)).asImageBitmap() }
        }
    }

    val detail = "${template.name.lowercase()}.${size.name.lowercase()}.${theme.name.lowercase()}"
    val saved = tr("படம் பதிவிறக்கப்பட்டது", "Image downloaded")
    val copied = tr("படம் நகலெடுக்கப்பட்டது", "Image copied")
    val failed = tr("நகலெடுக்க முடியவில்லை", "Could not copy")
    fun act(action: suspend (VerseImage.Options) -> Unit) {
        val o = options ?: return
        scope.launch { action(o) }
    }

    run {
        // The whole sheet at most 88% of the window: less its handle and the navigation bar below.
        val window = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
        val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val maxHeight = window * 0.88f - navBar - 24.dp
        Column(Modifier.heightIn(max = maxHeight)) {
            Row(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Kicker(tr("படமாகப் பகிர்", "Share as image"))
                    Text(label, style = Ts.type.cardTitle, color = c.ink, maxLines = 1)
                }
                Text("${size.w} × ${size.h}", style = Ts.type.caption, color = c.muted)
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(c.surface2).clickable(role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(TsIcons.Close, tr("மூடு", "Close"), Modifier.size(18.dp), tint = c.ink2) }
            }

            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // The preview keeps its shape: the story picture shows narrower.
                Box(
                    Modifier.padding(horizontal = 18.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val maxW: Dp = when (size) { VerseImage.Size.Story -> 150.dp; VerseImage.Size.Landscape -> Dp.Unspecified; else -> 220.dp }
                    Box(
                        Modifier.then(if (maxW != Dp.Unspecified) Modifier.widthIn(max = maxW) else Modifier).fillMaxWidth()
                            .aspectRatio(size.w.toFloat() / size.h)
                            .shadow(14.dp, RoundedCornerShape(2.dp))
                            .background(Color(VerseImage.background(theme))),
                    ) {
                        preview?.let { Image(it, tr("முன்னோட்டம்", "Preview"), Modifier.fillMaxWidth().fillMaxHeight(), contentScale = ContentScale.Fit) }
                    }
                }

                Section(tr("வடிவம்", "Template")) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VerseImage.Template.entries.forEach { t ->
                            Choice(template == t, { template = t }, Modifier.width(84.dp), PaddingValues(start = 5.dp, end = 5.dp, top = 6.dp, bottom = 6.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(c.surface2), contentAlignment = Alignment.Center) {
                                        thumbs[t]?.let { Image(it, null, Modifier.fillMaxWidth().fillMaxHeight(), contentScale = ContentScale.Fit) }
                                    }
                                    ChoiceText(templateName(t), template == t, small = true)
                                }
                            }
                        }
                    }
                }

                Section(tr("சின்னம்", "Icon")) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf<VerseImage.Icon?>(null) + VerseImage.Icon.entries).forEach { i ->
                            Choice(icon == i, { icon = i }) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(remember(i) { lineIcon(i?.paths ?: SPARKLE) }, null, Modifier.size(18.dp), tint = if (icon == i) c.accent else c.ink)
                                    ChoiceText(iconName(i), icon == i)
                                }
                            }
                        }
                    }
                }

                Section(tr("அளவு", "Size")) {
                    Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VerseImage.Size.entries.forEach { s ->
                            Choice(size == s, { size = s }, Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    val (w, h) = when (s) { VerseImage.Size.Square -> 14 to 14; VerseImage.Size.Story -> 10 to 17; VerseImage.Size.Landscape -> 19 to 10 }
                                    Box(Modifier.size(w.dp, h.dp).border(1.5.dp, if (size == s) c.accent else c.ink, RoundedCornerShape(2.dp)))
                                    ChoiceText(sizeName(s), size == s)
                                }
                            }
                        }
                    }
                }

                if (passages.size > 1) {
                    Section(tr("மொழி", "Language")) {
                        val names = passages.map { langName(it) }
                        Segments(listOf(names[0] to null, tr("இரண்டும்", "Both") to null, names[1] to null), listOf(0, -1, 1).indexOf(pick)) { pick = listOf(0, -1, 1)[it] }
                    }
                }

                Section(tr("தோற்றம்", "Theme")) {
                    Segments(
                        VerseImage.Theme.entries.map { (if (it == VerseImage.Theme.Light) tr("வெளிச்சம்", "Light") else tr("இரவு", "Dark")) to Color(VerseImage.background(it)) },
                        theme.ordinal,
                    ) { theme = VerseImage.Theme.entries[it] }
                }
            }

            Box {
                Column {
                    Box(Modifier.fillMaxWidth().background(c.line).heightIn(min = 1.5.dp, max = 1.5.dp))
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Action(tr("பதிவிறக்கு", "Download"), DOWNLOAD, primary = true, modifier = Modifier.weight(1.4f)) {
                            act { o ->
                                val ok = withContext(Dispatchers.IO) { saveToPictures(context, o, fileName) }
                                if (ok) { toast = saved; onShared("download.$detail") } else shareImage(context, o, fileName, label, url).also { onShared("sheet.$detail") }
                            }
                        }
                        Action(tr("பகிர்", "Share"), TsIcons.Share, modifier = Modifier.weight(1f)) {
                            act { o -> shareImage(context, o, fileName, label, url); onShared("sheet.$detail") }
                        }
                        Action(tr("நகல்", "Copy"), TsIcons.Copy, modifier = Modifier.weight(1f)) {
                            act { o ->
                                val ok = runCatching { copyImage(context, o, fileName, label) }.isSuccess
                                // Android 13 and later confirm a copy themselves.
                                if (!ok) toast = failed else { if (Build.VERSION.SDK_INT < 33) toast = copied; onShared("copy.$detail") }
                            }
                        }
                    }
                }
                toast?.let {
                    Text(
                        it, style = Ts.type.label, color = c.bg,
                        modifier = Modifier.align(Alignment.TopCenter).offset(y = (-40).dp).clip(RoundedCornerShape(8.dp)).background(c.ink).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun templateName(t: VerseImage.Template) = when (t) {
    VerseImage.Template.Plate -> tr("தகடு", "Plate")
    VerseImage.Template.Margin -> tr("ஓரம்", "Margin")
    VerseImage.Template.Rules -> tr("கோடுகள்", "Rules")
    VerseImage.Template.Numeral -> tr("எண்", "Numeral")
    VerseImage.Template.Initial -> tr("முதலெழுத்து", "Initial")
    VerseImage.Template.Corner -> tr("மூலை", "Corner")
}

@Composable
private fun iconName(i: VerseImage.Icon?) = when (i) {
    null -> tr("தானாக", "Auto")
    VerseImage.Icon.Cross -> tr("சிலுவை", "Cross")
    VerseImage.Icon.Bible -> tr("வேதாகமம்", "Bible")
    VerseImage.Icon.Church -> tr("ஆலயம்", "Church")
    VerseImage.Icon.Mountain -> tr("மலை", "Mountain")
    VerseImage.Icon.Desert -> tr("பாலைவனம்", "Desert")
    VerseImage.Icon.Sea -> tr("கடல்", "Sea")
}

@Composable
private fun sizeName(s: VerseImage.Size) = when (s) {
    VerseImage.Size.Square -> tr("சதுரம்", "Square")
    VerseImage.Size.Story -> tr("ஸ்டேட்டஸ்", "Status")
    VerseImage.Size.Landscape -> tr("கிடை", "Landscape")
}

/** Tamil and English by name, any other version by its short name. */
@Composable
private fun langName(p: VerseImage.Passage) = when (p.lang) {
    "ta" -> tr("தமிழ்", "Tamil")
    "en" -> "English"
    else -> p.ref.substringAfterLast(" · ")
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Kicker(title, Modifier.padding(horizontal = 18.dp))
        content()
    }
}

/** One option: an accent outline and wash when chosen. */
@Composable
private fun Choice(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable () -> Unit,
) {
    val c = Ts.colors
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier.heightIn(min = 44.dp).clip(shape)
            .background(if (selected) c.accentSoft else c.surface)
            .border(1.5.dp, if (selected) c.accent else c.line2, shape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ChoiceText(text: String, selected: Boolean, small: Boolean = false) {
    val c = Ts.colors
    BasicText(
        text, style = Ts.type.label.copy(fontSize = if (small) 12.sp else 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) c.accent else c.ink),
        maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = if (small) 12.sp else 13.sp),
    )
}

/** A row of equal parts, the chosen one filled; a part may carry a colour swatch. */
@Composable
private fun Segments(options: List<Pair<String, Color?>>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Ts.colors
    val shape = RoundedCornerShape(11.dp)
    Row(Modifier.padding(horizontal = 18.dp).fillMaxWidth().heightIn(min = 44.dp).clip(shape).border(1.5.dp, c.line2, shape).background(c.surface)) {
        options.forEachIndexed { i, (label, swatch) ->
            val on = i == selected
            Row(
                Modifier.weight(1f).heightIn(min = 44.dp).background(if (on) c.accent else Color.Transparent)
                    .selectable(on, role = Role.RadioButton) { onSelect(i) }.padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                if (swatch != null) Box(Modifier.size(14.dp).clip(CircleShape).background(swatch).border(1.dp, c.line2, CircleShape))
                BasicText(
                    label, style = Ts.type.label.copy(fontWeight = FontWeight.SemiBold, color = if (on) c.onAccent else c.ink2), maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 13.sp),
                )
            }
        }
    }
}

@Composable
private fun Action(text: String, icon: ImageVector, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    val c = Ts.colors
    val shape = RoundedCornerShape(12.dp)
    val fg = if (primary) c.onAccent else c.ink
    Row(
        modifier.heightIn(min = 46.dp).clip(shape).background(if (primary) c.accent else c.surface)
            .then(if (primary) Modifier else Modifier.border(1.5.dp, c.line2, shape))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = fg)
        BasicText(text, style = Ts.type.label.copy(color = fg), maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp))
    }
}

private val SPARKLE = listOf("M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z")
private val DOWNLOAD = lineIcon(listOf("M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m7 10 5 5 5-5", "M12 15V3"))

private fun lineIcon(paths: List<String>): ImageVector = ImageVector.Builder("line", 24.dp, 24.dp, 24f, 24f).apply {
    paths.forEach { addPath(addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) }
}.build()

/** The image at full size as a PNG in the cache, shared through the FileProvider. */
private suspend fun cached(context: Context, o: VerseImage.Options, name: String): Uri = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, name)
    val bitmap = VerseImage.render(context, o)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    FileProvider.getUriForFile(context, context.packageName + ".share", file)
}

/**
 * What goes with the picture, as on the website: each version's verses and its reference,
 * then the link (in the text, since apps that take a picture often drop a separate link).
 */
internal fun shareText(o: VerseImage.Options, url: String): String =
    (o.passages.map { p -> p.verses.joinToString(" ") { (if (o.many) "${it.n} " else "") + it.text } + "\n— " + p.ref } + url)
        .joinToString("\n\n")

private suspend fun shareImage(context: Context, o: VerseImage.Options, name: String, ref: String, url: String) {
    val uri = cached(context, o, name)
    val send = Intent(Intent.ACTION_SEND).setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, shareText(o, url))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // The chooser shows the picture as its preview.
    send.clipData = ClipData.newRawUri(ref, uri)
    context.startActivity(Intent.createChooser(send, ref))
}

private suspend fun copyImage(context: Context, o: VerseImage.Options, name: String, ref: String) {
    val uri = cached(context, o, name)
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newUri(context.contentResolver, ref, uri))
}

/** Into Pictures/Tamil Scripture. Before Android 10 that needs a permission, so the caller shares instead. */
private fun saveToPictures(context: Context, o: VerseImage.Options, name: String): Boolean {
    if (Build.VERSION.SDK_INT < 29) return false
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Tamil Scripture")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return runCatching {
        val bitmap = VerseImage.render(context, o)
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: error("no stream")
        bitmap.recycle()
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        true
    }.getOrElse { resolver.delete(uri, null, null); false }
}

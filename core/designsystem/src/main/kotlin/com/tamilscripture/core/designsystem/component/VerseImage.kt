package com.tamilscripture.core.designsystem.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.core.graphics.PathParser
import com.tamilscripture.core.designsystem.theme.CormorantGaramond
import com.tamilscripture.core.designsystem.theme.Lora
import com.tamilscripture.core.designsystem.theme.NotoSans
import com.tamilscripture.core.designsystem.theme.NotoSerifTamil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A verse as a picture to share (roadmap M8-6), the website's renderer
 * (apps/web/src/lib/reader/verse-image.ts) drawn the same way here: six templates, a
 * line icon as a faint watermark, three sizes, light or dark, and one or two versions,
 * the second a step smaller under a short gold rule. The type steps down until it fits.
 */
object VerseImage {
    enum class Template { Plate, Margin, Rules, Numeral, Initial, Corner }

    /** The watermark, as 24×24 line drawings (the design's SVG paths). */
    enum class Icon(val paths: List<String>) {
        Cross(listOf("M11 2a2 2 0 0 0-2 2v5H4a2 2 0 0 0-2 2v2c0 1.1.9 2 2 2h5v5c0 1.1.9 2 2 2h2a2 2 0 0 0 2-2v-5h5a2 2 0 0 0 2-2v-2a2 2 0 0 0-2-2h-5V4a2 2 0 0 0-2-2h-2z")),
        Bible(listOf("M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z", "M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z")),
        Church(listOf("m18 7 4 2v11a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9l4-2", "M14 22v-4a2 2 0 0 0-2-2a2 2 0 0 0-2 2v4", "M18 22V5l-6-3-6 3v17", "M12 7v5", "M10 9h4")),
        Mountain(listOf("m8 3 4 8 5-5 5 15H2L8 3z")),
        Desert(listOf(
            "M13 8c0-2.76-2.46-5-5.5-5S2 5.24 2 8h2l1-1 1 1h4",
            "M13 7.14A5.82 5.82 0 0 1 16.5 6c3.04 0 5.5 2.24 5.5 5h-3l-1-1-1 1h-3",
            "M5.89 9.71c-2.15 2.15-2.3 5.47-.35 7.43l4.24-4.25.7-.7.71-.71 2.12-2.12c-1.95-1.96-5.27-1.8-7.42.35z",
            "M11 15.5c.5 2.5-.17 4.5-1 6.5h4c2-5.5-.5-12-1-14",
        )),
        Sea(listOf(
            "M2 6c.6.5 1.2 1 2.5 1C7 7 7 5 9.5 5c2.6 0 2.4 2 5 2 2.5 0 2.5-2 5-2 1.3 0 1.9.5 2.5 1",
            "M2 12c.6.5 1.2 1 2.5 1 2.5 0 2.5-2 5-2 2.6 0 2.4 2 5 2 2.5 0 2.5-2 5-2 1.3 0 1.9.5 2.5 1",
            "M2 18c.6.5 1.2 1 2.5 1 2.5 0 2.5-2 5-2 2.6 0 2.4 2 5 2 2.5 0 2.5-2 5-2 1.3 0 1.9.5 2.5 1",
        ));

        internal val parsed: List<Path> by lazy { paths.map { PathParser.createPathFromPathData(it) } }
    }

    /** Square for feeds, story for WhatsApp status, landscape for link previews. */
    enum class Size(val w: Int, val h: Int) { Square(1080, 1080), Story(1080, 1920), Landscape(1200, 630) }

    enum class Theme { Light, Dark }

    data class Verse(val n: String, val text: String)

    /** One version's text: its verses and its reference line ("யோவான் 3:16 · IRV-TA"). */
    data class Passage(val lang: String, val verses: List<Verse>, val ref: String)

    data class Options(
        val w: Int,
        val h: Int,
        val template: Template,
        val icon: Icon,
        val theme: Theme,
        /** One or two passages; the second is set a step smaller. */
        val passages: List<Passage>,
        /** The chapter and verses alone, for the numeral template ("3:16"). */
        val chapterVerse: String,
        /** More than one verse: each starts with its number. */
        val many: Boolean,
    )

    private val AUTO_ICON = mapOf(
        "JHN" to Icon.Cross, "MAT" to Icon.Cross, "MRK" to Icon.Cross, "LUK" to Icon.Cross,
        "PSA" to Icon.Mountain, "EXO" to Icon.Desert, "NUM" to Icon.Desert, "DEU" to Icon.Desert,
        "JON" to Icon.Sea, "ACT" to Icon.Sea, "GEN" to Icon.Mountain, "ISA" to Icon.Mountain,
        "ROM" to Icon.Church, "EPH" to Icon.Church, "REV" to Icon.Church,
    )

    /** The icon a book gets when the reader leaves it on auto. */
    fun autoIcon(book: String): Icon = AUTO_ICON[book] ?: Icon.Bible

    private class Palette(val bg: Int, val text: Int, val muted: Int, val accent: Int, val div: Int, val wm: Float)

    private val LIGHT = Palette(0xFFF3F2F2.toInt(), 0xFF201F1D.toInt(), 0xFF605D5D.toInt(), 0xFFB68235.toInt(), 0x2E201F1D, 0.11f)
    private val DARK = Palette(0xFF1F1D1C.toInt(), 0xFFF3F2F2.toInt(), 0xFFBAB6B6.toInt(), 0xFFE1AD66.toInt(), 0x33F3F2F2, 0.13f)

    /** The swatch shown for a theme in the picker. */
    fun background(theme: Theme): Int = if (theme == Theme.Dark) DARK.bg else LIGHT.bg

    private class Faces(
        val ta: Typeface, val taBold: Typeface,
        val en: Typeface, val enBold: Typeface,
        val head: Typeface, val headBold: Typeface,
        val sans: Typeface,
    )

    @Volatile private var faces: Faces? = null

    private fun faces(context: Context): Faces = faces ?: synchronized(this) {
        faces ?: run {
            val resolver = createFontFamilyResolver(context.applicationContext)
            fun tf(family: FontFamily, weight: Int) = resolver.resolve(family, FontWeight(weight)).value as Typeface
            val en = tf(Lora, 400)
            Faces(
                tf(NotoSerifTamil, 400), tf(NotoSerifTamil, 600),
                en, Typeface.create(en, Typeface.BOLD),
                tf(CormorantGaramond, 400), tf(CormorantGaramond, 600),
                tf(NotoSans, 700),
            ).also { faces = it }
        }
    }

    /** Draws [o] at its full size. Safe off the main thread; each call has its own paint. */
    fun render(context: Context, o: Options): Bitmap {
        val bitmap = Bitmap.createBitmap(o.w, o.h, Bitmap.Config.ARGB_8888)
        Drawer(Canvas(bitmap), faces(context), o).draw()
        return bitmap
    }

    private class Tok(val t: String, val n: String) { var w = 0f }
    private class Line { val toks = mutableListOf<Tok>(); var ind = 0f }
    private class Indent(val w: Float, val lines: Int)
    private class Block(var toks: List<Tok>, val ta: Boolean, val lh: Float, val k: Float) { var ind: ((Float) -> Indent)? = null }
    private class Laid(val b: Block, val size: Float, val lines: List<Line>, val sp: Float, val h: Float)
    private class Fit(val laid: List<Laid>, val h: Float)
    private class RefLine(val text: String, val face: Typeface, val k: Float, val muted: Boolean)
    private enum class Align { Left, Center, Right }

    private class Drawer(val canvas: Canvas, val f: Faces, val o: Options) {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG)
        val C = if (o.theme == Theme.Dark) DARK else LIGHT
        val W = o.w.toFloat()
        val H = o.h.toFloat()

        fun font(face: Typeface, size: Float) { p.typeface = face; p.textSize = size }
        fun face(b: Block) = if (b.ta) f.ta else f.en
        fun measure(s: String) = p.measureText(s)

        fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int) {
            p.style = Paint.Style.FILL; p.color = color
            canvas.drawRect(x, y, x + w, y + h, p)
        }

        fun strokeRect(x: Float, y: Float, w: Float, h: Float, color: Int, width: Float, alpha: Float = 1f) {
            p.style = Paint.Style.STROKE; p.color = color; p.strokeWidth = width
            p.alpha = (p.alpha * alpha).toInt()
            canvas.drawRect(x, y, x + w, y + h, p)
            p.style = Paint.Style.FILL
        }

        /** Text centred on [cy], as a canvas's 'middle' baseline. */
        fun textMiddle(s: String, x: Float, cy: Float, color: Int) {
            p.style = Paint.Style.FILL; p.color = color; p.textAlign = Paint.Align.LEFT
            val fm = p.fontMetrics
            canvas.drawText(s, x, cy - (fm.ascent + fm.descent) / 2f, p)
        }

        fun text(s: String, x: Float, y: Float, color: Int, align: Align) {
            p.style = Paint.Style.FILL; p.color = color
            p.textAlign = when (align) { Align.Left -> Paint.Align.LEFT; Align.Center -> Paint.Align.CENTER; Align.Right -> Paint.Align.RIGHT }
            canvas.drawText(s, x, y, p)
            p.textAlign = Paint.Align.LEFT
        }

        fun tokens(verses: List<Verse>): List<Tok> = verses.flatMap { v ->
            v.text.split(Regex("\\s+")).filter { it.isNotEmpty() }.mapIndexed { i, w -> Tok(w, if (o.many && i == 0) v.n else "") }
        }

        fun wrap(toks: List<Tok>, face: Typeface, size: Float, maxW: Float, ind: Indent?): Pair<List<Line>, Float> {
            val lines = mutableListOf<Line>()
            var line = Line()
            var x = 0f
            fun indented() = ind != null && lines.size < ind.lines
            fun lim() = if (indented()) maxW - ind!!.w else maxW
            fun push() { line.ind = if (indented()) ind!!.w else 0f; lines += line; line = Line(); x = 0f }
            font(face, size)
            val sp = measure(" ")
            for (tk in toks) {
                var w = 0f
                if (tk.n.isNotEmpty()) { font(f.sans, size * 0.42f); w += measure(tk.n) + size * 0.1f }
                font(face, size)
                w += measure(tk.t)
                if (line.toks.isNotEmpty() && x + sp + w > lim()) push()
                line.toks += Tok(tk.t, tk.n).also { it.w = w }
                x += (if (line.toks.size > 1) sp else 0f) + w
            }
            if (line.toks.isNotEmpty()) push()
            return lines to sp
        }

        /** Steps the type down from [start] until the blocks fit [maxH] (or reach [min]). */
        fun fitBlocks(blocks: List<Block>, maxW: Float, maxH: Float, start: Float, min: Float): Fit {
            var fit = Fit(emptyList(), 0f)
            var size = start
            while (size >= min) {
                var h = 0f
                val laid = blocks.mapIndexed { i, b ->
                    val s = size * b.k
                    val (lines, sp) = wrap(b.toks, face(b), s, maxW, b.ind?.invoke(s))
                    val bh = lines.size * s * b.lh
                    h += bh + (if (i > 0) size * 1.1f else 0f)
                    Laid(b, s, lines, sp, bh)
                }
                fit = Fit(laid, h)
                if (h <= maxH || size - 2 < min) break
                size -= 2
            }
            return fit
        }

        fun drawBlocks(fit: Fit, x: Float, top: Float, maxW: Float, align: Align) {
            var y = top
            fit.laid.forEachIndexed { i, b ->
                if (i > 0) {
                    // short gold hairline between the two versions
                    val rx = when (align) { Align.Center -> x + maxW / 2 - b.size * 0.6f; Align.Right -> x + maxW - b.size * 1.2f; Align.Left -> x }
                    fillRect(rx, y + b.size * 0.5f, b.size * 1.2f, max(1f, b.size * 0.03f), C.accent)
                    y += (b.size * 1.1f) / b.b.k
                }
                b.lines.forEachIndexed { li, ln ->
                    val lw = ln.toks.sumOf { it.w.toDouble() }.toFloat() + b.sp * (ln.toks.size - 1)
                    var cx = when (align) { Align.Center -> x + (maxW - lw) / 2; Align.Right -> x + maxW - lw; Align.Left -> x + ln.ind }
                    val cy = y + (li + 0.5f) * b.size * b.b.lh
                    for (t in ln.toks) {
                        if (t.n.isNotEmpty()) {
                            font(f.sans, b.size * 0.42f)
                            textMiddle(t.n, cx, cy - b.size * 0.3f, C.accent)
                            cx += measure(t.n) + b.size * 0.1f
                        }
                        font(face(b.b), b.size)
                        textMiddle(t.t, cx, cy, C.text)
                        cx += measure(t.t) + b.sp
                    }
                }
                y += b.h
            }
        }

        fun drawIcon(cx: Float, cy: Float, size: Float) {
            canvas.save()
            canvas.translate(cx - size / 2, cy - size / 2)
            canvas.scale(size / 24f, size / 24f)
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1.6f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                color = C.accent; alpha = (255 * C.wm).toInt()
            }
            for (path in o.icon.parsed) canvas.drawPath(path, stroke)
            canvas.restore()
        }

        fun refLines(): List<RefLine> = o.passages.mapIndexed { i, ps ->
            val ta = ps.lang == "ta"
            val k = (if (ta) 0.86f else 1f) * (if (i > 0) 0.88f else 1f)
            RefLine(ps.ref, if (ta) f.taBold else f.headBold, k, muted = i > 0)
        }

        fun drawRef(x: Float, y: Float, size: Float, align: Align): Float {
            var yy = y
            for (r in refLines()) {
                font(r.face, size * r.k)
                text(r.text, x, yy, if (r.muted) C.muted else C.accent, align)
                yy += size * 1.25f
            }
            return yy - y
        }

        fun firstGrapheme(s: String): String {
            if (s.isEmpty()) return s
            val it = android.icu.text.BreakIterator.getCharacterInstance()
            it.setText(s)
            val end = it.next()
            return if (end == android.icu.text.BreakIterator.DONE) s else s.substring(0, end)
        }

        fun draw() {
            val u = sqrt(W * H) / 1080f
            val m = min(W, H)
            val both = o.passages.size > 1
            val hair = max(1f, u)
            canvas.drawColor(C.bg)
            val blocks = o.passages.mapIndexed { i, ps ->
                val ta = ps.lang == "ta"
                Block(tokens(ps.verses), ta, if (ta) 1.6f else 1.42f, if (i > 0) 0.78f else 1f)
            }
            val base = (if (both) 58f else 66f) * u
            val min = 22f * u
            when (o.template) {
                Template.Plate -> {
                    val pad = 64f * u
                    strokeRect(pad, pad, W - 2 * pad, H - 2 * pad, C.div, max(1f, 1.2f * u))
                    val q = pad + 10f * u
                    strokeRect(q, q, W - 2 * q, H - 2 * q, C.accent, max(1f, 0.9f * u), alpha = 0.6f)
                    drawIcon(W / 2, H / 2 - 20f * u, m * 0.6f)
                    val x = pad + 70f * u
                    val maxW = W - 2 * x
                    val top = pad + 80f * u
                    val refH = (if (both) 2 else 1) * 36f * u + 40f * u
                    val maxH = H - top - pad - 70f * u - refH
                    val fit = fitBlocks(blocks, maxW, maxH, base, min)
                    drawBlocks(fit, x, top + (maxH - fit.h) / 2, maxW, Align.Center)
                    fillRect(W / 2 - 24f * u, H - pad - refH - 26f * u, 48f * u, hair, C.accent)
                    drawRef(W / 2, H - pad - refH + 20f * u, 30f * u, Align.Center)
                }
                Template.Margin -> {
                    drawIcon(W * 0.86f, H * 0.84f, m * 0.56f)
                    val rx = W * 0.1f
                    val top = H * 0.14f
                    val bot = H * 0.86f
                    fillRect(rx, top, max(1.5f, 2f * u), bot - top, C.accent)
                    val x = rx + 44f * u
                    val maxW = W - x - W * 0.1f
                    val rh = drawRef(x, top + 26f * u, 28f * u, Align.Left)
                    val t = top + rh + 40f * u
                    drawBlocks(fitBlocks(blocks, maxW, bot - t - 10f * u, base, min), x, t, maxW, Align.Left)
                }
                Template.Rules -> {
                    val y1 = H * 0.13f
                    val y2 = H * 0.87f
                    val x = W * 0.1f
                    val maxW = W * 0.8f
                    drawIcon(W / 2, H / 2, m * 0.6f)
                    fillRect(x, y1, maxW, hair, C.div)
                    fillRect(x, y2, maxW, hair, C.div)
                    val lines = refLines()
                    font(lines[0].face, 28f * u * lines[0].k)
                    text(lines[0].text, x, y1 - 22f * u, C.accent, Align.Left)
                    lines.getOrNull(1)?.let { second ->
                        font(second.face, 24f * u)
                        text(second.text, x + maxW, y1 - 22f * u, C.muted, Align.Right)
                    }
                    val t = y1 + 50f * u
                    val maxH = y2 - 50f * u - t
                    val fit = fitBlocks(blocks, maxW, maxH, base, min)
                    drawBlocks(fit, x, t + (maxH - fit.h) / 2, maxW, Align.Left)
                }
                Template.Initial -> {
                    drawIcon(W * 0.8f, H * 0.78f, m * 0.5f)
                    val x = W * 0.11f
                    val maxW = W * 0.78f
                    val top = H * 0.11f
                    val rh = drawRef(x, top + 24f * u, 26f * u, Align.Left)
                    fillRect(x, top + rh + 18f * u, maxW, hair, C.div)
                    // The first letter large in gold, three lines deep; the first lines step in around it.
                    val first = blocks[0]
                    val word = first.toks.firstOrNull()?.t.orEmpty()
                    val g = firstGrapheme(word)
                    val rest = word.substring(g.length)
                    first.toks = if (rest.isNotEmpty()) listOf(Tok(rest, "")) + first.toks.drop(1)
                    else first.toks.drop(1).mapIndexed { i, t -> if (i > 0) t else Tok(t.t, "") }
                    val capFace = if (first.ta) f.taBold else f.enBold
                    var capSize = 0f
                    var capAscent = 0f
                    first.ind = { s ->
                        capSize = s * first.lh * 3 * 0.78f
                        font(capFace, capSize)
                        val bounds = Rect().also { r -> p.getTextBounds(g, 0, g.length, r) }
                        capAscent = if (bounds.top < 0) -bounds.top.toFloat() else capSize * 0.7f
                        Indent(measure(g) + s * 0.35f, 3)
                    }
                    val t = top + rh + 60f * u
                    val maxH = H * 0.9f - t
                    val fit = fitBlocks(blocks, maxW, maxH, base, min)
                    val ty = t + min((maxH - fit.h) / 2, 40f * u)
                    val lead = fit.laid.firstOrNull()
                    if (capSize > 0f && lead != null && g.isNotEmpty()) {
                        font(capFace, capSize)
                        text(g, x, ty + lead.size * lead.b.lh * 0.5f - lead.size * 0.4f + capAscent, C.accent, Align.Left)
                    }
                    drawBlocks(fit, x, ty, maxW, Align.Left)
                }
                Template.Corner -> {
                    drawIcon(W * 0.14f, H * 0.9f, m * 1.05f)
                    val x = W * 0.12f
                    val maxW = W * 0.78f
                    val top = H * 0.1f
                    drawRef(x + maxW, top + 24f * u, 26f * u, Align.Right)
                    fillRect(x + maxW - 48f * u, top + 24f * u + (if (both) 2 else 1) * 32f * u + 6f * u, 48f * u, hair, C.accent)
                    val t = top + 130f * u
                    val maxH = H * 0.8f - t
                    val fit = fitBlocks(blocks, maxW, maxH, base, min)
                    drawBlocks(fit, x, t + min((maxH - fit.h) / 2, 60f * u), maxW, Align.Right)
                }
                Template.Numeral -> {
                    drawIcon(W / 2, H * 0.1f, m * 0.72f)
                    val x = W * 0.12f
                    val maxW = W * 0.76f
                    val yr = H * 0.845f
                    fillRect(x, yr, maxW, hair, C.div)
                    font(f.head, 96f * u)
                    text(o.chapterVerse, x + maxW, yr + 92f * u, C.accent, Align.Right)
                    drawRef(x, yr + 46f * u, 26f * u, Align.Left)
                    val t = H * 0.3f
                    val maxH = yr - 40f * u - t
                    val fit = fitBlocks(blocks, maxW, maxH, base, min)
                    drawBlocks(fit, x, t + (maxH - fit.h) / 2, maxW, Align.Center)
                }
            }
        }
    }
}

package com.tamilscripture.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Reading plans, ported from the website's `apps/web/src/lib/plans/schedule.ts` so both
 * clients compute identical days. A plan is a few tracks of chapters split evenly across
 * its days; progress is the set of "day-track" keys read.
 */
data class PlanUnit(val book: Book, val chapter: Int, val verses: IntRange? = null)

data class PlanTrack(val nameTa: String, val nameEn: String, val units: List<PlanUnit>)

data class ReadingPlan(
    val id: String,
    val titleTa: String,
    val titleEn: String,
    val blurbTa: String,
    val blurbEn: String,
    val days: Int,
    val tracks: List<PlanTrack>,
    val community: Boolean = false,
) {
    fun title(lang: UiLang) = if (lang == UiLang.Tamil) titleTa else titleEn
}

data class PlanPassage(val trackIndex: Int, val track: PlanTrack, val units: List<PlanUnit>, val chapters: Int)

data class PlanProgress(val start: LocalDate, val done: Set<String>)

data class PlanStats(
    val todayIndex: Int,
    val start: LocalDate,
    val end: LocalDate,
    val percent: Int,
    val expectedPercent: Int,
    val chapters: Int,
    val chaptersTotal: Int,
    val streak: Int,
    val missed: List<Int>,
    val estimatedFinish: LocalDate?,
    val finished: Boolean,
) {
    val behind: Int get() = missed.size
}

object Plans {
    fun key(day: Int, track: Int) = "$day-$track"

    private fun chapterCount(units: List<PlanUnit>) = units.count { it.verses == null || it.verses.first == 1 }

    private fun unitsOf(books: List<Book>) = books.flatMap { b -> (1..b.chapters).map { PlanUnit(b, it) } }

    private fun psalmUnits(psalms: Book): List<PlanUnit> = (1..psalms.chapters).flatMap { c ->
        if (c == 119) (0 until 22).map { s -> PlanUnit(psalms, c, (s * 8 + 1)..(s * 8 + 8)) } else listOf(PlanUnit(psalms, c))
    }

    /** The four built-in plans, built from the manifest's book list. */
    fun builtIn(books: List<Book>): List<ReadingPlan> {
        val ot = PlanTrack("பழைய ஏற்பாடு", "Old Testament", unitsOf(books.filter { it.testament == "OT" }))
        val nt = PlanTrack("புதிய ஏற்பாடு", "New Testament", unitsOf(books.filter { it.testament == "NT" }))
        val psalms = books.firstOrNull { it.code == "PSA" }
        return buildList {
            add(
                ReadingPlan(
                    "bible-1y", "ஒரு வருடத்தில் வேதாகமம்", "Whole Bible · 1 year",
                    "ஆதியாகமம் முதல் மல்கியா வரை, மத்தேயு முதல் வெளிப்படுத்தல் வரை — நாளொன்றுக்கு இரண்டு பகுதிகள்.",
                    "Genesis to Malachi and Matthew to Revelation side by side — two readings a day.",
                    365, listOf(ot, nt),
                ),
            )
            add(
                ReadingPlan(
                    "bible-2y", "இரண்டு வருடத்தில் வேதாகமம்", "Whole Bible · 2 years",
                    "அதே இரண்டு பகுதிகள், பாதி வேகத்தில் — நிதானமாக வாசிக்க.",
                    "The same two tracks at half the pace — for unhurried reading.",
                    730, listOf(ot, nt),
                ),
            )
            add(
                ReadingPlan(
                    "nt-6m", "புதிய ஏற்பாடு · 6 மாதம்", "New Testament · 6 months",
                    "மத்தேயு முதல் வெளிப்படுத்தல் வரை 26 வாரங்களில்.", "Matthew to Revelation in 26 weeks.",
                    182, listOf(nt),
                ),
            )
            if (psalms != null) {
                add(
                    ReadingPlan(
                        "psalms-6m", "சங்கீதங்கள் · 6 மாதம்", "Psalms · 6 months",
                        "நாளுக்கு ஒரு சங்கீதம்; 119-ஆம் சங்கீதம் அதன் 22 பகுதிகளாக; இடையிடையே ஓய்வு நாட்கள்.",
                        "A psalm a day, Psalm 119 in its 22 stanzas, with a few spare days along the way.",
                        180, listOf(PlanTrack("சங்கீதங்கள்", "Psalms", psalmUnits(psalms))),
                    ),
                )
            }
        }
    }

    private fun jsRound(x: Double): Int = kotlin.math.floor(x + 0.5).toInt()

    /** Day d reads units [round(dN/D), round((d+1)N/D)) of each track — identical to the website. */
    fun schedule(plan: ReadingPlan): List<List<PlanPassage>> = (0 until plan.days).map { d ->
        plan.tracks.mapIndexedNotNull { ti, t ->
            val n = t.units.size
            val a = jsRound(d.toDouble() * n / plan.days)
            val b = jsRound((d + 1).toDouble() * n / plan.days)
            if (b > a) {
                val units = t.units.subList(a, b)
                PlanPassage(ti, t, units, chapterCount(units).coerceAtLeast(1))
            } else {
                null
            }
        }
    }

    fun dayDone(day: List<PlanPassage>, d: Int, done: Set<String>) = day.all { key(d, it.trackIndex) in done }

    /** "ஆதியாகமம் 1–3", "சங்கீதம் 119:1–16". */
    fun passageLabel(units: List<PlanUnit>, lang: UiLang): String {
        val groups = mutableListOf<MutableList<PlanUnit>>()
        for (u in units) {
            val last = groups.lastOrNull()
            val same = last != null && last[0].book == u.book && (last[0].verses != null) == (u.verses != null) &&
                (u.verses == null || last[0].chapter == u.chapter)
            if (same) last!!.add(u) else groups.add(mutableListOf(u))
        }
        return groups.joinToString(" · ") { g ->
            val f = g.first()
            val l = g.last()
            val name = f.book.name(lang)
            when {
                f.verses != null -> "$name ${f.chapter}:${f.verses.first}–${l.verses!!.last}"
                f.chapter == l.chapter -> "$name ${f.chapter}"
                else -> "$name ${f.chapter}–${l.chapter}"
            }
        }
    }

    fun stats(plan: ReadingPlan, progress: PlanProgress, now: LocalDate = LocalDate.now()): PlanStats {
        val s = schedule(plan)
        val ti = ChronoUnit.DAYS.between(progress.start, now).toInt()
        var total = 0
        var read = 0
        var chapters = 0
        var chaptersTotal = 0
        var doneDays = 0
        s.forEachIndexed { d, day ->
            for (p in day) {
                total++
                chaptersTotal += p.chapters
                if (key(d, p.trackIndex) in progress.done) {
                    read++
                    chapters += p.chapters
                }
            }
            if (dayDone(day, d, progress.done)) doneDays++
        }
        val missed = (0 until minOf(ti, plan.days)).filter { s[it].isNotEmpty() && !dayDone(s[it], it, progress.done) }
        var streak = 0
        var d = minOf(ti, plan.days - 1)
        if (d >= 0 && !dayDone(s[d], d, progress.done)) d--
        while (d >= 0 && dayDone(s[d], d, progress.done)) {
            if (s[d].isNotEmpty()) streak++
            d--
        }
        val pct = if (total > 0) (read * 100.0 / total).roundToInt() else 0
        val exp = ((maxOf(0, ti).toDouble() / plan.days) * 100).roundToInt().coerceIn(0, 100)
        val elapsed = maxOf(1, minOf(ti + 1, plan.days))
        val rate = doneDays.toDouble() / elapsed
        val remain = plan.days - doneDays
        val finished = remain == 0
        val est = if (!finished && ti >= 0 && rate > 0) now.plusDays(ceil(remain / minOf(rate, 1.0)).toLong()) else null
        return PlanStats(ti, progress.start, progress.start.plusDays(plan.days - 1L), pct, exp, chapters, chaptersTotal, streak, missed, est, finished)
    }
}

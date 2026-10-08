package com.tamilscripture.feature.study

import com.tamilscripture.core.model.GeoFeature
import com.tamilscripture.core.model.Journey
import com.tamilscripture.core.model.JourneyStop
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** The journeys of one person or period, as the atlas's chips show them ("பவுல்": four journeys). */
internal class JourneyGroup(val id: String, val nameTa: String, val nameEn: String, val journeys: List<Journey>)

/** Chip names, in the Bible's order; journeys of a period not listed fall into "Other". */
private val Groups = listOf(
    Triple("patriarchs", "முற்பிதாக்கள்", "Patriarchs"),
    Triple("exodus", "யாத்திராகமம்", "Exodus"),
    Triple("judges", "நியாயாதிபதிகள் · தாவீது", "Judges · David"),
    Triple("prophets", "தீர்க்கதரிசிகள்", "Prophets"),
    Triple("exile", "சிறையிருப்பு", "Exile"),
    Triple("jesus", "இயேசு", "Jesus"),
    Triple("apostles", "அப்போஸ்தலர்", "Apostles"),
    Triple("paul", "பவுல்", "Paul"),
    Triple("other", "மற்றவை", "Other"),
)

private fun groupOf(j: Journey): String = when {
    j.id.startsWith("paul-") -> "paul"
    else -> when (j.period) {
        "patriarchs" -> "patriarchs"
        "exodus", "conquest" -> "exodus"
        "judges", "united-monarchy" -> "judges"
        "divided-kingdom" -> "prophets"
        "exile", "persian" -> "exile"
        "gospels" -> "jesus"
        "acts" -> "apostles"
        else -> "other"
    }
}

/** The journeys gathered into the chips' groups, each in the file's (the Bible's) order. */
internal fun journeyGroups(journeys: List<Journey>): List<JourneyGroup> {
    val by = journeys.groupBy(::groupOf)
    return Groups.mapNotNull { (id, ta, en) -> by[id]?.let { JourneyGroup(id, ta, en, it) } }
}

/** A journey's period as the sheet's overline names it. */
internal fun periodName(period: String, tamil: Boolean): String = when (period) {
    "patriarchs" -> if (tamil) "முற்பிதாக்கள்" else "Patriarchs"
    "exodus" -> if (tamil) "யாத்திரை" else "Exodus"
    "conquest" -> if (tamil) "கானான் வெற்றி" else "Conquest"
    "judges" -> if (tamil) "நியாயாதிபதிகள்" else "Judges"
    "united-monarchy" -> if (tamil) "ஒன்றுபட்ட அரசு" else "United kingdom"
    "divided-kingdom" -> if (tamil) "பிரிந்த அரசுகள்" else "Divided kingdom"
    "exile" -> if (tamil) "சிறையிருப்பு" else "Exile"
    "persian" -> if (tamil) "பாரசீகக் காலம்" else "Persian period"
    "gospels" -> if (tamil) "சுவிசேஷங்கள்" else "Gospels"
    "acts" -> if (tamil) "அப்போஸ்தலர்" else "Acts"
    else -> period
}

/** Kilometres along a route line ([lon, lat] pairs), or between the stops when there is no line. */
internal fun journeyKm(route: GeoFeature?, stops: List<JourneyStop>): Double {
    fun hav(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Double {
        val r = Math.toRadians(1.0)
        val a = sin((lat2 - lat1) * r / 2).pow(2) + cos(lat1 * r) * cos(lat2 * r) * sin((lon2 - lon1) * r / 2).pow(2)
        return 2 * 6371.0 * asin(sqrt(a))
    }
    val parts = route?.parts.orEmpty().filter { it.size >= 4 }
    if (parts.isNotEmpty()) return parts.sumOf { p -> (2 until p.size step 2).sumOf { i -> hav(p[i - 2], p[i - 1], p[i], p[i + 1]) } }
    return stops.zipWithNext().sumOf { (a, b) -> hav(a.lon, a.lat, b.lon, b.lat) }
}

/** "≈ 4,500 கி.மீ": to the nearest hundred from a thousand up, else the nearest ten. */
internal fun kmLabel(km: Double, tamil: Boolean): String {
    val step = if (km >= 1000) 100 else 10
    val n = ((km / step).roundToInt() * step).coerceAtLeast(step)
    // No-break spaces: the figure and its unit stay on one line.
    return "≈ " + "%,d".format(java.util.Locale.ROOT, n) + if (tamil) " கி.மீ" else " km"
}

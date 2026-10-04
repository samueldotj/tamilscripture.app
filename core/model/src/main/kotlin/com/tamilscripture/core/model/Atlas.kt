package com.tamilscripture.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The atlas data the website draws (the `.geojson` files under `entities/geo/`, and `journeys.json`; roadmap
 * M8-5), read into plain coordinate lists. Coordinates are [lon, lat] pairs flattened into
 * a DoubleArray per line or ring.
 */
data class GeoFeature(
    val props: Map<String, String>,
    /** Rings of polygons, or lines; empty for points. */
    val parts: List<DoubleArray>,
    /** [lon, lat] for a point. */
    val point: DoubleArray? = null,
)

object GeoJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<GeoFeature> {
        val root = json.parseToJsonElement(text).jsonObject
        return root["features"]?.jsonArray.orEmpty().mapNotNull { f ->
            val o = f.jsonObject
            val props = (o["properties"] as? JsonObject).orEmpty().mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }.toMap()
            val g = o["geometry"] as? JsonObject ?: return@mapNotNull null
            val coords = g["coordinates"] ?: return@mapNotNull null
            when (g["type"]?.jsonPrimitive?.contentOrNull) {
                "Point" -> GeoFeature(props, emptyList(), line(JsonArray(listOf(coords))).takeIf { it.size == 2 })
                "LineString" -> GeoFeature(props, listOf(line(coords)))
                "MultiLineString", "Polygon" -> GeoFeature(props, coords.jsonArray.map(::line))
                "MultiPolygon" -> GeoFeature(props, coords.jsonArray.flatMap { poly -> poly.jsonArray.map(::line) })
                else -> null
            }
        }
    }

    private fun line(e: JsonElement): DoubleArray {
        val pts = e.jsonArray
        val out = DoubleArray(pts.size * 2)
        pts.forEachIndexed { i, p ->
            val a = p.jsonArray
            out[i * 2] = a[0].jsonPrimitive.double
            out[i * 2 + 1] = a[1].jsonPrimitive.double
        }
        return out
    }
}

/** A stop on a journey (`journeys.json`). */
@Serializable
data class JourneyStop(
    val place: String = "",
    @SerialName("name_en") val nameEn: String = "",
    @SerialName("name_ta") val nameTa: String = "",
    val lon: Double,
    val lat: Double,
    val ref: String? = null,
)

/** A journey from `journeys.json`; its route line comes from `geo/journeys.geojson`. */
@Serializable
data class Journey(
    val id: String,
    @SerialName("name_en") val nameEn: String = "",
    @SerialName("name_ta") val nameTa: String = "",
    val period: String = "",
    @SerialName("summary_en") val summaryEn: String = "",
    @SerialName("summary_ta") val summaryTa: String = "",
    val passages: List<String> = emptyList(),
    val stops: List<JourneyStop> = emptyList(),
)

/** Web Mercator in degrees: x is longitude, y grows northward like latitude near the equator. */
object Mercator {
    fun y(lat: Double): Double = Math.toDegrees(kotlin.math.ln(kotlin.math.tan(Math.PI / 4 + Math.toRadians(lat) / 2)))
}

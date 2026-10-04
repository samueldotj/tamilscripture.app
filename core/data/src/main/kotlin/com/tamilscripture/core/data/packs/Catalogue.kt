package com.tamilscripture.core.data.packs

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** `packs/catalogue.json` as written by `pack-build` (design §7.5). Locations are paths, never hosts. */
@Serializable
data class Catalogue(
    val schema: Int = 1,
    val contentBuild: String = "",
    val minApp: Int = 1,
    val config: Map<String, JsonElement> = emptyMap(),
    val packs: List<CatalogueEntry> = emptyList(),
) {
    fun entry(id: String): CatalogueEntry? = packs.firstOrNull { it.id == id }
}

@Serializable
data class PackTitle(val ta: String = "", val en: String = "")

@Serializable
data class CatalogueEntry(
    val id: String,
    val type: String,
    val lang: String = "",
    val version: Int,
    val schema: Int = 1,
    val minApp: Int = 1,
    val title: PackTitle = PackTitle(),
    val licence: String = "",
    val attribution: String = "",
    /** Path on the `packs` origin. */
    val path: String,
    val size: Long,
    val rawSize: Long,
    val sha256: String,
    val rawSha256: String,
    val starter: Boolean = false,
    val build: String = "",
)

/** One installed pack, recorded in `packs/installed.json`. */
@Serializable
data class InstalledPack(val id: String, val version: Int, val file: String, val size: Long, val installedAt: Long)

/** Download progress for the Downloads screen. */
sealed interface PackState {
    data object NotInstalled : PackState
    data class Queued(val update: Boolean) : PackState
    data class Downloading(val fraction: Float) : PackState
    data object Installing : PackState
    data class Installed(val version: Int, val updateAvailable: Boolean) : PackState
    data class Failed(val reason: String) : PackState
}

/** The highest pack schema this app can read; newer packs are skipped until the app updates. */
const val SUPPORTED_PACK_SCHEMA = 1

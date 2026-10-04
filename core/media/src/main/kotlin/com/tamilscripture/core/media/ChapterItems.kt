package com.tamilscripture.core.media

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.tamilscripture.core.data.audio.AudioPaths
import com.tamilscripture.core.data.audio.AudioStore
import com.tamilscripture.core.data.net.Origin
import com.tamilscripture.core.data.net.OriginResolver
import com.tamilscripture.core.model.Book
import com.tamilscripture.core.model.ContentManifest

/**
 * Media items for chapters, shared by the app's player and the Android Auto browse tree
 * (M5-9). Ids: `c:VERSION:BOOK:chapter` for chapters, `b:VERSION:BOOK` for books,
 * `v:VERSION` for versions under the root.
 */
class ChapterItems(private val audio: AudioStore, private val origins: OriginResolver) {
    fun chapter(version: String, recording: String, book: Book, chapter: Int): MediaItem {
        // A downloaded chapter plays from the device (A-6.6); otherwise it streams.
        val local = audio.local(version, recording, book.code, chapter)
        return MediaItem.Builder()
            .setMediaId("c:$version:${book.code}:$chapter")
            .setUri(local?.let(Uri::fromFile) ?: Uri.parse(origins.url(Origin.Audio, AudioPaths.chapter(version, recording, book.code, chapter))))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("${book.nameTa} $chapter")
                    .setArtist(version)
                    .setAlbumTitle(book.nameTa)
                    .setTrackNumber(chapter)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build(),
            )
            .build()
    }

    /** The whole book from [book]'s first chapter, for a queue that continues by itself (A-6.1). */
    fun book(version: String, recording: String, book: Book): List<MediaItem> =
        (1..book.chapters).map { chapter(version, recording, book, it) }

    fun folder(id: String, title: String, subtitle: String? = null, type: Int): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(type)
                    .build(),
            )
            .build()

    /** Resolves a chapter id from Android Auto to its version, recording, book and chapter. */
    fun parse(manifest: ContentManifest, id: String): Triple<String, Book, Int>? {
        val parts = id.split(':')
        if (parts.size != 4 || parts[0] != "c") return null
        val book = manifest.book(parts[2]) ?: return null
        return Triple(parts[1], book, parts[3].toIntOrNull() ?: return null)
    }
}

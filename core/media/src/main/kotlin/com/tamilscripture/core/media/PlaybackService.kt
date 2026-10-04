package com.tamilscripture.core.media

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.tamilscripture.core.data.GraphHost
import com.tamilscripture.core.model.ContentManifest

/**
 * Plays chapter MP3s in the background with a media notification, lock-screen and
 * headphone controls (A-6.2). The app talks to it only through [AudioController]; Android
 * Auto and other media browsers walk its library (M5-9): versions with audio → books →
 * chapters, a chosen chapter queueing the rest of its book.
 */
class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        val graph = (application as GraphHost).graph
        session = MediaLibrarySession.Builder(this, player, Library(ChapterItems(graph.audio, graph.origins)) { graph.content.manifest.value }).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    // onSetMediaItems, which expands a chosen chapter into its book, is still marked unstable.
    @OptIn(UnstableApi::class)
    private class Library(
        private val items: ChapterItems,
        private val manifest: () -> ContentManifest?,
    ) : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(items.folder(ROOT, "தமிழ் வேதாகமம்", type = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val m = manifest() ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
            val parts = parentId.split(':')
            val children: List<MediaItem> = when {
                parentId == ROOT -> m.versions.filter { it.audio != null }.sortedBy { it.order }.map { v ->
                    items.folder("v:${v.code}", v.nameNative ?: v.name, v.short, MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
                }
                parts[0] == "v" && parts.size == 2 -> m.books.map { b ->
                    items.folder("b:${parts[1]}:${b.code}", b.nameTa, b.nameEn, MediaMetadata.MEDIA_TYPE_ALBUM)
                }
                parts[0] == "b" && parts.size == 3 -> {
                    val rec = m.version(parts[1])?.audio?.recording
                    val book = m.book(parts[2])
                    if (rec == null || book == null) emptyList() else items.book(parts[1], rec, book)
                }
                else -> emptyList()
            }
            val from = (page * pageSize).coerceAtMost(children.size)
            val to = (from + pageSize).coerceAtMost(children.size)
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(children.subList(from, to)), params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = manifest()?.let { m -> resolve(m, mediaId) }
            return Futures.immediateFuture(item?.let { LibraryResult.ofItem(it, null) } ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
        }

        /**
         * A chapter chosen in a media browser queues the rest of its book, like the app
         * does; items the app sends already carry their URIs and pass through unchanged.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val m = manifest()
            val single = mediaItems.singleOrNull()
            val target = if (m != null && single != null && single.localConfiguration == null) items.parse(m, single.mediaId) else null
            if (m != null && target != null) {
                val (version, book, chapter) = target
                val rec = m.version(version)?.audio?.recording
                if (rec != null) {
                    return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(items.book(version, rec, book), chapter - 1, 0L))
                }
            }
            val resolved = mediaItems.map { item -> if (item.localConfiguration != null || m == null) item else resolve(m, item.mediaId) ?: item }
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(resolved, startIndex, startPositionMs))
        }

        private fun resolve(m: ContentManifest, id: String): MediaItem? {
            val (version, book, chapter) = items.parse(m, id) ?: return null
            val rec = m.version(version)?.audio?.recording ?: return null
            return items.chapter(version, rec, book, chapter)
        }
    }

    private companion object {
        const val ROOT = "root"
    }
}

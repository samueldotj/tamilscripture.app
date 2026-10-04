package com.tamilscripture.core.media

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.tamilscripture.core.data.audio.AudioPaths
import com.tamilscripture.core.data.audio.AudioStore
import com.tamilscripture.core.data.content.ContentRepository
import com.tamilscripture.core.data.net.Origin
import com.tamilscripture.core.data.net.OriginResolver
import com.tamilscripture.core.data.stats.StatsRecorder
import com.tamilscripture.core.model.AudioTimings
import com.tamilscripture.core.model.Book
import com.tamilscripture.core.model.ContentManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AudioState(
    val active: Boolean = false,
    val playing: Boolean = false,
    val version: String? = null,
    val book: String? = null,
    val chapter: Int = 0,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    /** The verse being read, from the chapter's timings (A-6.4). */
    val verse: Int? = null,
    val verseCount: Int = 0,
)

/**
 * The app's single handle on playback. Connects to [PlaybackService] lazily on first play,
 * publishes [state] for the player bar and the reader's verse highlight, and records
 * `audio` stats events with the same actions as the website (play, next, jump, end, time).
 */
class AudioController(
    private val context: Context,
    private val content: ContentRepository,
    private val origins: OriginResolver,
    private val stats: StatsRecorder,
    private val scope: CoroutineScope,
    audio: AudioStore,
) {
    private val items = ChapterItems(audio, origins)
    private val mutable = MutableStateFlow(AudioState())
    val state: StateFlow<AudioState> = mutable

    private var controller: MediaController? = null
    private var ticker: Job? = null
    private var timings: AudioTimings? = null
    private var timingsKey: String? = null
    private var listenedMs = 0L
    private var lastTick = 0L

    private val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

    fun hasAudio(manifest: ContentManifest?, version: String): Boolean = manifest?.version(version)?.audio != null

    /** Starts [book] [chapter] in [version], optionally from [fromVerse]; the rest of the book follows (A-6.1). */
    fun play(version: String, book: Book, chapter: Int, fromVerse: Int? = null) {
        scope.launch(Dispatchers.Main) {
            val manifest = content.manifest.value ?: return@launch
            val rec = manifest.version(version)?.audio?.recording ?: return@launch
            val c = connect()
            flushListened()
            val items = items.book(version, rec, book)
            c.setMediaItems(items, chapter - 1, 0L)
            c.prepare()
            if (fromVerse != null && fromVerse > 1) {
                loadTimings(version, book.code, chapter)?.startOf(fromVerse)?.let { c.seekTo(chapter - 1, it) }
            }
            c.play()
            stats.record("audio", action = "play", version = version, book = book.code, chapter = chapter,
                verse = fromVerse?.let { "${book.code}.$chapter.$it" })
            startTicker()
        }
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekToVerse(verse: Int) {
        val s = state.value
        val c = controller ?: return
        scope.launch(Dispatchers.Main) {
            val t = loadTimings(s.version ?: return@launch, s.book ?: return@launch, s.chapter)?.startOf(verse) ?: return@launch
            c.seekTo(t)
            stats.record("audio", action = "jump", version = s.version, book = s.book, chapter = s.chapter, verse = "${s.book}.${s.chapter}.$verse")
        }
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }

    fun cycleSpeed() {
        val c = controller ?: return
        val i = speeds.indexOf(c.playbackParameters.speed).let { if (it < 0) 1 else it }
        c.setPlaybackSpeed(speeds[(i + 1) % speeds.size])
    }

    fun stop() {
        flushListened()
        controller?.run { stop(); clearMediaItems() }
        ticker?.cancel()
        mutable.value = AudioState()
    }

    private suspend fun connect(): MediaController {
        controller?.let { return it }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val c = withContext(Dispatchers.IO) { future.get() }
        c.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    val s = state.value
                    stats.record("audio", action = "end", version = s.version, book = s.book, chapter = s.chapter)
                }
                flushListened()
                publish()
                val s = state.value
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    stats.record("audio", action = "next", version = s.version, book = s.book, chapter = s.chapter)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) flushListened()
                publish()
            }
        })
        controller = c
        return c
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        lastTick = System.currentTimeMillis()
        ticker = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val now = System.currentTimeMillis()
                if (controller?.isPlaying == true) listenedMs += now - lastTick
                lastTick = now
                publish()
                delay(200)
            }
        }
    }

    private fun publish() {
        val c = controller ?: return
        // c:VERSION:BOOK:chapter (ChapterItems), from the app or from Android Auto.
        val id = c.currentMediaItem?.mediaId?.split(':')?.takeIf { it.size == 4 && it[0] == "c" }
        val version = id?.getOrNull(1)
        val book = id?.getOrNull(2)
        val chapter = id?.getOrNull(3)?.toIntOrNull() ?: 0
        if (version != null && book != null && timingsKey != "$version/$book/$chapter") {
            timingsKey = "$version/$book/$chapter"
            timings = null
            scope.launch(Dispatchers.Main) { timings = loadTimings(version, book, chapter) }
        }
        val pos = c.currentPosition
        mutable.value = AudioState(
            active = c.mediaItemCount > 0,
            playing = c.isPlaying,
            version = version,
            book = book,
            chapter = chapter,
            positionMs = pos,
            durationMs = c.duration.coerceAtLeast(0),
            speed = c.playbackParameters.speed,
            verse = timings?.verseAt(pos),
            verseCount = timings?.verses?.size ?: 0,
        )
    }

    private suspend fun loadTimings(version: String, book: String, chapter: Int): AudioTimings? =
        content.timings(version, book, chapter)

    /** Seconds actually heard, sent when the chapter changes, playback pauses or stops. */
    private fun flushListened() {
        val s = state.value
        val secs = listenedMs / 1000
        if (secs >= 1 && s.book != null) {
            stats.record("audio", action = "time", version = s.version, book = s.book, chapter = s.chapter, amount = secs.coerceAtMost(3600))
        }
        listenedMs = 0
    }
}

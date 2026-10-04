package com.tamilscripture.app

import android.content.Context
import android.content.Intent
import androidx.navigation3.runtime.NavKey
import com.tamilscripture.core.model.Passage

/**
 * A second (third, …) window on a passage (FF-9, roadmap M2-9). MainActivity is singleTask
 * so website links always reach the same window; this one is a plain activity launched as
 * its own document task, next to the current window where the system allows. Each window
 * has its own back stack and reader state; settings, packs and history are shared.
 */
class ReaderWindowActivity : MainActivity() {
    override fun startStack(): List<NavKey> {
        val p = intent?.let {
            Passage(
                it.getStringExtra(VERSION) ?: return@let null,
                it.getStringExtra(BOOK) ?: return@let null,
                it.getIntExtra(CHAPTER, 1),
                it.getIntExtra(VERSE, 0).takeIf { v -> v > 0 },
            )
        }
        return if (p == null) listOf(HomeRoute) else listOf(HomeRoute, ReaderRoute(p))
    }

    companion object {
        private const val VERSION = "version"
        private const val BOOK = "book"
        private const val CHAPTER = "chapter"
        private const val VERSE = "verse"

        fun open(context: Context, p: Passage) {
            context.startActivity(
                Intent(context, ReaderWindowActivity::class.java)
                    .putExtra(VERSION, p.version)
                    .putExtra(BOOK, p.book)
                    .putExtra(CHAPTER, p.chapter)
                    .putExtra(VERSE, p.verse ?: 0)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT,
                    ),
            )
        }
    }
}

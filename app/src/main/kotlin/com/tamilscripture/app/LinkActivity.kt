package com.tamilscripture.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import java.util.UUID

/**
 * Where tamilscripture.com links, the widget and the reminder arrive (A-2.10). It hands the
 * link to [MainActivity] with an id of its own and closes. Android replays a task's first
 * intent when it restarts the task (after a force-stop, for example); with the id,
 * MainActivity can tell that replay from someone opening the same link again.
 */
class LinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { uri ->
            startActivity(
                Intent(Intent.ACTION_VIEW, uri, this, MainActivity::class.java)
                    .putExtra(MainActivity.LINK_ID, UUID.randomUUID().toString())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        finish()
    }
}

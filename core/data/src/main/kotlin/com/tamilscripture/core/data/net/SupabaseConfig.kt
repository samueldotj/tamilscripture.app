package com.tamilscripture.core.data.net

/**
 * The website's Supabase project. The anon (publishable) key is public by design, as in the
 * website's own config: everything it reaches is guarded by row-level security.
 */
object SupabaseConfig {
    const val URL = "https://zytgmnqmrvgspjdokajp.supabase.co"
    const val ANON_KEY = "sb_publishable_9kbp5Fkm29ZBkhPxzSkhsg_6yK_SJV7"
}

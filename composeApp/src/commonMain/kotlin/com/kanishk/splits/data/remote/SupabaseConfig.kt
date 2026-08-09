package com.kanishk.splits.data.remote

/**
 * Point this at your own Supabase project (Dashboard → Project Settings → API), then run
 * `backend/supabase/schema.sql` once in the SQL editor.
 *
 * The anon key is safe to ship inside the app. Every table has RLS on with no policies, so
 * this key can't touch them directly — it can only call the four SECURITY DEFINER functions,
 * each of which already demands an unguessable group id or invite code.
 *
 * Leave these blank and the app simply runs offline-only: nothing syncs, nothing crashes.
 */
object SupabaseConfig {
    const val URL: String = ""
    const val ANON_KEY: String = ""

    val isConfigured: Boolean
        get() = URL.isNotBlank() && ANON_KEY.isNotBlank()

    val rpcBase: String
        get() = URL.trimEnd('/') + "/rest/v1/rpc"
}

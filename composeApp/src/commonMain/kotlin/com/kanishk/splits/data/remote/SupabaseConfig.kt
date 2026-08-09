package com.kanishk.splits.data.remote

/**
 * Where the app finds your Supabase project.
 *
 * Values are injected at build time from `local.properties` (gitignored), so the project URL
 * and anon key never enter the repository:
 *
 * ```properties
 * supabase.url=https://abcdefgh.supabase.co
 * supabase.anonKey=eyJhbGciOi...
 * ```
 *
 * Leave them out and [isConfigured] is false: the app never touches the network, `SyncEngine`
 * reports `Disabled`, and everything still works as a local-only app.
 *
 * The anon key is safe to ship inside a client. Every table has RLS on with no policies and
 * direct grants revoked, so this key cannot read or write a row. It can only call the four
 * SECURITY DEFINER functions, each of which already demands an unguessable group id or invite
 * code. Keeping it out of the public repo is defence in depth, not the thing holding the door.
 */
object SupabaseConfig {
    val URL: String get() = GeneratedConfig.SUPABASE_URL
    val ANON_KEY: String get() = GeneratedConfig.SUPABASE_ANON_KEY

    val isConfigured: Boolean
        get() = URL.isNotBlank() && ANON_KEY.isNotBlank()

    val rpcBase: String
        get() = URL.trimEnd('/') + "/rest/v1/rpc"
}
